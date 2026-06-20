package com.voluble.titanMC.regions.persistence;

import com.voluble.titanMC.regions.model.BlockBox;
import com.voluble.titanMC.regions.model.CuboidGeometry;
import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.model.RegionGeometry;
import com.voluble.titanMC.regions.model.RegionId;
import com.voluble.titanMC.regions.model.RegionKey;
import com.voluble.titanMC.regions.model.WorldId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SqliteRegionRepository implements RegionRepository {

	private static final int SCHEMA_VERSION = 3;
	private final Path databasePath;
	private Connection connection;

	public SqliteRegionRepository(Path databasePath) {
		this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath();
	}

	@Override
	public synchronized void initialize() throws SQLException {
		if (connection != null) return;
		try {
			Path parent = databasePath.getParent();
			if (parent != null) Files.createDirectories(parent);
			Class.forName("org.sqlite.JDBC");
		} catch (Exception exception) {
			throw new SQLException("Failed to prepare SQLite driver or database directory", exception);
		}

		connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
		try {
			configureConnection();
			verifyIntegrity();
			initializeSchema();
		} catch (SQLException exception) {
			try {
				connection.close();
			} catch (SQLException suppressed) {
				exception.addSuppressed(suppressed);
			}
			connection = null;
			throw exception;
		}
	}

	private void configureConnection() throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys = ON");
			statement.execute("PRAGMA journal_mode = WAL");
			statement.execute("PRAGMA synchronous = FULL");
			statement.execute("PRAGMA busy_timeout = 5000");
		}
	}

	private void verifyIntegrity() throws SQLException {
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
			if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) {
				throw new SQLException("Region database integrity check failed; refusing to modify it");
			}
	}
	}

	private void initializeSchema() throws SQLException {
		int version;
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("PRAGMA user_version")) {
			version = result.next() ? result.getInt(1) : 0;
		}
		if (version != 0 && version != SCHEMA_VERSION) {
			throw new SQLException(
				"Unsupported region database schema " + version + "; expected an empty database or schema " + SCHEMA_VERSION
			);
		}
		if (version == SCHEMA_VERSION) return;

		inTransaction(() -> {
			try (Statement statement = connection.createStatement()) {
				statement.executeUpdate("""
					CREATE TABLE IF NOT EXISTS regions (
					    id TEXT PRIMARY KEY NOT NULL,
					    world_id TEXT NOT NULL,
					    namespace TEXT NOT NULL,
					    name TEXT NOT NULL,
					    priority INTEGER NOT NULL,
					    revision INTEGER NOT NULL,
					    created_at INTEGER NOT NULL,
					    updated_at INTEGER NOT NULL,
					    UNIQUE(world_id, namespace, name)
					)
					""");
				statement.executeUpdate("""
					CREATE TABLE IF NOT EXISTS region_cuboids (
					    region_id TEXT PRIMARY KEY NOT NULL,
					    min_x INTEGER NOT NULL,
					    min_y INTEGER NOT NULL,
					    min_z INTEGER NOT NULL,
					    max_x_exclusive INTEGER NOT NULL,
					    max_y_exclusive INTEGER NOT NULL,
					    max_z_exclusive INTEGER NOT NULL,
					    FOREIGN KEY(region_id) REFERENCES regions(id) ON DELETE CASCADE
					)
					""");
				statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
			}
		});
	}

	@Override
	public synchronized List<RegionDefinition> loadAll() throws SQLException {
		requireInitialized();
		List<RegionDefinition> definitions = new ArrayList<>();
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("""
			SELECT r.id, r.world_id, r.namespace, r.name, r.priority, r.revision, r.created_at, r.updated_at,
			       c.min_x, c.min_y, c.min_z, c.max_x_exclusive, c.max_y_exclusive, c.max_z_exclusive
			FROM regions r
			LEFT JOIN region_cuboids c ON c.region_id = r.id
			ORDER BY r.world_id, r.namespace, r.name
			""")) {
			while (result.next()) {
				RegionId id = RegionId.parse(result.getString("id"));
				if (result.getObject("min_x") == null) throw new SQLException("Region has no geometry: " + id);
				definitions.add(new RegionDefinition(
					id,
					RegionKey.of(result.getString("namespace"), result.getString("name")),
					WorldId.parse(result.getString("world_id")),
					result.getInt("priority"),
					new CuboidGeometry(new BlockBox(
						result.getInt("min_x"), result.getInt("min_y"), result.getInt("min_z"),
						result.getInt("max_x_exclusive"), result.getInt("max_y_exclusive"),
						result.getInt("max_z_exclusive")
					)),
					Instant.ofEpochMilli(result.getLong("created_at")),
					Instant.ofEpochMilli(result.getLong("updated_at")),
					result.getLong("revision")
				));
			}
		}
		return List.copyOf(definitions);
	}

	@Override
	public synchronized void save(RegionDefinition definition) throws SQLException {
		requireInitialized();
		inTransaction(() -> {
			try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO regions(id, world_id, namespace, name, priority, revision, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT(id) DO UPDATE SET
				    world_id = excluded.world_id,
				    namespace = excluded.namespace,
				    name = excluded.name,
				    priority = excluded.priority,
				    revision = excluded.revision,
				    updated_at = excluded.updated_at
				""")) {
				statement.setString(1, definition.id().toString());
				statement.setString(2, definition.worldId().toString());
				statement.setString(3, definition.key().namespace());
				statement.setString(4, definition.key().name());
				statement.setInt(5, definition.priority());
				statement.setLong(6, definition.revision());
				statement.setLong(7, definition.createdAt().toEpochMilli());
				statement.setLong(8, definition.updatedAt().toEpochMilli());
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement("DELETE FROM region_cuboids WHERE region_id = ?")) {
				statement.setString(1, definition.id().toString());
				statement.executeUpdate();
			}
			try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO region_cuboids(
				    region_id, min_x, min_y, min_z,
				    max_x_exclusive, max_y_exclusive, max_z_exclusive
				) VALUES (?, ?, ?, ?, ?, ?, ?)
				""")) {
				bindCuboid(statement, definition);
				statement.executeUpdate();
			}
		});
	}

	@Override
	public synchronized void delete(RegionId id) throws SQLException {
		requireInitialized();
		inTransaction(() -> {
			try (PreparedStatement statement = connection.prepareStatement("DELETE FROM regions WHERE id = ?")) {
				statement.setString(1, id.toString());
				statement.executeUpdate();
			}
		});
	}

	@Override
	public synchronized void applyBatch(List<RegionDefinition> saves, List<RegionId> deletes) throws SQLException {
		requireInitialized();
		Objects.requireNonNull(saves, "saves");
		Objects.requireNonNull(deletes, "deletes");
		inTransaction(() -> {
			try (PreparedStatement statement = connection.prepareStatement("DELETE FROM regions WHERE id = ?")) {
				for (RegionId id : deletes) {
					statement.setString(1, Objects.requireNonNull(id, "deletes must not contain null").toString());
					statement.addBatch();
				}
				for (RegionDefinition definition : saves) {
					statement.setString(1, Objects.requireNonNull(definition, "saves must not contain null").id().toString());
					statement.addBatch();
				}
				statement.executeBatch();
			}

			try (PreparedStatement regionStatement = connection.prepareStatement("""
				INSERT INTO regions(id, world_id, namespace, name, priority, revision, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				"""); PreparedStatement boxStatement = connection.prepareStatement("""
				INSERT INTO region_cuboids(
				    region_id, min_x, min_y, min_z,
				    max_x_exclusive, max_y_exclusive, max_z_exclusive
				) VALUES (?, ?, ?, ?, ?, ?, ?)
				""")) {
				for (RegionDefinition definition : saves) {
					regionStatement.setString(1, definition.id().toString());
					regionStatement.setString(2, definition.worldId().toString());
					regionStatement.setString(3, definition.key().namespace());
					regionStatement.setString(4, definition.key().name());
					regionStatement.setInt(5, definition.priority());
					regionStatement.setLong(6, definition.revision());
					regionStatement.setLong(7, definition.createdAt().toEpochMilli());
					regionStatement.setLong(8, definition.updatedAt().toEpochMilli());
					regionStatement.addBatch();
					bindCuboid(boxStatement, definition);
					boxStatement.addBatch();
				}
				regionStatement.executeBatch();
				boxStatement.executeBatch();
			}
		});
	}

	private static void bindCuboid(PreparedStatement statement, RegionDefinition definition) throws SQLException {
		RegionGeometry geometry = definition.geometry();
		if (!(geometry instanceof CuboidGeometry cuboid)) {
			throw new SQLException("Unsupported region geometry: " + geometry.getClass().getSimpleName());
		}
		BlockBox box = cuboid.bounds();
		statement.setString(1, definition.id().toString());
		statement.setInt(2, box.minX());
		statement.setInt(3, box.minY());
		statement.setInt(4, box.minZ());
		statement.setInt(5, box.maxXExclusive());
		statement.setInt(6, box.maxYExclusive());
		statement.setInt(7, box.maxZExclusive());
	}

	@Override
	public synchronized void close() throws SQLException {
		if (connection == null) return;
		connection.close();
		connection = null;
	}

	private void requireInitialized() throws SQLException {
		if (connection == null) throw new SQLException("Region repository is not initialized");
	}

	private void inTransaction(SqlAction action) throws SQLException {
		boolean previousAutoCommit = connection.getAutoCommit();
		connection.setAutoCommit(false);
		try {
			action.run();
			connection.commit();
		} catch (SQLException | RuntimeException exception) {
			try {
				connection.rollback();
			} catch (SQLException rollbackFailure) {
				exception.addSuppressed(rollbackFailure);
			}
			throw exception;
		} finally {
			connection.setAutoCommit(previousAutoCommit);
		}
	}

	@FunctionalInterface
	private interface SqlAction {
		void run() throws SQLException;
	}
}
