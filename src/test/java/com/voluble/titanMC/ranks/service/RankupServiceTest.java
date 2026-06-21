package com.voluble.titanMC.ranks.service;

import com.voluble.titanMC.ranks.model.PrisonRank;
import com.voluble.titanMC.ranks.model.RankId;
import com.voluble.titanMC.ranks.model.RankupRequirement;
import com.voluble.titanMC.ranks.model.WardDefinition;
import com.voluble.titanMC.ranks.model.WardId;
import com.voluble.titanMC.ranks.persistence.PlayerRankStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RankupServiceTest {
	private static final WardId E = WardId.of("e");
	private static final WardId D = WardId.of("d");
	private static final RankId E4 = RankId.of("e4");
	private static final RankId E3 = RankId.of("e3");
	private static final RankId E2 = RankId.of("e2");
	private static final RankId D4 = RankId.of("d4");

	@TempDir Path directory;
	private PlayerRankStorage storage;
	private RankCatalog catalog;
	private PlayerRankService players;
	private TestEconomy economy;
	private AtomicLong clock;

	@BeforeEach
	void setUp() throws Exception {
		storage = new PlayerRankStorage(directory.resolve("ranks.db"));
		catalog = new RankCatalog(
			List.of(
				new WardDefinition(E, "E Ward", List.of(E4, E3, E2)),
				new WardDefinition(D, "D Ward", List.of(D4))
			),
			List.of(
				new PrisonRank(E4, E, "E4"),
				new PrisonRank(E3, E, "E3").withRankup(RankupRequirement.of(1_000L)),
				new PrisonRank(E2, E, "E2").withRankup(RankupRequirement.of(2_500L, E4)),
				new PrisonRank(D4, D, "D4").withRankup(RankupRequirement.of(10_000L))
			)
		);
		clock = new AtomicLong(1_000L);
		Logger logger = Logger.getAnonymousLogger();
		logger.setLevel(Level.OFF);
		players = new PlayerRankService(catalog, storage, e -> {}, logger, clock::get);
		economy = new TestEconomy();
	}

	@AfterEach
	void tearDown() throws Exception {
		storage.close();
	}

	@Test
	void rankupChargesAndAdvances() {
		UUID player = UUID.randomUUID();
		players.assignStarting(player);
		economy.deposit(player, 5_000L);

		RankupResult result = newService().rankup(player);

		RankupResult.Success success = assertInstanceOf(RankupResult.Success.class, result);
		assertEquals(E4, success.previous().rankId());
		assertEquals(E3, success.current().rankId());
		assertEquals(1_000L, success.charged());
		assertEquals(4_000L, economy.balance(player));
		assertEquals(E3, players.current(player).orElseThrow().rankId());
	}

	@Test
	void insufficientFundsLeavesPlayerUnchanged() {
		UUID player = UUID.randomUUID();
		players.assignStarting(player);
		economy.deposit(player, 100L);

		RankupResult result = newService().rankup(player);

		RankupResult.InsufficientFunds funds = assertInstanceOf(RankupResult.InsufficientFunds.class, result);
		assertEquals(1_000L, funds.needed());
		assertEquals(100.0, funds.balance());
		assertEquals(100L, economy.balance(player));
		assertEquals(E4, players.current(player).orElseThrow().rankId());
	}

	@Test
	void economyUnavailableReturnsExplicitResult() {
		UUID player = UUID.randomUUID();
		players.assignStarting(player);

		RankupService service = new RankupService(catalog, players, RankEconomy.unavailable(), clock::get);
		RankupResult result = service.rankup(player);

		assertInstanceOf(RankupResult.EconomyUnavailable.class, result);
		assertEquals(E4, players.current(player).orElseThrow().rankId());
	}

	@Test
	void atMaxRankWhenNoNextExists() {
		UUID player = UUID.randomUUID();
		players.assignStarting(player);
		players.apply(players.current(player).orElseThrow().withRank(D4, 0L));
		economy.deposit(player, 1_000_000L);

		RankupResult result = newService().rankup(player);

		assertInstanceOf(RankupResult.AtMaxRank.class, result);
	}

	@Test
	void noCurrentRankWhenPlayerNeverJoined() {
		RankupResult result = newService().rankup(UUID.randomUUID());

		assertInstanceOf(RankupResult.NoCurrentRank.class, result);
	}

	@Test
	void missingRequirementCheckedAgainstExplicitRank() {
		UUID player = UUID.randomUUID();
		players.assignStarting(player);
		players.apply(players.current(player).orElseThrow().withRank(E3, 0L));
		economy.deposit(player, 100_000L);

		RankupResult result = newService().rankup(player);

		assertInstanceOf(RankupResult.Success.class, result);
		assertEquals(E2, players.current(player).orElseThrow().rankId());
	}

	@Test
	void rankupRunsAcrossWardBoundary() {
		UUID player = UUID.randomUUID();
		players.assignStarting(player);
		players.apply(players.current(player).orElseThrow().withRank(E2, 0L));
		economy.deposit(player, 50_000L);

		RankupResult result = newService().rankup(player);

		RankupResult.Success success = assertInstanceOf(RankupResult.Success.class, result);
		assertEquals(D4, success.current().rankId());
	}

	@Test
	void failedWithdrawIsReportedAsInsufficient() {
		UUID player = UUID.randomUUID();
		players.assignStarting(player);
		economy.deposit(player, 5_000L);
		economy.failNextWithdraw();

		RankupResult result = newService().rankup(player);

		assertInstanceOf(RankupResult.InsufficientFunds.class, result);
		assertEquals(5_000L, economy.balance(player));
	}

	private RankupService newService() {
		return new RankupService(catalog, players, economy, clock::get);
	}

	private static final class TestEconomy implements RankEconomy {
		private final Map<UUID, Long> balances = new HashMap<>();
		private final List<UUID> withdrawAttempts = new ArrayList<>();
		private boolean nextWithdrawFails;

		void deposit(UUID id, long amount) {
			balances.merge(id, amount, Long::sum);
		}

		void failNextWithdraw() {
			nextWithdrawFails = true;
		}

		@Override
		public boolean available() {
			return true;
		}

		@Override
		public boolean has(UUID playerId, long amount) {
			return balances.getOrDefault(playerId, 0L) >= amount;
		}

		@Override
		public double balance(UUID playerId) {
			return balances.getOrDefault(playerId, 0L).doubleValue();
		}

		@Override
		public boolean withdraw(UUID playerId, long amount) {
			withdrawAttempts.add(playerId);
			if (nextWithdrawFails) {
				nextWithdrawFails = false;
				return false;
			}
			long current = balances.getOrDefault(playerId, 0L);
			if (current < amount) return false;
			balances.put(playerId, current - amount);
			return true;
		}
	}
}
