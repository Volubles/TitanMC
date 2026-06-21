package com.voluble.titanMC.ranks.persistence;

import com.voluble.titanMC.ranks.model.PlayerRank;
import com.voluble.titanMC.ranks.model.RankId;

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

public final class PlayerRankStorage implements AutoCloseable {

	private static final int SCHEMA_VERSION = 1;
	private final Connection connection;
	private final ExecutorService writer = Executors.newSingleThreadExecutor(
		Thread.ofPlatform().name("titan-rank-writer").factory()
	);

	public PlayerRankStorage(Path databasePath) throws SQLException {
		Objects.requireNonNull(databasePath, "databasePath");
		try {
			Path parent = databasePath.toAbsolutePath().getParent();
			if (parent != null) Files.createDirectories(parent);
			Class.forName("org.sqlite.JDBC");
		} catch (Exception exception) {
			throw new SQLException("Failed to prepare Ranks database", exception);
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
					throw new SQLException("Unsupported Ranks database schema " + version);
				}
			}
			statement.executeUpdate("""
					CREATE TABLE IF NOT EXISTS player_ranks (
					    player_uuid TEXT PRIMARY KEY NOT NULL,
					    rank_id TEXT NOT NULL,
					    assigned_at INTEGER NOT NULL
					)
					""");
			statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
		}
	}

	public synchronized Map<UUID, PlayerRank> loadAll() throws SQLException {
		Map<UUID, PlayerRank> ranks = new LinkedHashMap<>();
		try (Statement statement = connection.createStatement();
			 ResultSet result = statement.executeQuery("SELECT player_uuid, rank_id, assigned_at FROM player_ranks ORDER BY player_uuid")) {
			while (result.next()) {
				UUID playerId = UUID.fromString(result.getString("player_uuid"));
				PlayerRank rank = new PlayerRank(
					playerId,
					RankId.of(result.getString("rank_id")),
					result.getLong("assigned_at")
				);
				ranks.put(playerId, rank);
			}
		}
		return ranks;
	}

	public CompletableFuture<Void> save(PlayerRank rank) {
		Objects.requireNonNull(rank, "rank");
		return write(() -> upsert(rank));
	}

	public CompletableFuture<Void> delete(UUID playerId) {
		Objects.requireNonNull(playerId, "playerId");
		return write(() -> execute(
			"DELETE FROM player_ranks WHERE player_uuid = ?",
			playerId.toString()
		));
	}

	private void upsert(PlayerRank rank) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO player_ranks(player_uuid, rank_id, assigned_at)
				VALUES(?,?,?) ON CONFLICT(player_uuid) DO UPDATE SET
				rank_id = excluded.rank_id,
				assigned_at = excluded.assigned_at
				""")) {
			statement.setString(1, rank.playerId().toString());
			statement.setString(2, rank.rankId().value());
			statement.setLong(3, rank.assignedAtEpochMillis());
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
				throw new IllegalStateException("Ranks database write failed", exception);
			}
		}, writer);
	}

	public void flush() {
		try {
			writer.submit(() -> {
			}).get();
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to flush Ranks database", exception);
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
