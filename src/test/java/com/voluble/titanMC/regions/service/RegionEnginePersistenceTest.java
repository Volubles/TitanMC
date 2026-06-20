package com.voluble.titanMC.regions.service;

import com.voluble.titanMC.regions.model.BlockBox;
import com.voluble.titanMC.regions.model.CuboidGeometry;
import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.model.RegionKey;
import com.voluble.titanMC.regions.model.WorldId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionEnginePersistenceTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void concurrentSubmissionsAreSerializedAndSurviveRestart() throws Exception {
		Path database = temporaryDirectory.resolve("regions.db");
		WorldId world = new WorldId(UUID.randomUUID());
		List<CompletableFuture<RegionMutationResult>> writes = new ArrayList<>();

		try (RegionEngine engine = RegionEngine.open(database)) {
			for (int index = 0; index < 100; index++) {
				writes.add(engine.create(
					RegionKey.of("cell", "cell_" + index),
					world,
					index % 5,
					new CuboidGeometry(new BlockBox(index * 32, 0, 0, index * 32 + 16, 16, 16))
				));
			}
			CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();
			assertTrue(writes.stream().map(CompletableFuture::join).allMatch(RegionMutationResult::successful));
			assertEquals(100, engine.snapshot().definitions().size());
		}

		try (RegionEngine reloaded = RegionEngine.open(database)) {
			assertEquals(100, reloaded.snapshot().definitions().size());
			assertEquals("cell_42", reloaded.findAll(world, 42 * 32, 1, 1).getFirst().key().name());
		}
	}

	@Test
	void duplicateKeyFailureDoesNotMutateSnapshotOrDatabase() throws Exception {
		Path database = temporaryDirectory.resolve("duplicate.db");
		WorldId world = new WorldId(UUID.randomUUID());
		RegionKey key = RegionKey.of("cell", "alpha");

		try (RegionEngine engine = RegionEngine.open(database)) {
			RegionMutationResult first = engine.create(key, world, 0, new CuboidGeometry(new BlockBox(0, 0, 0, 16, 16, 16))).join();
			RegionMutationResult duplicate = engine.create(key, world, 0, new CuboidGeometry(new BlockBox(32, 0, 0, 48, 16, 16))).join();
			assertInstanceOf(RegionMutationResult.Success.class, first);
			RegionMutationResult.Failure failure = assertInstanceOf(RegionMutationResult.Failure.class, duplicate);
			assertEquals(RegionMutationResult.Reason.DUPLICATE_KEY, failure.reason());
			assertEquals(1, engine.snapshot().definitions().size());
		}

		try (RegionEngine reloaded = RegionEngine.open(database)) {
			assertEquals(1, reloaded.snapshot().definitions().size());
			RegionDefinition stored = reloaded.find(world, key);
			assertEquals(new CuboidGeometry(new BlockBox(0, 0, 0, 16, 16, 16)), stored.geometry());
		}
	}
}
