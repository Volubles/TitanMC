package com.voluble.titanMC.mines.regions;

import com.voluble.titanMC.mines.Mine;
import com.voluble.titanMC.mines.WeightedPalette;
import com.voluble.titanMC.regions.model.BlockBox;
import com.voluble.titanMC.regions.model.CuboidGeometry;
import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.model.RegionKey;
import com.voluble.titanMC.regions.model.WorldId;
import com.voluble.titanMC.regions.service.RegionEngine;
import com.voluble.titanMC.util.RegionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MineRegionServiceTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void reconcilesCreatesUpdatesAndDeletesMineRegions() throws Exception {
		UUID worldId = new UUID(20L, 1L);
		UUID movedWorldId = new UUID(20L, 2L);
		Mine alpha = mine("Alpha", cuboid(worldId, 0, 0, 0, 9, 9, 9));
		Mine beta = mine("beta", cuboid(worldId, 20, 0, 20, 29, 9, 29));

		try (RegionEngine engine = RegionEngine.open(temporaryDirectory.resolve("regions.db"))) {
			MineRegionService service = new MineRegionService(engine);

			service.reconcile(List.of(alpha, beta));
			assertEquals(2, engine.snapshot().definitions().size());
			assertRegion(engine, alpha);
			assertRegion(engine, beta);

			RegionUtils.Cuboid moved = cuboid(movedWorldId, 40, 5, 40, 49, 14, 49);
			service.redefine(alpha, moved);
			alpha.setCuboid(moved);
			assertRegion(engine, alpha);

			service.delete(beta);
			assertNull(engine.find(new WorldId(worldId), RegionKey.of("mine", "beta")));
		}
	}

	@Test
	void reconciliationRemovesStaleMineRegionsWithoutTouchingOtherNamespaces() throws Exception {
		UUID worldId = new UUID(21L, 1L);
		WorldId world = new WorldId(worldId);
		try (RegionEngine engine = RegionEngine.open(temporaryDirectory.resolve("stale.db"))) {
			engine.create(
				RegionKey.of("mine", "stale"), world, MineRegionService.PRIORITY,
				new CuboidGeometry(BlockBox.inclusive(0, 0, 0, 5, 5, 5))
			).join();
			engine.create(
				RegionKey.of("cell", "keep"), world, 200,
				new CuboidGeometry(BlockBox.inclusive(10, 0, 10, 15, 5, 15))
			).join();

			new MineRegionService(engine).reconcile(List.of());

			assertNull(engine.find(world, RegionKey.of("mine", "stale")));
			assertEquals("cell:keep", engine.find(world, RegionKey.of("cell", "keep")).key().toString());
		}
	}

	@Test
	void rejectsMineNamesThatCollideAfterRegionNormalization() throws Exception {
		UUID worldId = new UUID(22L, 1L);
		try (RegionEngine engine = RegionEngine.open(temporaryDirectory.resolve("collision.db"))) {
			MineRegionService service = new MineRegionService(engine);

			assertThrows(MineRegionException.class, () -> service.reconcile(List.of(
				mine("Alpha", cuboid(worldId, 0, 0, 0, 5, 5, 5)),
				mine("alpha", cuboid(worldId, 10, 0, 10, 15, 5, 15))
			)));
		}
	}

	private static void assertRegion(RegionEngine engine, Mine mine) {
		RegionDefinition region = engine.find(
			new WorldId(mine.getCuboid().worldId),
			RegionKey.of("mine", mine.getName())
		);
		assertEquals(MineRegionService.PRIORITY, region.priority());
		assertEquals(new CuboidGeometry(BlockBox.inclusive(
			mine.getCuboid().minX,
			mine.getCuboid().minY,
			mine.getCuboid().minZ,
			mine.getCuboid().maxX,
			mine.getCuboid().maxY,
			mine.getCuboid().maxZ
		)), region.geometry());
	}

	private static Mine mine(String name, RegionUtils.Cuboid cuboid) {
		return new Mine(name, cuboid, 900, true, 1500, new WeightedPalette());
	}

	private static RegionUtils.Cuboid cuboid(
		UUID worldId,
		int minX,
		int minY,
		int minZ,
		int maxX,
		int maxY,
		int maxZ
	) {
		return new RegionUtils.Cuboid(worldId, minX, minY, minZ, maxX, maxY, maxZ);
	}
}
