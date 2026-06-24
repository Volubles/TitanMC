package com.voluble.titanMC.progression.persistence;

import com.voluble.titanMC.progression.model.PlayerProgression;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ProgressionStorage implements AutoCloseable {

	private static final int SCHEMA_VERSION = 1;
	private final Connection connection;
	private final ExecutorService writer = Executors.newSingleThreadExecutor(
		Thread.ofPlatform().name("titan-progression-writer").factory()
	);

	public ProgressionStorage(Path databasePath) throws SQLException {
		Objects.requireNonNull(databasePath, "databasePath");
		try {
			Path parent = databasePath.toAbsolutePath().getParent();
			if (parent != null) Files.createDirectories(parent);
			Class.forName("org.sqlite.JDBC");
		} catch (Exception exception) {
			throw new SQLException("Failed to prepare Progression database", exception);
		}
		connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
		configure();
		initializeSchema();
	}

	private void configure() throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys = ON");
			statement.execute("PRAGMA journal_mode = WAL");
			statement.execute("PRAGMA synchronous = FULL");
			statement.execute("PRAGMA busy_timeout = 5000");
		}
	}

	private void initializeSchema() throws SQLException {
		try (Statement statement = connection.createStatement()) {
			int version;
			try (ResultSet result = statement.executeQuery("PRAGMA user_version")) {
				version = result.next() ? result.getInt(1) : 0;
				if (version > SCHEMA_VERSION) {
					throw new SQLException("Unsupported Progression database schema " + version);
				}
			}
			statement.executeUpdate("""
					CREATE TABLE IF NOT EXISTS player_progression (
					    player_uuid TEXT PRIMARY KEY NOT NULL,
					    total_cred INTEGER NOT NULL,
					    level INTEGER NOT NULL,
					    updated_at INTEGER NOT NULL
					)
					""");
			statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
		}
	}

	public synchronized Map<UUID, PlayerProgression> loadAll() throws SQLException {
		Map<UUID, PlayerProgression> records = new LinkedHashMap<>();
		try (Statement statement = connection.createStatement();
			 ResultSet result = statement.executeQuery(
				 "SELECT player_uuid, total_cred, level, updated_at FROM player_progression ORDER BY player_uuid"
			 )) {
			while (result.next()) {
				UUID playerId = UUID.fromString(result.getString("player_uuid"));
				PlayerProgression progression = new PlayerProgression(
					playerId,
					result.getLong("total_cred"),
					result.getInt("level"),
					result.getLong("updated_at")
				);
				records.put(playerId, progression);
			}
		}
		return records;
	}

	public CompletableFuture<Void> save(PlayerProgression progression) {
		Objects.requireNonNull(progression, "progression");
		return write(() -> upsert(progression));
	}

	public CompletableFuture<Void> delete(UUID playerId) {
		Objects.requireNonNull(playerId, "playerId");
		return write(() -> execute(
			"DELETE FROM player_progression WHERE player_uuid = ?",
			playerId.toString()
		));
	}

	private void upsert(PlayerProgression progression) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO player_progression(player_uuid, total_cred, level, updated_at)
				VALUES(?,?,?,?) ON CONFLICT(player_uuid) DO UPDATE SET
				total_cred = excluded.total_cred,
				level = excluded.level,
				updated_at = excluded.updated_at
				""")) {
			statement.setString(1, progression.playerId().toString());
			statement.setLong(2, progression.totalCred());
			statement.setInt(3, progression.level());
			statement.setLong(4, progression.updatedAtEpochMillis());
			statement.executeUpdate();
		}
	}

	private void execute(String sql, Object... values) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
			statement.executeUpdate();
		}
	}

	private CompletableFuture<Void> write(SqlTask task) {
		return CompletableFuture.runAsync(() -> {
			try {
				task.run();
			} catch (SQLException exception) {
				throw new IllegalStateException("Progression database write failed", exception);
			}
		}, writer);
	}

	public void flush() {
		try {
			writer.submit(() -> {
			}).get();
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to flush Progression database", exception);
		}
	}

	@Override
	public void close() throws SQLException {
		flush();
		writer.shutdown();
		connection.close();
	}

	@FunctionalInterface
	private interface SqlTask {
		void run() throws SQLException;
	}
}
