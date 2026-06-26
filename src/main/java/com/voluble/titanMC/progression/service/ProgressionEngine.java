package com.voluble.titanMC.progression.service;

import com.voluble.titanMC.progression.event.CredGrantedEvent;
import com.voluble.titanMC.progression.event.PlayerLeveledUpEvent;
import com.voluble.titanMC.progression.model.CredAmount;
import com.voluble.titanMC.progression.model.CredSource;
import com.voluble.titanMC.progression.model.LevelCurve;
import com.voluble.titanMC.progression.model.PlayerProgression;
import com.voluble.titanMC.progression.persistence.ProgressionStorage;
import org.bukkit.event.Event;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

public final class ProgressionEngine implements AutoCloseable {

	private final ProgressionStorage storage;
	private final LevelCurve curve;
	private final int maxLevel;
	private final Consumer<Event> events;
	private final Logger logger;
	private final LongSupplier clock;
	private final Map<UUID, PlayerProgression> cache = new ConcurrentHashMap<>();

	private ProgressionEngine(
		ProgressionStorage storage,
		LevelCurve curve,
		int maxLevel,
		Consumer<Event> events,
		Logger logger,
		LongSupplier clock
	) {
		this.storage = Objects.requireNonNull(storage, "storage");
		this.curve = Objects.requireNonNull(curve, "curve");
		if (maxLevel < 1) throw new IllegalArgumentException("maxLevel must be >= 1 (was " + maxLevel + ")");
		this.maxLevel = maxLevel;
		this.events = Objects.requireNonNull(events, "events");
		this.logger = Objects.requireNonNull(logger, "logger");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	public static ProgressionEngine open(
		Path databasePath,
		LevelCurve curve,
		int maxLevel,
		Consumer<Event> events,
		Logger logger
	) throws SQLException {
		return open(databasePath, curve, maxLevel, events, logger, System::currentTimeMillis);
	}

	static ProgressionEngine open(
		Path databasePath,
		LevelCurve curve,
		int maxLevel,
		Consumer<Event> events,
		Logger logger,
		LongSupplier clock
	) throws SQLException {
		ProgressionStorage storage = new ProgressionStorage(databasePath);
		ProgressionEngine engine = new ProgressionEngine(storage, curve, maxLevel, events, logger, clock);
		engine.load();
		return engine;
	}

	static ProgressionEngine create(
		ProgressionStorage storage,
		LevelCurve curve,
		int maxLevel,
		Consumer<Event> events,
		Logger logger,
		LongSupplier clock
	) throws SQLException {
		ProgressionEngine engine = new ProgressionEngine(storage, curve, maxLevel, events, logger, clock);
		engine.load();
		return engine;
	}

	private void load() throws SQLException {
		cache.clear();
		cache.putAll(storage.loadAll());
	}

	public LevelCurve curve() {
		return curve;
	}

	public int maxLevel() {
		return maxLevel;
	}

	public PlayerProgression current(UUID playerId) {
		Objects.requireNonNull(playerId, "playerId");
		PlayerProgression existing = cache.get(playerId);
		return existing != null ? existing : PlayerProgression.initial(playerId, clock.getAsLong());
	}

	public ProgressionUpdate give(UUID playerId, CredAmount amount, CredSource source) {
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(amount, "amount");
		Objects.requireNonNull(source, "source");
		return apply(playerId, amount.value(), source);
	}

	public ProgressionUpdate take(UUID playerId, CredAmount amount, CredSource source) {
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(amount, "amount");
		Objects.requireNonNull(source, "source");
		return apply(playerId, -amount.value(), source);
	}

	private ProgressionUpdate apply(UUID playerId, long delta, CredSource source) {
		PlayerProgression previous = current(playerId);
		if (delta == 0L) return new ProgressionUpdate(previous, previous, 0L);

		long maxCred = curve.credForLevel(maxLevel);
		long newTotal = clamp(addSafely(previous.totalCred(), delta), 0L, maxCred);
		long applied = newTotal - previous.totalCred();
		if (applied == 0L) {
			return new ProgressionUpdate(previous, previous, 0L);
		}

		int newLevel = Math.min(maxLevel, curve.levelAt(newTotal));
		PlayerProgression updated = previous.with(newTotal, newLevel, clock.getAsLong());
		try {
			storage.saveLatest(updated, failure -> logger.log(java.util.logging.Level.SEVERE,
				"Failed to persist progression for " + playerId + " (" + source.value() + ")", failure));
		} catch (RuntimeException failure) {
			logger.log(java.util.logging.Level.SEVERE,
				"Failed to persist progression for " + playerId + " (" + source.value() + ")", failure);
			throw failure;
		}
		cache.put(playerId, updated);
		events.accept(new CredGrantedEvent(playerId, previous, updated, applied, source));
		if (updated.level() > previous.level()) {
			events.accept(new PlayerLeveledUpEvent(playerId, previous.level(), updated.level(), updated));
		}
		return new ProgressionUpdate(previous, updated, applied);
	}

	private static long addSafely(long a, long b) {
		try {
			return Math.addExact(a, b);
		} catch (ArithmeticException overflow) {
			return b > 0 ? Long.MAX_VALUE : 0L;
		}
	}

	private static long clamp(long value, long min, long max) {
		if (value < min) return min;
		if (value > max) return max;
		return value;
	}

	@Override
	public void close() throws SQLException {
		storage.close();
	}
}
