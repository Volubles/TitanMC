package com.voluble.titanMC.milestones.persistence;

import com.voluble.titanMC.milestones.model.MilestoneCompletion;
import com.voluble.titanMC.milestones.model.MilestoneMetric;
import com.voluble.titanMC.milestones.model.MilestoneProgress;
import com.voluble.titanMC.milestones.model.MilestoneProgressKey;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class MilestoneStorage implements AutoCloseable {
	private static final int SCHEMA_VERSION = 1;

	private final Connection connection;
	private final ExecutorService writer = Executors.newSingleThreadExecutor(
		Thread.ofPlatform().name("titan-milestones-writer").factory()
	);
	private final Object latestLock = new Object();
	private final Map<MilestoneProgressKey, MilestoneProgress> latestProgress = new LinkedHashMap<>();
	private final List<MilestoneCompletion> pendingCompletions = new ArrayList<>();
	private boolean drainScheduled;
	private RuntimeException writeFailure;

	public MilestoneStorage(Path databasePath) throws SQLException {
		Objects.requireNonNull(databasePath, "databasePath");
		try {
			Path parent = databasePath.toAbsolutePath().getParent();
			if (parent != null) Files.createDirectories(parent);
			Class.forName("org.sqlite.JDBC");
		} catch (Exception exception) {
			throw new SQLException("Failed to prepare Milestones database", exception);
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
					throw new SQLException("Unsupported Milestones database schema " + version);
				}
			}
			statement.executeUpdate("""
					CREATE TABLE IF NOT EXISTS milestone_progress (
					    player_uuid TEXT NOT NULL,
					    metric TEXT NOT NULL,
					    subject TEXT NOT NULL DEFAULT '',
					    amount INTEGER NOT NULL,
					    updated_at INTEGER NOT NULL,
					    PRIMARY KEY(player_uuid, metric, subject)
					)
					""");
			statement.executeUpdate("""
					CREATE TABLE IF NOT EXISTS milestone_completions (
					    player_uuid TEXT NOT NULL,
					    tier_id TEXT NOT NULL,
					    completed_at INTEGER NOT NULL,
					    PRIMARY KEY(player_uuid, tier_id)
					)
					""");
			statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
		}
	}

	public synchronized Map<MilestoneProgressKey, MilestoneProgress> loadProgress() throws SQLException {
		Map<MilestoneProgressKey, MilestoneProgress> records = new LinkedHashMap<>();
		try (Statement statement = connection.createStatement();
			 ResultSet result = statement.executeQuery("""
				 SELECT player_uuid, metric, subject, amount, updated_at
				 FROM milestone_progress ORDER BY player_uuid, metric, subject
				 """)) {
			while (result.next()) {
				MilestoneProgressKey key = new MilestoneProgressKey(
					UUID.fromString(result.getString("player_uuid")),
					MilestoneMetric.valueOf(result.getString("metric")),
					result.getString("subject")
				);
				records.put(key, new MilestoneProgress(key, result.getLong("amount"), result.getLong("updated_at")));
			}
		}
		return records;
	}

	public synchronized Set<MilestoneCompletion> loadCompletions() throws SQLException {
		Set<MilestoneCompletion> records = new LinkedHashSet<>();
		try (Statement statement = connection.createStatement();
			 ResultSet result = statement.executeQuery("""
				 SELECT player_uuid, tier_id, completed_at FROM milestone_completions ORDER BY player_uuid, tier_id
				 """)) {
			while (result.next()) {
				records.add(new MilestoneCompletion(
					UUID.fromString(result.getString("player_uuid")),
					result.getString("tier_id"),
					result.getLong("completed_at")
				));
			}
		}
		return records;
	}

	public void saveLatest(MilestoneProgress progress, List<MilestoneCompletion> completions, Consumer<RuntimeException> failureHandler) {
		Objects.requireNonNull(progress, "progress");
		Objects.requireNonNull(completions, "completions");
		Objects.requireNonNull(failureHandler, "failureHandler");
		synchronized (latestLock) {
			if (writeFailure != null) throw writeFailure;
			latestProgress.put(progress.key(), progress);
			pendingCompletions.addAll(completions);
			if (!drainScheduled) {
				drainScheduled = true;
				writer.execute(() -> drain(failureHandler));
			}
		}
	}

	private void drain(Consumer<RuntimeException> failureHandler) {
		while (true) {
			List<MilestoneProgress> progressBatch;
			List<MilestoneCompletion> completionBatch;
			synchronized (latestLock) {
				if (latestProgress.isEmpty() && pendingCompletions.isEmpty()) {
					drainScheduled = false;
					latestLock.notifyAll();
					return;
				}
				progressBatch = List.copyOf(latestProgress.values());
				completionBatch = List.copyOf(pendingCompletions);
				latestProgress.clear();
				pendingCompletions.clear();
			}
			try {
				writeBatch(progressBatch, completionBatch);
			} catch (SQLException exception) {
				IllegalStateException failure = new IllegalStateException("Milestones database write failed", exception);
				synchronized (latestLock) {
					for (MilestoneProgress progress : progressBatch) latestProgress.putIfAbsent(progress.key(), progress);
					pendingCompletions.addAll(0, completionBatch);
					writeFailure = failure;
					drainScheduled = false;
					latestLock.notifyAll();
				}
				failureHandler.accept(failure);
				return;
			}
		}
	}

	private synchronized void writeBatch(List<MilestoneProgress> progressBatch, List<MilestoneCompletion> completionBatch) throws SQLException {
		boolean autoCommit = connection.getAutoCommit();
		connection.setAutoCommit(false);
		try {
			upsertProgress(progressBatch);
			insertCompletions(completionBatch);
			connection.commit();
		} catch (SQLException exception) {
			connection.rollback();
			throw exception;
		} finally {
			connection.setAutoCommit(autoCommit);
		}
	}

	private void upsertProgress(List<MilestoneProgress> progressBatch) throws SQLException {
		if (progressBatch.isEmpty()) return;
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO milestone_progress(player_uuid, metric, subject, amount, updated_at)
				VALUES(?,?,?,?,?) ON CONFLICT(player_uuid, metric, subject) DO UPDATE SET
				amount = excluded.amount,
				updated_at = excluded.updated_at
				""")) {
			for (MilestoneProgress progress : progressBatch) {
				statement.setString(1, progress.key().playerId().toString());
				statement.setString(2, progress.key().metric().name());
				statement.setString(3, progress.key().subject());
				statement.setLong(4, progress.amount());
				statement.setLong(5, progress.updatedAtEpochMillis());
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}

	private void insertCompletions(List<MilestoneCompletion> completionBatch) throws SQLException {
		if (completionBatch.isEmpty()) return;
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT OR IGNORE INTO milestone_completions(player_uuid, tier_id, completed_at)
				VALUES(?,?,?)
				""")) {
			for (MilestoneCompletion completion : completionBatch) {
				statement.setString(1, completion.playerId().toString());
				statement.setString(2, completion.tierId());
				statement.setLong(3, completion.completedAtEpochMillis());
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}

	public void flush() {
		try {
			writer.submit(() -> {
			}).get();
			synchronized (latestLock) {
				if (writeFailure != null) throw writeFailure;
				while (drainScheduled || !latestProgress.isEmpty() || !pendingCompletions.isEmpty()) {
					latestLock.wait();
				}
				if (writeFailure != null) throw writeFailure;
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Failed to flush Milestones database", exception);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to flush Milestones database", exception);
		}
	}

	@Override
	public void close() throws SQLException {
		RuntimeException flushFailure = null;
		try {
			flush();
		} catch (RuntimeException exception) {
			flushFailure = exception;
		} finally {
			writer.shutdown();
		}
		try {
			connection.close();
		} catch (SQLException exception) {
			if (flushFailure != null) exception.addSuppressed(flushFailure);
			throw exception;
		}
		if (flushFailure != null) throw flushFailure;
	}
}
