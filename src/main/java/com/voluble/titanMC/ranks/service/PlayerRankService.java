package com.voluble.titanMC.ranks.service;

import com.voluble.titanMC.ranks.event.PlayerRankChangedEvent;
import com.voluble.titanMC.ranks.model.PlayerRank;
import com.voluble.titanMC.ranks.model.PrisonRank;
import com.voluble.titanMC.ranks.model.RankId;
import com.voluble.titanMC.ranks.persistence.PlayerRankStorage;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

public final class PlayerRankService {
	private final RankCatalog catalog;
	private final PlayerRankStorage storage;
	private final Consumer<PlayerRankChangedEvent> publisher;
	private final Logger logger;
	private final LongSupplier clock;
	private final Map<UUID, PlayerRank> cache = new ConcurrentHashMap<>();

	public PlayerRankService(
			RankCatalog catalog,
			PlayerRankStorage storage,
			Consumer<PlayerRankChangedEvent> publisher,
			Logger logger
	) {
		this(catalog, storage, publisher, logger, System::currentTimeMillis);
	}

	PlayerRankService(
			RankCatalog catalog,
			PlayerRankStorage storage,
			Consumer<PlayerRankChangedEvent> publisher,
			Logger logger,
			LongSupplier clock
	) {
		this.catalog = Objects.requireNonNull(catalog, "catalog");
		this.storage = Objects.requireNonNull(storage, "storage");
		this.publisher = Objects.requireNonNull(publisher, "publisher");
		this.logger = Objects.requireNonNull(logger, "logger");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	public void load() throws SQLException {
		cache.clear();
		Map<UUID, PlayerRank> stored = new LinkedHashMap<>(storage.loadAll());
		for (Map.Entry<UUID, PlayerRank> entry : stored.entrySet()) {
			PlayerRank rank = entry.getValue();
			if (catalog.findRank(rank.rankId()).isPresent()) {
				cache.put(entry.getKey(), rank);
				continue;
			}
			RankId starter = starterRank().id();
			logger.warning(
				"Player " + entry.getKey() + " had unknown rank " + rank.rankId().value()
					+ "; resetting to starter " + starter.value()
			);
			PlayerRank healed = new PlayerRank(entry.getKey(), starter, clock.getAsLong());
			storage.save(healed).join();
			cache.put(entry.getKey(), healed);
		}
	}

	public Optional<PlayerRank> current(UUID playerId) {
		return Optional.ofNullable(cache.get(Objects.requireNonNull(playerId, "playerId")));
	}

	public PlayerRank assignStarting(UUID playerId) {
		Objects.requireNonNull(playerId, "playerId");
		PlayerRank existing = cache.get(playerId);
		if (existing != null) return existing;
		PlayerRank starter = new PlayerRank(playerId, starterRank().id(), clock.getAsLong());
		return persistAndPublish(null, starter);
	}

	public PlayerRank apply(PlayerRank updated) {
		Objects.requireNonNull(updated, "updated");
		PlayerRank previous = cache.get(updated.playerId());
		return persistAndPublish(previous, updated);
	}

	private PlayerRank persistAndPublish(PlayerRank previous, PlayerRank next) {
		if (catalog.findRank(next.rankId()).isEmpty()) {
			throw new IllegalArgumentException("Unknown rank: " + next.rankId().value());
		}
		if (previous != null && previous.rankId().equals(next.rankId())) {
			return previous;
		}
		storage.save(next).join();
		cache.put(next.playerId(), next);
		publisher.accept(new PlayerRankChangedEvent(next.playerId(), previous, next));
		return next;
	}

	private PrisonRank starterRank() {
		return catalog.ranks().getFirst();
	}
}
