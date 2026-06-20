package com.voluble.titanMC.regions.benchmark;

import com.voluble.titanMC.regions.index.RegionIndexOptions;
import com.voluble.titanMC.regions.index.RegionIndexSnapshot;
import com.voluble.titanMC.regions.model.BlockBox;
import com.voluble.titanMC.regions.model.CuboidGeometry;
import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.model.RegionId;
import com.voluble.titanMC.regions.model.RegionKey;
import com.voluble.titanMC.regions.model.WorldId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class RegionIndexBenchmark {

	private RegionIndexBenchmark() {}

	public static void main(String[] args) throws Exception {
		WorldId world = new WorldId(new UUID(1L, 1L));
		List<RegionDefinition> definitions = new ArrayList<>();
		for (int index = 0; index < 10_000; index++) {
			int gridX = index % 100;
			int gridZ = index / 100;
			definitions.add(new RegionDefinition(
				new RegionId(new UUID(0L, index + 1L)),
				RegionKey.of("benchmark", "r_" + index),
				world,
				index % 10,
				new CuboidGeometry(new BlockBox(gridX * 32, -64, gridZ * 32, gridX * 32 + 16, 320, gridZ * 32 + 16)),
				Instant.EPOCH,
				Instant.EPOCH
			));
		}

		long buildStart = System.nanoTime();
		RegionIndexSnapshot snapshot = RegionIndexSnapshot.build(1L, definitions, RegionIndexOptions.defaults());
		long buildNanos = System.nanoTime() - buildStart;
		Random random = new Random(0x544954414eL);
		long checksum = 0L;
		for (int warmup = 0; warmup < 250_000; warmup++) {
			checksum += snapshot.findAll(world, random.nextInt(3_200), random.nextInt(-64, 320), random.nextInt(3_200)).size();
		}

		int queries = 2_000_000;
		long queryStart = System.nanoTime();
		for (int query = 0; query < queries; query++) {
			checksum += snapshot.findAll(world, random.nextInt(3_200), random.nextInt(-64, 320), random.nextInt(3_200)).size();
		}
		long queryNanos = System.nanoTime() - queryStart;
		double queriesPerSecond = queries / (queryNanos / 1_000_000_000.0);
		System.out.printf("regions=%d buildMs=%.2f queriesPerSecond=%.0f checksum=%d%n",
			definitions.size(), buildNanos / 1_000_000.0, queriesPerSecond, checksum);
	}
}
