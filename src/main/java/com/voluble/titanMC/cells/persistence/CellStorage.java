package com.voluble.titanMC.cells.persistence;

import com.voluble.titanMC.cells.model.CellDefinition;
import com.voluble.titanMC.cells.model.CellLease;
import com.voluble.titanMC.cells.model.TrackedCellBlock;
import com.voluble.titanMC.cells.model.CellResetJob;
import com.voluble.titanMC.cells.model.CellSign;
import com.voluble.titanMC.cells.model.CellRecoveryLot;
import com.voluble.titanMC.util.RegionUtils;
import com.voluble.titanMC.ranks.model.WardId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CellStorage implements AutoCloseable {

	private static final int SCHEMA_VERSION = 5;
	private final Connection connection;
	private final ExecutorService writer = Executors.newSingleThreadExecutor(
			Thread.ofPlatform().name("titan-cell-writer").factory()
	);

	public CellStorage(Path databasePath) throws SQLException {
		Objects.requireNonNull(databasePath, "databasePath");
		try {
			Path parent = databasePath.toAbsolutePath().getParent();
			if (parent != null) Files.createDirectories(parent);
			Class.forName("org.sqlite.JDBC");
		} catch (Exception exception) {
			throw new SQLException("Failed to prepare Cells database", exception);
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
					throw new SQLException("Unsupported Cells database schema " + version);
				}
			}
			statement.executeUpdate("""
					CREATE TABLE IF NOT EXISTS cells (
					    id TEXT PRIMARY KEY NOT NULL,
					    display_name TEXT NOT NULL,
					    ward_id TEXT NOT NULL,
					    world_id TEXT NOT NULL,
					    min_x INTEGER NOT NULL, min_y INTEGER NOT NULL, min_z INTEGER NOT NULL,
					    max_x INTEGER NOT NULL, max_y INTEGER NOT NULL, max_z INTEGER NOT NULL,
					    rent_price INTEGER NOT NULL,
					    rent_duration_seconds INTEGER NOT NULL,
					    max_rent_duration_seconds INTEGER NOT NULL,
					    enabled INTEGER NOT NULL
					)
					""");
			if (version == 1) {
				statement.executeUpdate("ALTER TABLE cells ADD COLUMN display_name TEXT");
				statement.executeUpdate("UPDATE cells SET display_name=id WHERE display_name IS NULL");
			}
			if (version > 0 && version < 3) {
				statement.executeUpdate("ALTER TABLE cells ADD COLUMN max_rent_duration_seconds INTEGER");
				statement.executeUpdate("UPDATE cells SET max_rent_duration_seconds=rent_duration_seconds*30 WHERE max_rent_duration_seconds IS NULL");
			}
			if (version > 0 && version < 4) {
				statement.executeUpdate("ALTER TABLE cells ADD COLUMN ward_id TEXT NOT NULL DEFAULT 'e'");
			}
			statement.executeUpdate("""
					CREATE TABLE IF NOT EXISTS cell_leases (
					    cell_id TEXT PRIMARY KEY NOT NULL,
					    owner_uuid TEXT NOT NULL,
					    generation INTEGER NOT NULL,
					    started_at INTEGER NOT NULL,
					    expires_at INTEGER NOT NULL,
					    FOREIGN KEY(cell_id) REFERENCES cells(id) ON DELETE CASCADE
					)
					""");
			if (version > 0 && version < 3 && hasColumn("cell_leases", "auto_renew")) {
				statement.executeUpdate("ALTER TABLE cell_leases DROP COLUMN auto_renew");
			}
			statement.executeUpdate("""
					CREATE TABLE IF NOT EXISTS cell_blocks (
					    cell_id TEXT NOT NULL,
					    lease_generation INTEGER NOT NULL,
					    world_id TEXT NOT NULL,
					    x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL,
					    PRIMARY KEY(world_id, x, y, z),
					    FOREIGN KEY(cell_id) REFERENCES cells(id) ON DELETE CASCADE
					)
					""");
			statement.executeUpdate("""
					CREATE TABLE IF NOT EXISTS cell_members (
					    cell_id TEXT NOT NULL,
					    lease_generation INTEGER NOT NULL,
					    player_uuid TEXT NOT NULL,
					    PRIMARY KEY(cell_id, lease_generation, player_uuid),
					    FOREIGN KEY(cell_id) REFERENCES cells(id) ON DELETE CASCADE
					)
					""");
			statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_cell_blocks_cell ON cell_blocks(cell_id, lease_generation)");
			statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_cell_leases_owner ON cell_leases(owner_uuid)");
			statement.executeUpdate("""
					CREATE TABLE IF NOT EXISTS cell_recovery_lots (
					    id INTEGER PRIMARY KEY AUTOINCREMENT,
					    cell_id TEXT NOT NULL,
					    ward_id TEXT NOT NULL,
					    owner_uuid TEXT NOT NULL,
					    lease_generation INTEGER NOT NULL,
					    created_at INTEGER NOT NULL,
					    status TEXT NOT NULL
					)
					""");
			if (version > 0 && version < 5) {
				if (!hasColumn("cell_recovery_lots", "ward_id")) {
					statement.executeUpdate("ALTER TABLE cell_recovery_lots ADD COLUMN ward_id TEXT NOT NULL DEFAULT 'e'");
				}
				statement.executeUpdate("""
					UPDATE cell_recovery_lots
					SET ward_id=COALESCE((SELECT ward_id FROM cells WHERE cells.id=cell_recovery_lots.cell_id), 'e')
					""");
			}
			statement.executeUpdate("""
					CREATE TABLE IF NOT EXISTS cell_recovery_items (
					    id INTEGER PRIMARY KEY AUTOINCREMENT,
					    lot_id INTEGER NOT NULL,
					    sort_index INTEGER NOT NULL,
					    item_data BLOB NOT NULL,
					    FOREIGN KEY(lot_id) REFERENCES cell_recovery_lots(id) ON DELETE CASCADE
					)
					""");
			statement.executeUpdate("""
					CREATE TABLE IF NOT EXISTS cell_reset_jobs (
					    cell_id TEXT PRIMARY KEY NOT NULL,
					    lease_generation INTEGER NOT NULL,
					    owner_uuid TEXT NOT NULL,
					    phase TEXT NOT NULL,
					    recovery_lot_id INTEGER
					)
					""");
			statement.executeUpdate("""
					CREATE TABLE IF NOT EXISTS cell_signs (
					    world_id TEXT NOT NULL,
					    x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL,
					    cell_id TEXT NOT NULL,
					    PRIMARY KEY(world_id, x, y, z),
					    FOREIGN KEY(cell_id) REFERENCES cells(id) ON DELETE CASCADE
					)
					""");
			statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
		}
	}

	private boolean hasColumn(String table, String column) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
			"SELECT COUNT(*) FROM pragma_table_info(?) WHERE name = ?"
		)) {
			statement.setString(1, table);
			statement.setString(2, column);
			try (ResultSet result = statement.executeQuery()) {
				return result.next() && result.getInt(1) > 0;
			}
		}
	}

	public synchronized Map<String, CellDefinition> loadCells() throws SQLException {
		Map<String, CellDefinition> cells = new LinkedHashMap<>();
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT * FROM cells ORDER BY id")) {
			while (result.next()) {
				UUID worldId = UUID.fromString(result.getString("world_id"));
				CellDefinition cell = new CellDefinition(
						result.getString("id"),
						result.getString("display_name"),
						WardId.of(result.getString("ward_id")),
						new RegionUtils.Cuboid(worldId, result.getInt("min_x"), result.getInt("min_y"), result.getInt("min_z"), result.getInt("max_x"), result.getInt("max_y"), result.getInt("max_z")),
						result.getLong("rent_price"), result.getLong("rent_duration_seconds"), result.getLong("max_rent_duration_seconds"), result.getInt("enabled") != 0
				);
				cells.put(cell.id(), cell);
			}
		}
		return cells;
	}

	public synchronized Map<String, CellLease> loadLeases() throws SQLException {
		Map<String, CellLease> leases = new LinkedHashMap<>();
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT * FROM cell_leases ORDER BY cell_id")) {
			while (result.next()) {
				CellLease lease = new CellLease(result.getString("cell_id"), UUID.fromString(result.getString("owner_uuid")), result.getLong("generation"), result.getLong("started_at"), result.getLong("expires_at"));
				leases.put(lease.cellId(), lease);
			}
		}
		return leases;
	}

	public synchronized List<TrackedCellBlock> loadBlocks() throws SQLException {
		List<TrackedCellBlock> blocks = new ArrayList<>();
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT * FROM cell_blocks")) {
			while (result.next())
				blocks.add(new TrackedCellBlock(result.getString("cell_id"), result.getLong("lease_generation"), UUID.fromString(result.getString("world_id")), result.getInt("x"), result.getInt("y"), result.getInt("z")));
		}
		return blocks;
	}

	public synchronized Map<String, java.util.Set<UUID>> loadMembers(Map<String, CellLease> leases) throws SQLException {
		Map<String, java.util.Set<UUID>> members = new LinkedHashMap<>();
		try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM cell_members ORDER BY cell_id,player_uuid")) {
			while (r.next()) {
				String cellId = r.getString("cell_id");
				CellLease lease = leases.get(cellId);
				if (lease != null && lease.generation() == r.getLong("lease_generation"))
					members.computeIfAbsent(cellId, ignored -> new java.util.LinkedHashSet<>()).add(UUID.fromString(r.getString("player_uuid")));
			}
		}
		return members;
	}

	public synchronized Map<String, CellResetJob> loadResetJobs() throws SQLException {
		Map<String, CellResetJob> jobs = new LinkedHashMap<>();
		try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM cell_reset_jobs")) {
			while (r.next()) {
				Object value = r.getObject("recovery_lot_id");
				Long lot = value instanceof Number number ? number.longValue() : null;
				CellResetJob j = new CellResetJob(r.getString("cell_id"), r.getLong("lease_generation"), UUID.fromString(r.getString("owner_uuid")), CellResetJob.Phase.valueOf(r.getString("phase")), lot);
				jobs.put(j.cellId(), j);
			}
		}
		return jobs;
	}

	public synchronized List<CellSign> loadSigns() throws SQLException {
		List<CellSign> signs = new ArrayList<>();
		try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM cell_signs ORDER BY cell_id")) {
			while (r.next())
				signs.add(new CellSign(r.getString("cell_id"), UUID.fromString(r.getString("world_id")), r.getInt("x"), r.getInt("y"), r.getInt("z")));
		}
		return signs;
	}

	public CompletableFuture<Void> saveCell(CellDefinition cell) {
		return write(() -> upsertCell(cell));
	}

	public CompletableFuture<Void> deleteCell(String id) {
		return write(() -> execute("DELETE FROM cells WHERE id = ?", id));
	}

	public CompletableFuture<Void> saveLease(CellLease lease) {
		return write(() -> upsertLease(lease));
	}

	public CompletableFuture<Void> deleteLease(String cellId) {
		return write(() -> execute("DELETE FROM cell_leases WHERE cell_id = ?", cellId));
	}

	public CompletableFuture<Void> addMember(String cellId, long generation, UUID playerId) {
		return write(() -> execute("INSERT OR IGNORE INTO cell_members VALUES(?,?,?)", cellId, generation, playerId.toString()));
	}

	public CompletableFuture<Void> removeMember(String cellId, long generation, UUID playerId) {
		return write(() -> execute("DELETE FROM cell_members WHERE cell_id=? AND lease_generation=? AND player_uuid=?", cellId, generation, playerId.toString()));
	}

	public CompletableFuture<Void> addBlocks(Collection<TrackedCellBlock> blocks) {
		return write(() -> insertBlocks(blocks));
	}

	public CompletableFuture<Void> removeBlocks(Collection<TrackedCellBlock> blocks) {
		return write(() -> deleteBlocks(blocks));
	}

	public CompletableFuture<Void> deleteBlocks(String cellId, long generation) {
		return write(() -> execute("DELETE FROM cell_blocks WHERE cell_id = ? AND lease_generation = ?", cellId, generation));
	}

	public CompletableFuture<Void> beginReset(CellLease lease) {
		return write(() -> execute("INSERT OR REPLACE INTO cell_reset_jobs VALUES(?,?,?,?,NULL)", lease.cellId(), lease.generation(), lease.ownerId().toString(), CellResetJob.Phase.COLLECTING.name()));
	}

	public CompletableFuture<Void> completeReset(String cellId, long generation, long lotId) {
		return write(() -> completeResetTransaction(cellId, generation, lotId));
	}

	public CompletableFuture<Void> saveSign(CellSign sign) {
		return write(() -> execute("INSERT OR REPLACE INTO cell_signs(world_id,x,y,z,cell_id) VALUES(?,?,?,?,?)", sign.worldId().toString(), sign.x(), sign.y(), sign.z(), sign.cellId()));
	}

	public CompletableFuture<Void> deleteSign(CellSign sign) {
		return write(() -> execute("DELETE FROM cell_signs WHERE world_id=? AND x=? AND y=? AND z=?", sign.worldId().toString(), sign.x(), sign.y(), sign.z()));
	}

	public CompletableFuture<Long> createRecoveryLot(CellLease lease, WardId wardId, List<byte[]> items) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return insertRecoveryLot(lease, wardId, items);
			} catch (SQLException exception) {
				throw new IllegalStateException("Failed to persist cell recovery lot", exception);
			}
		}, writer);
	}

	public CompletableFuture<List<CellRecoveryLot>> loadReadyRecoveryLots() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return readReadyRecoveryLots();
			} catch (SQLException exception) {
				throw new IllegalStateException("Could not load ready recovery lots", exception);
			}
		}, writer);
	}

	private List<CellRecoveryLot> readReadyRecoveryLots() throws SQLException {
		Map<Long, RecoveryBuilder> lots = new LinkedHashMap<>();
		try (PreparedStatement statement = connection.prepareStatement("""
			SELECT lot.id, lot.owner_uuid, lot.ward_id, item.item_data
			FROM cell_recovery_lots lot
			LEFT JOIN cell_recovery_items item ON item.lot_id = lot.id
			WHERE lot.status = 'READY'
			ORDER BY lot.id, item.sort_index
			"""); ResultSet result = statement.executeQuery()) {
			while (result.next()) {
				long id = result.getLong("id");
				RecoveryBuilder builder = lots.get(id);
				if (builder == null) {
					builder = new RecoveryBuilder(
						UUID.fromString(result.getString("owner_uuid")),
						WardId.of(result.getString("ward_id"))
					);
					lots.put(id, builder);
				}
				byte[] item = result.getBytes("item_data");
				if (item != null) builder.items.add(item);
			}
		}
		return lots.entrySet().stream()
			.map(entry -> new CellRecoveryLot(
				entry.getKey(), entry.getValue().ownerId, entry.getValue().wardId, entry.getValue().items
			))
			.toList();
	}

	public CompletableFuture<Void> markRecoveryLotAuctioned(long lotId) {
		return write(() -> markRecoveryLotAuctionedTransaction(lotId));
	}

	private void markRecoveryLotAuctionedTransaction(long lotId) throws SQLException {
		boolean oldAutoCommit = connection.getAutoCommit();
		connection.setAutoCommit(false);
		try {
			execute("UPDATE cell_recovery_lots SET status='AUCTIONED' WHERE id=? AND status='READY'", lotId);
			execute("DELETE FROM cell_recovery_items WHERE lot_id=? AND EXISTS (SELECT 1 FROM cell_recovery_lots WHERE id=? AND status='AUCTIONED')", lotId, lotId);
			connection.commit();
		} catch (SQLException exception) {
			connection.rollback();
			throw exception;
		} finally {
			connection.setAutoCommit(oldAutoCommit);
		}
	}

	private static final class RecoveryBuilder {
		private final UUID ownerId;
		private final WardId wardId;
		private final List<byte[]> items = new ArrayList<>();

		private RecoveryBuilder(UUID ownerId, WardId wardId) {
			this.ownerId = ownerId;
			this.wardId = wardId;
		}
	}

	private CompletableFuture<Void> write(SqlTask task) {
		return CompletableFuture.runAsync(() -> {
			try {
				task.run();
			} catch (SQLException exception) {
				throw new IllegalStateException("Cells database write failed", exception);
			}
		}, writer);
	}

	private void upsertCell(CellDefinition cell) throws SQLException {
		try (PreparedStatement s = connection.prepareStatement("""
				INSERT INTO cells(id,display_name,ward_id,world_id,min_x,min_y,min_z,max_x,max_y,max_z,rent_price,rent_duration_seconds,max_rent_duration_seconds,enabled)
				VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET
				display_name=excluded.display_name,ward_id=excluded.ward_id,world_id=excluded.world_id,min_x=excluded.min_x,min_y=excluded.min_y,min_z=excluded.min_z,
				max_x=excluded.max_x,max_y=excluded.max_y,max_z=excluded.max_z,
				rent_price=excluded.rent_price,rent_duration_seconds=excluded.rent_duration_seconds,max_rent_duration_seconds=excluded.max_rent_duration_seconds,enabled=excluded.enabled
				""")) {
			RegionUtils.Cuboid c = cell.cuboid();
			s.setString(1, cell.id());
			s.setString(2, cell.displayName());
			s.setString(3, cell.wardId().value());
			s.setString(4, c.worldId.toString());
			s.setInt(5, c.minX);
			s.setInt(6, c.minY);
			s.setInt(7, c.minZ);
			s.setInt(8, c.maxX);
			s.setInt(9, c.maxY);
			s.setInt(10, c.maxZ);
			s.setLong(11, cell.rentPrice());
			s.setLong(12, cell.rentDurationSeconds());
			s.setLong(13, cell.maxRentDurationSeconds());
			s.setInt(14, cell.enabled() ? 1 : 0);
			s.executeUpdate();
		}
	}

	private void upsertLease(CellLease lease) throws SQLException {
		try (PreparedStatement s = connection.prepareStatement("""
			INSERT INTO cell_leases(cell_id,owner_uuid,generation,started_at,expires_at) VALUES(?,?,?,?,?)
			ON CONFLICT(cell_id) DO UPDATE SET
			 owner_uuid=excluded.owner_uuid,
			 generation=excluded.generation,
			 started_at=excluded.started_at,
			 expires_at=excluded.expires_at
			""")) {
			s.setString(1, lease.cellId());
			s.setString(2, lease.ownerId().toString());
			s.setLong(3, lease.generation());
			s.setLong(4, lease.startedAtEpochMillis());
			s.setLong(5, lease.expiresAtEpochMillis());
			s.executeUpdate();
		}
	}

	private void insertBlocks(Collection<TrackedCellBlock> blocks) throws SQLException {
		if (blocks.isEmpty()) return;
		try (PreparedStatement s = connection.prepareStatement("INSERT OR REPLACE INTO cell_blocks VALUES(?,?,?,?,?,?)")) {
			for (TrackedCellBlock block : blocks) {
				s.setString(1, block.cellId());
				s.setLong(2, block.leaseGeneration());
				s.setString(3, block.worldId().toString());
				s.setInt(4, block.x());
				s.setInt(5, block.y());
				s.setInt(6, block.z());
				s.addBatch();
			}
			s.executeBatch();
		}
	}

	private void deleteBlocks(Collection<TrackedCellBlock> blocks) throws SQLException {
		if (blocks.isEmpty()) return;
		try (PreparedStatement s = connection.prepareStatement("DELETE FROM cell_blocks WHERE world_id=? AND x=? AND y=? AND z=?")) {
			for (TrackedCellBlock block : blocks) {
				s.setString(1, block.worldId().toString());
				s.setInt(2, block.x());
				s.setInt(3, block.y());
				s.setInt(4, block.z());
				s.addBatch();
			}
			s.executeBatch();
		}
	}

	private long insertRecoveryLot(CellLease lease, WardId wardId, List<byte[]> items) throws SQLException {
		boolean old = connection.getAutoCommit();
		connection.setAutoCommit(false);
		try {
			long lotId;
			try (PreparedStatement s = connection.prepareStatement("INSERT INTO cell_recovery_lots(cell_id,ward_id,owner_uuid,lease_generation,created_at,status) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
				s.setString(1, lease.cellId());
				s.setString(2, wardId.value());
				s.setString(3, lease.ownerId().toString());
				s.setLong(4, lease.generation());
				s.setLong(5, System.currentTimeMillis());
				s.setString(6, "PREPARED");
				s.executeUpdate();
				try (ResultSet keys = s.getGeneratedKeys()) {
					if (!keys.next()) throw new SQLException("No recovery lot id");
					lotId = keys.getLong(1);
				}
			}
			try (PreparedStatement s = connection.prepareStatement("INSERT INTO cell_recovery_items(lot_id,sort_index,item_data) VALUES(?,?,?)")) {
				int index = 0;
				for (byte[] item : items) {
					s.setLong(1, lotId);
					s.setInt(2, index++);
					s.setBytes(3, item);
					s.addBatch();
				}
				s.executeBatch();
			}
			execute("UPDATE cell_reset_jobs SET phase=?, recovery_lot_id=? WHERE cell_id=?", CellResetJob.Phase.PREPARED.name(), lotId, lease.cellId());
			connection.commit();
			return lotId;
		} catch (SQLException e) {
			connection.rollback();
			throw e;
		} finally {
			connection.setAutoCommit(old);
		}
	}

	private void completeResetTransaction(String cellId, long generation, long lotId) throws SQLException {
		boolean old = connection.getAutoCommit();
		connection.setAutoCommit(false);
		try {
			execute("DELETE FROM cell_blocks WHERE cell_id=? AND lease_generation=?", cellId, generation);
			execute("DELETE FROM cell_members WHERE cell_id=? AND lease_generation=?", cellId, generation);
			execute("DELETE FROM cell_leases WHERE cell_id=?", cellId);
			execute("UPDATE cell_recovery_lots SET status='READY' WHERE id=?", lotId);
			execute("DELETE FROM cell_reset_jobs WHERE cell_id=?", cellId);
			connection.commit();
		} catch (SQLException e) {
			connection.rollback();
			throw e;
		} finally {
			connection.setAutoCommit(old);
		}
	}

	private void execute(String sql, Object... values) throws SQLException {
		try (PreparedStatement s = connection.prepareStatement(sql)) {
			for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
			s.executeUpdate();
		}
	}

	public void flush() {
		try {
			writer.submit(() -> {
			}).get();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to flush Cells database", e);
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
