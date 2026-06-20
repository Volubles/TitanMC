package com.voluble.titanMC.regions.index;

import com.voluble.titanMC.regions.model.BlockBox;
import com.voluble.titanMC.regions.model.CuboidGeometry;
import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.model.RegionId;
import com.voluble.titanMC.regions.model.RegionKey;
import com.voluble.titanMC.regions.model.WorldId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegionIndexSnapshotTest {

	private static final Comparator<RegionDefinition> QUERY_ORDER = Comparator
		.comparingInt(RegionDefinition::priority).reversed()
		.thenComparing(RegionDefinition::key)
		.thenComparing(RegionDefinition::id);

	@Test
	void isolatesWorldsAndOrdersOverlapsDeterministically() throws Exception {
		WorldId firstWorld = world(1);
		WorldId secondWorld = world(2);
		RegionDefinition low = region(1, firstWorld, "low", 10, new BlockBox(-10, -64, -10, 11, 100, 11));
		RegionDefinition high = region(2, firstWorld, "high", 50, new BlockBox(0, 0, 0, 20, 20, 20));
		RegionDefinition otherWorld = region(3, secondWorld, "other", 100, new BlockBox(0, 0, 0, 20, 20, 20));

		RegionIndexSnapshot snapshot = RegionIndexSnapshot.build(1L, List.of(low, high, otherWorld), RegionIndexOptions.defaults());

		assertEquals(List.of(high, low), snapshot.findAll(firstWorld, 5, 5, 5));
		assertEquals(List.of(otherWorld), snapshot.findAll(secondWorld, 5, 5, 5));
		assertEquals(List.of(), snapshot.findAll(world(99), 5, 5, 5));
	}

	@Test
	void rejectsDuplicateKeysWithinAWorld() {
		WorldId world = world(1);
		RegionDefinition first = region(1, world, "same", 0, new BlockBox(0, 0, 0, 1, 1, 1));
		RegionDefinition second = region(2, world, "same", 0, new BlockBox(2, 0, 0, 3, 1, 1));

		assertThrows(RegionIndexBuildException.class,
			() -> RegionIndexSnapshot.build(1L, List.of(first, second), RegionIndexOptions.defaults()));
	}

	@Test
	void randomizedQueriesMatchNaiveReference() throws Exception {
		Random random = new Random(0x544954414eL);
		List<WorldId> worlds = List.of(world(1), world(2), world(3));
		List<RegionDefinition> definitions = new ArrayList<>();
		for (int index = 0; index < 250; index++) {
			int minX = random.nextInt(-600, 600);
			int minY = random.nextInt(-64, 280);
			int minZ = random.nextInt(-600, 600);
			int sizeX = random.nextInt(1, 65);
			int sizeY = random.nextInt(1, 33);
			int sizeZ = random.nextInt(1, 65);
			definitions.add(region(
				index + 1,
				worlds.get(random.nextInt(worlds.size())),
				"region_" + index,
				random.nextInt(-10, 11),
				new BlockBox(minX, minY, minZ, minX + sizeX, minY + sizeY, minZ + sizeZ)
			));
		}

		RegionIndexSnapshot snapshot = RegionIndexSnapshot.build(1L, definitions, RegionIndexOptions.defaults());
		for (int query = 0; query < 10_000; query++) {
			WorldId world = worlds.get(random.nextInt(worlds.size()));
			int x = random.nextInt(-700, 700);
			int y = random.nextInt(-80, 330);
			int z = random.nextInt(-700, 700);
			List<RegionDefinition> expected = definitions.stream()
				.filter(region -> region.worldId().equals(world) && region.contains(x, y, z))
				.sorted(QUERY_ORDER)
				.toList();
			assertEquals(expected, snapshot.findAll(world, x, y, z));
		}
	}

	private static RegionDefinition region(long id, WorldId world, String name, int priority, BlockBox box) {
		return new RegionDefinition(
			new RegionId(new UUID(0L, id)),
			RegionKey.of("test", name),
			world,
			priority,
			new CuboidGeometry(box),
			Instant.EPOCH,
			Instant.EPOCH
		);
	}

	private static WorldId world(long id) {
		return new WorldId(new UUID(1L, id));
	}
}
