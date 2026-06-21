package com.voluble.titanMC.ranks.service;

import com.voluble.titanMC.ranks.event.PlayerRankChangedEvent;
import com.voluble.titanMC.ranks.model.PlayerRank;
import com.voluble.titanMC.ranks.model.PrisonRank;
import com.voluble.titanMC.ranks.model.RankId;
import com.voluble.titanMC.ranks.model.RankupRequirement;
import com.voluble.titanMC.ranks.model.WardDefinition;
import com.voluble.titanMC.ranks.model.WardId;
import com.voluble.titanMC.ranks.persistence.PlayerRankStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerRankServiceTest {
	private static final WardId E = WardId.of("e");
	private static final RankId E4 = RankId.of("e4");
	private static final RankId E3 = RankId.of("e3");

	@TempDir Path directory;
	private PlayerRankStorage storage;
	private RankCatalog catalog;
	private List<PlayerRankChangedEvent> events;
	private Logger logger;
	private AtomicLong clock;

	@BeforeEach
	void setUp() throws Exception {
		storage = new PlayerRankStorage(directory.resolve("ranks.db"));
		catalog = new RankCatalog(
			List.of(new WardDefinition(E, "E Ward", List.of(E4, E3))),
			List.of(
				new PrisonRank(E4, E, "E4"),
				new PrisonRank(E3, E, "E3").withRankup(RankupRequirement.of(100L))
			)
		);
		events = new ArrayList<>();
		logger = Logger.getAnonymousLogger();
		logger.setLevel(Level.OFF);
		clock = new AtomicLong(1_000L);
	}

	@org.junit.jupiter.api.AfterEach
	void tearDown() throws Exception {
		storage.close();
	}

	@Test
	void assignStartingPersistsAndPublishesForNewPlayer() {
		PlayerRankService service = newService();
		UUID player = UUID.randomUUID();

		PlayerRank assigned = service.assignStarting(player);

		assertEquals(E4, assigned.rankId());
		assertEquals(1_000L, assigned.assignedAtEpochMillis());
		assertEquals(1, events.size());
		assertTrue(events.getFirst().previous().isEmpty());
		assertEquals(assigned, events.getFirst().current());
	}

	@Test
	void assignStartingIsIdempotent() {
		PlayerRankService service = newService();
		UUID player = UUID.randomUUID();
		PlayerRank first = service.assignStarting(player);
		events.clear();

		PlayerRank second = service.assignStarting(player);

		assertSame(first, second);
		assertTrue(events.isEmpty());
	}

	@Test
	void applyChangesRankAndPublishesPrevious() {
		PlayerRankService service = newService();
		UUID player = UUID.randomUUID();
		service.assignStarting(player);
		events.clear();
		clock.set(2_000L);

		PlayerRank updated = service.apply(new PlayerRank(player, E3, clock.get()));

		assertEquals(E3, updated.rankId());
		assertEquals(1, events.size());
		assertEquals(E4, events.getFirst().previous().orElseThrow().rankId());
		assertEquals(E3, events.getFirst().current().rankId());
	}

	@Test
	void applyToSameRankIsNoOp() {
		PlayerRankService service = newService();
		UUID player = UUID.randomUUID();
		PlayerRank starter = service.assignStarting(player);
		events.clear();

		PlayerRank applied = service.apply(new PlayerRank(player, E4, 9_999L));

		assertSame(starter, applied);
		assertTrue(events.isEmpty());
	}

	@Test
	void applyRejectsUnknownRank() {
		PlayerRankService service = newService();
		UUID player = UUID.randomUUID();
		service.assignStarting(player);

		assertThrows(IllegalArgumentException.class,
			() -> service.apply(new PlayerRank(player, RankId.of("ghost"), 0L)));
	}

	@Test
	void loadRehydratesCacheFromStorage() throws Exception {
		UUID player = UUID.randomUUID();
		storage.save(new PlayerRank(player, E3, 5_000L)).join();

		PlayerRankService service = newService();
		service.load();

		assertEquals(E3, service.current(player).orElseThrow().rankId());
		assertTrue(events.isEmpty());
	}

	@Test
	void loadHealsStaleRanksToStarter() throws Exception {
		UUID player = UUID.randomUUID();
		storage.save(new PlayerRank(player, RankId.of("ghost"), 5_000L)).join();

		PlayerRankService service = newService();
		service.load();

		PlayerRank healed = service.current(player).orElseThrow();
		assertEquals(E4, healed.rankId());
		assertEquals(1_000L, healed.assignedAtEpochMillis());

		storage.flush();
		assertEquals(E4, storage.loadAll().get(player).rankId());
	}

	private PlayerRankService newService() {
		return new PlayerRankService(catalog, storage, events::add, logger, clock::get);
	}
}
