package com.voluble.titanMC.ranks.persistence;

import com.voluble.titanMC.ranks.model.PlayerRank;
import com.voluble.titanMC.ranks.model.RankId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerRankStorageTest {
	@TempDir Path directory;

	@Test
	void savedRanksSurviveReopen() throws Exception {
		Path database = directory.resolve("ranks.db");
		UUID alice = UUID.randomUUID();
		UUID bob = UUID.randomUUID();
		PlayerRank aliceRank = new PlayerRank(alice, RankId.of("e3"), 1_000L);
		PlayerRank bobRank = new PlayerRank(bob, RankId.of("d4"), 2_000L);

		try (PlayerRankStorage storage = new PlayerRankStorage(database)) {
			storage.save(aliceRank).join();
			storage.save(bobRank).join();
		}

		try (PlayerRankStorage storage = new PlayerRankStorage(database)) {
			Map<UUID, PlayerRank> loaded = storage.loadAll();
			assertEquals(2, loaded.size());
			assertEquals(aliceRank, loaded.get(alice));
			assertEquals(bobRank, loaded.get(bob));
		}
	}

	@Test
	void saveOverwritesExistingRank() throws Exception {
		Path database = directory.resolve("overwrite.db");
		UUID player = UUID.randomUUID();

		try (PlayerRankStorage storage = new PlayerRankStorage(database)) {
			storage.save(new PlayerRank(player, RankId.of("e4"), 1_000L)).join();
			storage.save(new PlayerRank(player, RankId.of("e3"), 2_500L)).join();
		}

		try (PlayerRankStorage storage = new PlayerRankStorage(database)) {
			PlayerRank loaded = storage.loadAll().get(player);
			assertEquals(RankId.of("e3"), loaded.rankId());
			assertEquals(2_500L, loaded.assignedAtEpochMillis());
		}
	}

	@Test
	void deleteRemovesRank() throws Exception {
		Path database = directory.resolve("delete.db");
		UUID player = UUID.randomUUID();

		try (PlayerRankStorage storage = new PlayerRankStorage(database)) {
			storage.save(new PlayerRank(player, RankId.of("e4"), 1_000L)).join();
			storage.delete(player).join();
		}

		try (PlayerRankStorage storage = new PlayerRankStorage(database)) {
			Map<UUID, PlayerRank> loaded = storage.loadAll();
			assertTrue(loaded.isEmpty());
			assertNull(loaded.get(player));
		}
	}

	@Test
	void emptyDatabaseLoadsEmptyMap() throws Exception {
		Path database = directory.resolve("empty.db");
		try (PlayerRankStorage storage = new PlayerRankStorage(database)) {
			assertTrue(storage.loadAll().isEmpty());
		}
	}
}
