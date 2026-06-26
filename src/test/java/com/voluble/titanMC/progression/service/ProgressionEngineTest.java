package com.voluble.titanMC.progression.service;

import com.voluble.titanMC.progression.event.CredGrantedEvent;
import com.voluble.titanMC.progression.event.PlayerLeveledUpEvent;
import com.voluble.titanMC.progression.model.CredAmount;
import com.voluble.titanMC.progression.model.CredSource;
import com.voluble.titanMC.progression.model.LevelCurve;
import com.voluble.titanMC.progression.model.PlayerProgression;
import com.voluble.titanMC.progression.model.PolynomialLevelCurve;
import com.voluble.titanMC.progression.persistence.ProgressionStorage;
import org.bukkit.event.Event;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionEngineTest {
	private static final CredSource MINING = CredSource.of("mining");
	private static final CredSource ADMIN = CredSource.of("admin");

	@TempDir Path directory;
	private LevelCurve curve;
	private List<Event> events;
	private AtomicLong clock;
	private Logger logger;

	@BeforeEach
	void setUp() {
		curve = new PolynomialLevelCurve(100.0, 1.0); // linear: level L needs (L-1)*100 cred
		events = new ArrayList<>();
		clock = new AtomicLong(1_000L);
		logger = Logger.getAnonymousLogger();
		logger.setLevel(Level.OFF);
	}

	@Test
	void currentReturnsInitialForUnknownPlayer() throws Exception {
		try (ProgressionEngine engine = newEngine(100)) {
			PlayerProgression progression = engine.current(UUID.randomUUID());
			assertEquals(0L, progression.totalCred());
			assertEquals(1, progression.level());
		}
	}

	@Test
	void giveCredAdvancesLevelAndFiresEvents() throws Exception {
		UUID player = UUID.randomUUID();
		try (ProgressionEngine engine = newEngine(100)) {
			ProgressionUpdate update = engine.give(player, CredAmount.of(250L), MINING);

			assertEquals(250L, update.applied());
			assertEquals(3, update.current().level()); // 200 cred = level 3 on linear curve
			assertTrue(update.leveledUp());
			assertEquals(1, events.stream().filter(e -> e instanceof CredGrantedEvent).count());
			assertEquals(1, events.stream().filter(e -> e instanceof PlayerLeveledUpEvent).count());

			PlayerLeveledUpEvent levelUp = events.stream()
				.filter(e -> e instanceof PlayerLeveledUpEvent)
				.map(e -> (PlayerLeveledUpEvent) e)
				.findFirst().orElseThrow();
			assertEquals(1, levelUp.previousLevel());
			assertEquals(3, levelUp.currentLevel());
		}
	}

	@Test
	void giveCredWithoutLevelChangeOnlyFiresGrantedEvent() throws Exception {
		UUID player = UUID.randomUUID();
		try (ProgressionEngine engine = newEngine(100)) {
			engine.give(player, CredAmount.of(50L), MINING);
			events.clear();

			ProgressionUpdate update = engine.give(player, CredAmount.of(25L), MINING);

			assertEquals(75L, update.current().totalCred());
			assertEquals(1, update.current().level());
			assertFalse(update.leveledUp());
			assertEquals(1, events.size());
			assertInstanceOf(CredGrantedEvent.class, events.getFirst());
		}
	}

	@Test
	void giveCapsAtMaxLevel() throws Exception {
		UUID player = UUID.randomUUID();
		try (ProgressionEngine engine = newEngine(5)) { // max 5 = 400 cred on linear curve
			engine.give(player, CredAmount.of(350L), MINING);
			events.clear();

			ProgressionUpdate update = engine.give(player, CredAmount.of(1_000L), MINING);

			assertEquals(50L, update.applied()); // only 50 cred actually applied to reach the cap
			assertEquals(400L, update.current().totalCred());
			assertEquals(5, update.current().level());
			assertEquals(1, events.stream().filter(e -> e instanceof CredGrantedEvent).count());
		}
	}

	@Test
	void awardAtMaxLevelReturnsZeroAppliedAndFiresNoEvents() throws Exception {
		UUID player = UUID.randomUUID();
		try (ProgressionEngine engine = newEngine(5)) {
			engine.give(player, CredAmount.of(400L), MINING);
			events.clear();

			ProgressionUpdate update = engine.give(player, CredAmount.of(1_000L), MINING);

			assertEquals(0L, update.applied());
			assertSame(update.previous(), update.current());
			assertTrue(events.isEmpty());
		}
	}

	@Test
	void takeRemovesCredAndCanDeLevel() throws Exception {
		UUID player = UUID.randomUUID();
		try (ProgressionEngine engine = newEngine(100)) {
			engine.give(player, CredAmount.of(250L), MINING);
			events.clear();

			ProgressionUpdate update = engine.take(player, CredAmount.of(150L), ADMIN);

			assertEquals(-150L, update.applied());
			assertEquals(100L, update.current().totalCred());
			assertEquals(2, update.current().level());
			assertEquals(1, events.stream().filter(e -> e instanceof CredGrantedEvent).count());
			assertEquals(0, events.stream().filter(e -> e instanceof PlayerLeveledUpEvent).count());
		}
	}

	@Test
	void takeFloorsAtZero() throws Exception {
		UUID player = UUID.randomUUID();
		try (ProgressionEngine engine = newEngine(100)) {
			engine.give(player, CredAmount.of(50L), MINING);
			events.clear();

			ProgressionUpdate update = engine.take(player, CredAmount.of(200L), ADMIN);

			assertEquals(-50L, update.applied());
			assertEquals(0L, update.current().totalCred());
			assertEquals(1, update.current().level());
		}
	}

	@Test
	void zeroAwardIsNoOp() throws Exception {
		try (ProgressionEngine engine = newEngine(100)) {
			UUID player = UUID.randomUUID();

			ProgressionUpdate update = engine.give(player, CredAmount.ZERO, MINING);

			assertEquals(0L, update.applied());
			assertSame(update.previous(), update.current());
			assertTrue(events.isEmpty());
		}
	}

	@Test
	void engineReopensWithCachedState() throws Exception {
		UUID player = UUID.randomUUID();
		try (ProgressionEngine engine = newEngine(100)) {
			engine.give(player, CredAmount.of(250L), MINING);
		}

		try (ProgressionEngine reopened = newEngine(100)) {
			PlayerProgression loaded = reopened.current(player);
			assertEquals(250L, loaded.totalCred());
			assertEquals(3, loaded.level());
		}
	}

	private ProgressionEngine newEngine(int maxLevel) throws Exception {
		ProgressionStorage storage = new ProgressionStorage(directory.resolve("progression.db"));
		return ProgressionEngine.create(storage, curve, maxLevel, events::add, logger, clock::get);
	}
}
