package com.voluble.titanMC.progression.persistence;

import com.voluble.titanMC.progression.model.PlayerProgression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionStorageTest {
	@TempDir Path directory;

	@Test
	void savedProgressionSurvivesReopen() throws Exception {
		Path database = directory.resolve("progression.db");
		UUID alice = UUID.randomUUID();
		UUID bob = UUID.randomUUID();
		PlayerProgression aliceState = new PlayerProgression(alice, 1_250L, 3, 1_000L);
		PlayerProgression bobState = new PlayerProgression(bob, 50L, 1, 2_000L);

		try (ProgressionStorage storage = new ProgressionStorage(database)) {
			storage.save(aliceState).join();
			storage.save(bobState).join();
		}

		try (ProgressionStorage storage = new ProgressionStorage(database)) {
			Map<UUID, PlayerProgression> loaded = storage.loadAll();
			assertEquals(2, loaded.size());
			assertEquals(aliceState, loaded.get(alice));
			assertEquals(bobState, loaded.get(bob));
		}
	}

	@Test
	void saveOverwritesExistingProgression() throws Exception {
		Path database = directory.resolve("overwrite.db");
		UUID player = UUID.randomUUID();

		try (ProgressionStorage storage = new ProgressionStorage(database)) {
			storage.save(new PlayerProgression(player, 100L, 1, 1L)).join();
			storage.save(new PlayerProgression(player, 5_000L, 7, 2L)).join();
		}

		try (ProgressionStorage storage = new ProgressionStorage(database)) {
			PlayerProgression loaded = storage.loadAll().get(player);
			assertEquals(5_000L, loaded.totalCred());
			assertEquals(7, loaded.level());
			assertEquals(2L, loaded.updatedAtEpochMillis());
		}
	}

	@Test
	void saveLatestCoalescesPendingProgressionByPlayer() throws Exception {
		Path database = directory.resolve("latest.db");
		UUID player = UUID.randomUUID();
		List<RuntimeException> failures = new ArrayList<>();

		try (ProgressionStorage storage = new ProgressionStorage(database)) {
			storage.saveLatest(new PlayerProgression(player, 100L, 1, 1L), failures::add);
			storage.saveLatest(new PlayerProgression(player, 200L, 2, 2L), failures::add);
			storage.saveLatest(new PlayerProgression(player, 300L, 3, 3L), failures::add);
			storage.flush();
		}

		try (ProgressionStorage storage = new ProgressionStorage(database)) {
			PlayerProgression loaded = storage.loadAll().get(player);
			assertEquals(300L, loaded.totalCred());
			assertEquals(3, loaded.level());
			assertEquals(3L, loaded.updatedAtEpochMillis());
			assertTrue(failures.isEmpty());
		}
	}

	@Test
	void closeFlushesLatestProgression() throws Exception {
		Path database = directory.resolve("close-latest.db");
		UUID player = UUID.randomUUID();

		try (ProgressionStorage storage = new ProgressionStorage(database)) {
			storage.saveLatest(new PlayerProgression(player, 450L, 4, 10L), failure -> {
				throw failure;
			});
		}

		try (ProgressionStorage storage = new ProgressionStorage(database)) {
			PlayerProgression loaded = storage.loadAll().get(player);
			assertEquals(450L, loaded.totalCred());
			assertEquals(4, loaded.level());
		}
	}

	@Test
	void deleteRemovesProgression() throws Exception {
		Path database = directory.resolve("delete.db");
		UUID player = UUID.randomUUID();

		try (ProgressionStorage storage = new ProgressionStorage(database)) {
			storage.save(new PlayerProgression(player, 100L, 1, 1L)).join();
			storage.delete(player).join();
		}

		try (ProgressionStorage storage = new ProgressionStorage(database)) {
			assertTrue(storage.loadAll().isEmpty());
		}
	}

	@Test
	void emptyDatabaseLoadsEmptyMap() throws Exception {
		Path database = directory.resolve("empty.db");
		try (ProgressionStorage storage = new ProgressionStorage(database)) {
			assertTrue(storage.loadAll().isEmpty());
		}
	}
}
