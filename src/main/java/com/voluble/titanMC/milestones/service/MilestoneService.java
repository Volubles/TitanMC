package com.voluble.titanMC.milestones.service;

import com.voluble.titanMC.milestones.config.MilestoneConfiguration;
import com.voluble.titanMC.milestones.model.MilestoneCompletion;
import com.voluble.titanMC.milestones.model.MilestoneMetric;
import com.voluble.titanMC.milestones.model.MilestoneProgress;
import com.voluble.titanMC.milestones.model.MilestoneProgressKey;
import com.voluble.titanMC.milestones.model.MilestoneTier;
import com.voluble.titanMC.milestones.persistence.MilestoneStorage;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MilestoneService implements AutoCloseable {
	private final MilestoneStorage storage;
	private final Supplier<MilestoneConfiguration> configuration;
	private final Logger logger;
	private final LongSupplier clock;
	private final Map<MilestoneProgressKey, MilestoneProgress> progress = new LinkedHashMap<>();
	private final Set<CompletionKey> completions = new LinkedHashSet<>();

	public MilestoneService(
		MilestoneStorage storage,
		Supplier<MilestoneConfiguration> configuration,
		Logger logger
	) throws SQLException {
		this(storage, configuration, logger, System::currentTimeMillis);
	}

	MilestoneService(
		MilestoneStorage storage,
		Supplier<MilestoneConfiguration> configuration,
		Logger logger,
		LongSupplier clock
	) throws SQLException {
		this.storage = Objects.requireNonNull(storage, "storage");
		this.configuration = Objects.requireNonNull(configuration, "configuration");
		this.logger = Objects.requireNonNull(logger, "logger");
		this.clock = Objects.requireNonNull(clock, "clock");
		load();
	}

	private void load() throws SQLException {
		progress.clear();
		progress.putAll(storage.loadProgress());
		completions.clear();
		for (MilestoneCompletion completion : storage.loadCompletions()) {
			completions.add(CompletionKey.of(completion.playerId(), completion.tierId()));
		}
	}

	public synchronized MilestoneProgress progress(UUID playerId, MilestoneMetric metric, String subject) {
		MilestoneProgressKey key = new MilestoneProgressKey(playerId, metric, subject);
		return progress.getOrDefault(key, new MilestoneProgress(key, 0L, clock.getAsLong()));
	}

	public synchronized boolean completed(UUID playerId, String tierId) {
		return completions.contains(CompletionKey.of(playerId, tierId));
	}

	public synchronized MilestoneUpdate addProgress(UUID playerId, MilestoneMetric metric, String subject, long amount) {
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(metric, "metric");
		if (amount <= 0) throw new IllegalArgumentException("amount must be positive");

		MilestoneProgress previous = progress(playerId, metric, subject);
		long updatedAmount = addSafely(previous.amount(), amount);
		MilestoneProgress current = previous.withAmount(updatedAmount, clock.getAsLong());
		List<MilestoneCompletion> newCompletions = completions(playerId, previous, current);
		progress.put(current.key(), current);
		for (MilestoneCompletion completion : newCompletions) {
			completions.add(CompletionKey.of(completion.playerId(), completion.tierId()));
		}
		storage.saveLatest(current, newCompletions, failure ->
			logger.log(Level.SEVERE, "Failed to persist milestone progress for " + playerId, failure));
		return new MilestoneUpdate(previous, current, newCompletions);
	}

	private List<MilestoneCompletion> completions(UUID playerId, MilestoneProgress previous, MilestoneProgress current) {
		List<MilestoneCompletion> completed = new ArrayList<>();
		for (var track : configuration.get().catalog().tracks(current.key())) {
			for (MilestoneTier tier : track.tiers()) {
				if (current.amount() < tier.target()) continue;
				if (completions.contains(CompletionKey.of(playerId, tier.id()))) continue;
				completed.add(new MilestoneCompletion(playerId, tier.id(), clock.getAsLong()));
			}
		}
		return completed;
	}

	private static long addSafely(long current, long amount) {
		try {
			return Math.addExact(current, amount);
		} catch (ArithmeticException overflow) {
			return Long.MAX_VALUE;
		}
	}

	@Override
	public void close() throws SQLException {
		storage.close();
	}

	private record CompletionKey(UUID playerId, String tierId) {
		private CompletionKey {
			Objects.requireNonNull(playerId, "playerId");
			tierId = Objects.requireNonNull(tierId, "tierId").trim().toLowerCase(java.util.Locale.ROOT);
		}

		private static CompletionKey of(UUID playerId, String tierId) {
			return new CompletionKey(playerId, tierId);
		}
	}
}
