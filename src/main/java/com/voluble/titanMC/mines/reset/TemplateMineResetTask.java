package com.voluble.titanMC.mines.reset;

import com.voluble.titanMC.mines.Mine;
import com.voluble.titanMC.mines.template.MineTemplate;
import com.voluble.titanMC.util.RegionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

final class TemplateMineResetTask implements MineResetTask {
	private final Plugin plugin;
	private final Mine mine;
	private final RegionUtils.Cuboid cuboid;
	private final CompletableFuture<MineTemplate> loadingTemplate;
	private World world;
	private MineTemplate template;
	private List<BlockData> palette;
	private int chunkX;
	private int chunkZ;
	private int x;
	private int y;
	private int z;
	private Chunk activeChunk;
	private CompletableFuture<Chunk> loadingChunk;
	private CompletableFuture<Chunk> loadingSafeSpawnChunk;
	private boolean initialized;
	private boolean playersTeleported;
	private boolean finished;
	private boolean cancelled;
	private boolean successful = true;

	TemplateMineResetTask(Plugin plugin, Mine mine, CompletableFuture<MineTemplate> loadingTemplate) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.mine = Objects.requireNonNull(mine, "mine");
		this.cuboid = mine.getCuboid();
		this.loadingTemplate = Objects.requireNonNull(loadingTemplate, "loadingTemplate");
	}

	@Override public String name() { return mine.getName(); }
	@Override public int maxBlocksPerSlice() { return mine.getBatchSizePerTick(); }
	@Override public boolean successful() { return successful; }

	@Override
	public void cancel() {
		cancelled = true;
		releaseActiveChunk();
		loadingTemplate.cancel(false);
		if (loadingChunk != null) loadingChunk.cancel(false);
		if (loadingSafeSpawnChunk != null) loadingSafeSpawnChunk.cancel(false);
	}

	@Override
	public MineResetWork process(int maxBlocks, long deadlineNanos) {
		if (maxBlocks <= 0) throw new IllegalArgumentException("maxBlocks must be positive");
		if (finished || cancelled) return new MineResetWork(0, 0, true);
		if (!initialized && !initialize()) return new MineResetWork(0, 0, finished || cancelled);
		int scanned = 0;
		int changed = 0;
		while (scanned < maxBlocks && !finished && !cancelled
			&& (scanned == 0 || System.nanoTime() < deadlineNanos)) {
			if (!activateChunk()) break;
			Block block = activeChunk.getBlock(x & 15, y, z & 15);
			BlockData target = palette.get(template.paletteIndex(x - cuboid.minX, y - cuboid.minY, z - cuboid.minZ));
			if (!block.getBlockData().equals(target)) {
				block.setBlockData(target, false);
				changed++;
			}
			scanned++;
			advanceCursor();
		}
		if (finished || cancelled) releaseActiveChunk();
		return new MineResetWork(scanned, changed, finished || cancelled);
	}

	private boolean initialize() {
		world = Bukkit.getWorld(cuboid.worldId);
		if (world == null) return fail("world is unavailable", null);
		if (template == null) {
			if (!loadingTemplate.isDone()) return false;
			try {
				template = loadingTemplate.join();
				if (template.sizeX() != cuboid.maxX - cuboid.minX + 1
					|| template.sizeY() != cuboid.maxY - cuboid.minY + 1
					|| template.sizeZ() != cuboid.maxZ - cuboid.minZ + 1) {
					return fail("template dimensions do not match the mine region", null);
				}
				palette = template.blockPalette().stream().map(Bukkit::createBlockData).toList();
			} catch (RuntimeException exception) {
				return fail("template could not be loaded", exception);
			}
		}
		if (!playersTeleported && !teleportPlayersOut()) return false;
		chunkX = cuboid.minChunkX();
		chunkZ = cuboid.minChunkZ();
		initialized = true;
		return true;
	}

	private boolean teleportPlayersOut() {
		Location safeSpawn = mine.getSafeSpawn();
		if (safeSpawn == null || safeSpawn.getWorld() == null) {
			playersTeleported = true;
			return true;
		}
		if (loadingSafeSpawnChunk == null) loadingSafeSpawnChunk = safeSpawn.getWorld().getChunkAtAsync(safeSpawn, true);
		if (!loadingSafeSpawnChunk.isDone()) return false;
		loadingSafeSpawnChunk.join();
		for (Player player : world.getPlayers()) {
			if (cuboid.contains(player.getLocation())) player.teleport(safeSpawn);
		}
		playersTeleported = true;
		return true;
	}

	private boolean activateChunk() {
		if (activeChunk != null) return true;
		if (loadingChunk == null) loadingChunk = world.getChunkAtAsync(chunkX, chunkZ, true);
		if (!loadingChunk.isDone()) return false;
		activeChunk = loadingChunk.join();
		loadingChunk = null;
		activeChunk.addPluginChunkTicket(plugin);
		x = Math.max(cuboid.minX, chunkX << 4);
		y = cuboid.minY;
		z = Math.max(cuboid.minZ, chunkZ << 4);
		clearDroppedItemsInActiveChunk();
		return true;
	}

	private void advanceCursor() {
		int chunkMaxZ = Math.min(cuboid.maxZ, (chunkZ << 4) + 15);
		int chunkMinZ = Math.max(cuboid.minZ, chunkZ << 4);
		int chunkMaxX = Math.min(cuboid.maxX, (chunkX << 4) + 15);
		int chunkMinX = Math.max(cuboid.minX, chunkX << 4);
		if (z < chunkMaxZ) { z++; return; }
		z = chunkMinZ;
		if (y < cuboid.maxY) { y++; return; }
		y = cuboid.minY;
		if (x < chunkMaxX) { x++; return; }
		x = chunkMinX;
		releaseActiveChunk();
		if (chunkZ < cuboid.maxChunkZ()) { chunkZ++; return; }
		chunkZ = cuboid.minChunkZ();
		if (chunkX < cuboid.maxChunkX()) { chunkX++; return; }
		finished = true;
	}

	private void clearDroppedItemsInActiveChunk() {
		BoundingBox bounds = new BoundingBox(
			Math.max(cuboid.minX, chunkX << 4), cuboid.minY, Math.max(cuboid.minZ, chunkZ << 4),
			Math.min(cuboid.maxX + 1.0, (chunkX << 4) + 16.0), cuboid.maxY + 1.0,
			Math.min(cuboid.maxZ + 1.0, (chunkZ << 4) + 16.0)
		);
		for (var entity : world.getNearbyEntities(bounds, entity -> entity instanceof Item)) entity.remove();
	}

	private boolean fail(String reason, RuntimeException failure) {
		successful = false;
		finished = true;
		mine.setNextResetEpochMs(System.currentTimeMillis() + 60_000L);
		if (failure == null) plugin.getLogger().severe("Template reset failed for " + mine.getName() + ": " + reason);
		else plugin.getLogger().log(java.util.logging.Level.SEVERE, "Template reset failed for " + mine.getName() + ": " + reason, failure);
		return false;
	}

	private void releaseActiveChunk() {
		if (activeChunk == null) return;
		activeChunk.removePluginChunkTicket(plugin);
		activeChunk = null;
	}
}
