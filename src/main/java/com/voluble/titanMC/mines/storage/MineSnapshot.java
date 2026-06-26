package com.voluble.titanMC.mines.storage;

import com.voluble.titanMC.mines.Mine;
import com.voluble.titanMC.mines.MineResetDefinition;
import com.voluble.titanMC.mines.breaking.MineBreakProfile;
import com.voluble.titanMC.util.RegionUtils;
import org.bukkit.Location;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

record MineSnapshot(
	String name,
	UUID worldId,
	int minX,
	int minY,
	int minZ,
	int maxX,
	int maxY,
	int maxZ,
	int intervalSeconds,
	int batchPerTick,
	boolean enabled,
	int autoResetBelowPercent,
	double credMultiplier,
	int brokenBlocks,
	long nextResetEpochMs,
	SafeSpawnSnapshot safeSpawn,
	String resetType,
	String templateId,
	String breakMode,
	Set<String> diggableMaterials,
	Map<String, Integer> palette
) {
	static MineSnapshot from(Mine mine) {
		RegionUtils.Cuboid cuboid = mine.getCuboid();
		Location safeSpawn = mine.getSafeSpawn();
		SafeSpawnSnapshot safeSpawnSnapshot = null;
		if (safeSpawn != null && safeSpawn.getWorld() != null) {
			safeSpawnSnapshot = new SafeSpawnSnapshot(
				safeSpawn.getWorld().getUID(),
				safeSpawn.getX(),
				safeSpawn.getY(),
				safeSpawn.getZ(),
				safeSpawn.getYaw(),
				safeSpawn.getPitch()
			);
		}
		MineResetDefinition reset = mine.getResetDefinition();
		MineBreakProfile breaks = mine.getBreakProfile();
		return new MineSnapshot(
			mine.getName(),
			cuboid.worldId,
			cuboid.minX,
			cuboid.minY,
			cuboid.minZ,
			cuboid.maxX,
			cuboid.maxY,
			cuboid.maxZ,
			mine.getResetIntervalSeconds(),
			mine.getBatchSizePerTick(),
			mine.isEnabled(),
			mine.getAutoResetBelowPercent(),
			mine.getCredMultiplier(),
			mine.getBrokenBlocks(),
			mine.getNextResetEpochMs(),
			safeSpawnSnapshot,
			reset instanceof MineResetDefinition.Template ? "TEMPLATE" : "PALETTE",
			reset instanceof MineResetDefinition.Template template ? template.templateId() : null,
			breaks instanceof MineBreakProfile.AllowList ? "ALLOW_LIST" : "UNRESTRICTED",
			breaks instanceof MineBreakProfile.AllowList allowList
				? allowList.materials().stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet())
				: Set.of(),
			Collections.unmodifiableMap(new LinkedHashMap<>(mine.getPalette().toConfigMap()))
		);
	}

	record SafeSpawnSnapshot(
		UUID worldId,
		double x,
		double y,
		double z,
		float yaw,
		float pitch
	) {}
}
