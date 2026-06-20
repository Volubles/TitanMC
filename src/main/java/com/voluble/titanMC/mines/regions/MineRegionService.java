package com.voluble.titanMC.mines.regions;

import com.voluble.titanMC.mines.Mine;
import com.voluble.titanMC.mines.protection.MineProtectionPolicy;
import com.voluble.titanMC.regions.model.BlockBox;
import com.voluble.titanMC.regions.model.CuboidGeometry;
import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.model.RegionKey;
import com.voluble.titanMC.regions.model.WorldId;
import com.voluble.titanMC.regions.service.RegionBatchResult;
import com.voluble.titanMC.regions.service.RegionEngine;
import com.voluble.titanMC.regions.service.RegionMutationBatch;
import com.voluble.titanMC.regions.service.RegionMutationResult;
import com.voluble.titanMC.util.RegionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class MineRegionService {

	public static final int PRIORITY = 100;

	private final RegionEngine regions;

	public MineRegionService(RegionEngine regions) {
		this.regions = Objects.requireNonNull(regions, "regions");
	}

	public void reconcile(Collection<Mine> mines) {
		Objects.requireNonNull(mines, "mines");
		Map<String, Mine> desired = desiredMines(mines);
		Map<String, List<RegionDefinition>> existing = existingMineRegions();
		List<RegionMutationBatch.Operation> operations = new ArrayList<>();

		for (Map.Entry<String, Mine> entry : desired.entrySet()) {
			Mine mine = entry.getValue();
			List<RegionDefinition> candidates = existing.remove(entry.getKey());
			RegionDefinition retained = selectRetained(candidates, mine);
			if (candidates != null) {
				for (RegionDefinition candidate : candidates) {
					if (candidate != retained) {
						operations.add(new RegionMutationBatch.Delete(candidate.id(), candidate.revision()));
					}
				}
			}

			if (retained == null) {
				operations.add(new RegionMutationBatch.Create(definition(mine)));
			} else if (!matches(retained, mine.getCuboid())) {
				operations.add(update(retained, mine.getCuboid()));
			}
		}

		for (List<RegionDefinition> staleRegions : existing.values()) {
			for (RegionDefinition stale : staleRegions) {
				operations.add(new RegionMutationBatch.Delete(stale.id(), stale.revision()));
			}
		}

		if (operations.isEmpty()) return;
		RegionBatchResult result = regions.submit(new RegionMutationBatch(operations)).join();
		if (result instanceof RegionBatchResult.Failure failure) {
			throw new MineRegionException(
				"Failed to reconcile mine regions: " + failure.reason() + " (" + failure.message() + ")"
			);
		}
	}

	public void create(Mine mine) {
		Objects.requireNonNull(mine, "mine");
		requireSuccess("create", regions.create(
			key(mine.getName()),
			world(mine.getCuboid()),
			PRIORITY,
			geometry(mine.getCuboid())
		).join());
	}

	public void redefine(Mine mine, RegionUtils.Cuboid newCuboid) {
		Objects.requireNonNull(mine, "mine");
		Objects.requireNonNull(newCuboid, "newCuboid");
		RegionDefinition existing = find(mine);
		if (existing == null) {
			throw new MineRegionException("Cannot redefine missing mine region: " + mine.getName());
		}
		requireSuccess("redefine", regions.update(
			existing.id(),
			existing.revision(),
			existing.key(),
			world(newCuboid),
			PRIORITY,
			geometry(newCuboid)
		).join());
	}

	public void delete(Mine mine) {
		Objects.requireNonNull(mine, "mine");
		RegionDefinition existing = find(mine);
		if (existing == null) return;
		requireSuccess("delete", regions.delete(existing.id(), existing.revision()).join());
	}

	private Map<String, Mine> desiredMines(Collection<Mine> mines) {
		Map<String, Mine> desired = new LinkedHashMap<>();
		for (Mine mine : mines) {
			Objects.requireNonNull(mine, "mines must not contain null");
			String normalizedName = normalizeName(mine.getName());
			if (desired.putIfAbsent(normalizedName, mine) != null) {
				throw new MineRegionException("Mine names collide when normalized: " + mine.getName());
			}
		}
		return desired;
	}

	private Map<String, List<RegionDefinition>> existingMineRegions() {
		Map<String, List<RegionDefinition>> existing = new LinkedHashMap<>();
		for (RegionDefinition region : regions.snapshot().definitions()) {
			if (!region.key().namespace().equals(MineProtectionPolicy.NAMESPACE)) continue;
			existing.computeIfAbsent(region.key().name(), ignored -> new ArrayList<>()).add(region);
		}
		return existing;
	}

	private static RegionDefinition selectRetained(List<RegionDefinition> candidates, Mine mine) {
		if (candidates == null || candidates.isEmpty()) return null;
		WorldId desiredWorld = world(mine.getCuboid());
		return candidates.stream()
			.filter(candidate -> candidate.worldId().equals(desiredWorld))
			.findFirst()
			.orElse(candidates.getFirst());
	}

	private RegionDefinition find(Mine mine) {
		return regions.find(world(mine.getCuboid()), key(mine.getName()));
	}

	private static RegionMutationBatch.Update update(
		RegionDefinition existing,
		RegionUtils.Cuboid cuboid
	) {
		return new RegionMutationBatch.Update(
			existing.id(),
			existing.revision(),
			existing.key(),
			world(cuboid),
			PRIORITY,
			geometry(cuboid)
		);
	}

	private static RegionDefinition definition(Mine mine) {
		return RegionDefinition.create(
			key(mine.getName()),
			world(mine.getCuboid()),
			PRIORITY,
			geometry(mine.getCuboid())
		);
	}

	private static boolean matches(RegionDefinition region, RegionUtils.Cuboid cuboid) {
		return region.worldId().equals(world(cuboid))
			&& region.priority() == PRIORITY
			&& region.geometry().equals(geometry(cuboid));
	}

	private static RegionKey key(String mineName) {
		return RegionKey.of(MineProtectionPolicy.NAMESPACE, normalizeName(mineName));
	}

	private static String normalizeName(String mineName) {
		return Objects.requireNonNull(mineName, "mineName").toLowerCase(Locale.ROOT);
	}

	private static WorldId world(RegionUtils.Cuboid cuboid) {
		return new WorldId(cuboid.worldId);
	}

	private static BlockBox box(RegionUtils.Cuboid cuboid) {
		return BlockBox.inclusive(
			cuboid.minX, cuboid.minY, cuboid.minZ,
			cuboid.maxX, cuboid.maxY, cuboid.maxZ
		);
	}

	private static CuboidGeometry geometry(RegionUtils.Cuboid cuboid) {
		return new CuboidGeometry(box(cuboid));
	}

	private static void requireSuccess(String operation, RegionMutationResult result) {
		if (result instanceof RegionMutationResult.Failure failure) {
			throw new MineRegionException(
				"Failed to " + operation + " mine region: " + failure.reason() + " (" + failure.message() + ")"
			);
		}
	}
}
