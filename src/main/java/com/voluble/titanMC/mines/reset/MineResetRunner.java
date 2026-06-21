package com.voluble.titanMC.mines.reset;

import com.voluble.titanMC.mines.Mine;
import com.voluble.titanMC.mines.WeightedPalette;
import com.voluble.titanMC.util.RegionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

import java.util.concurrent.CompletableFuture;

/**
 * Performs a batched reset of a mine's cuboid area.
 * Invoke processBatch() each tick until it returns true (finished).
 */
public final class MineResetRunner implements MineResetTask {

	private final Plugin plugin;
	private final Mine mine;
	private final RegionUtils.Cuboid cuboid;
	private final WeightedPalette palette;

	private World world;
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
	private volatile boolean cancelled;

	public MineResetRunner(Plugin plugin, Mine mine) {
		this.plugin = plugin;
		this.mine = mine;
		this.cuboid = mine.getCuboid();
		this.palette = mine.getPalette();
		this.initialized = false;
		this.finished = false;
		this.cancelled = false;
	}

	public boolean isFinished() { return finished; }

	@Override
	public String name() {
		return mine.getName();
	}

	@Override
	public int maxBlocksPerSlice() {
		return mine.getBatchSizePerTick();
	}

	@Override
	public void cancel() {
		this.cancelled = true;
		releaseActiveChunk();
		if (loadingChunk != null) loadingChunk.cancel(false);
		if (loadingSafeSpawnChunk != null) loadingSafeSpawnChunk.cancel(false);
	}

	@Override
	public MineResetWork process(int maxBlocks, long deadlineNanos) {
		if (maxBlocks <= 0) throw new IllegalArgumentException("maxBlocks must be positive");
		if (finished || cancelled) return new MineResetWork(0, 0, true);
		world = Bukkit.getWorld(cuboid.worldId);
		if (world == null) {
			// World not available; abort this cycle as finished to avoid blocking scheduler
			finished = true;
			return new MineResetWork(0, 0, true);
		}
		if (!initialized) {
			if (!initialize()) return new MineResetWork(0, 0, false);
		}
		int scanned = 0;
		int changed = 0;
		while (scanned < maxBlocks && !finished && !cancelled
				&& (scanned == 0 || System.nanoTime() < deadlineNanos)) {
			if (!activateChunk()) break;
			Block block = activeChunk.getBlock(x & 15, y, z & 15);
			Material m = palette.pickRandomThreadLocal();
			if (block.getType() != m) {
				block.setType(m, false);
				changed++;
			}
			scanned++;
			advanceCursor();
		}
		if (finished || cancelled) releaseActiveChunk();
		return new MineResetWork(scanned, changed, finished || cancelled);
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
		advanceChunk();
	}

	private boolean initialize() {
		if (!playersTeleported && !teleportPlayersOut()) return false;
		chunkX = cuboid.minChunkX();
		chunkZ = cuboid.minChunkZ();
		initialized = true;
		return true;
	}

	private boolean teleportPlayersOut() {
		Location safeSpawn = mine.getSafeSpawn();
		if (safeSpawn == null) {
			playersTeleported = true;
			return true;
		}
		World safeWorld = safeSpawn.getWorld();
		if (safeWorld == null) {
			playersTeleported = true;
			return true;
		}
		if (loadingSafeSpawnChunk == null) {
			loadingSafeSpawnChunk = safeWorld.getChunkAtAsync(safeSpawn, true);
		}
		if (!loadingSafeSpawnChunk.isDone()) return false;
		loadingSafeSpawnChunk.join();
		for (Player p : world.getPlayers()) {
			Location loc = p.getLocation();
			if (cuboid.contains(loc)) {
				p.teleport(safeSpawn);
			}
		}
		playersTeleported = true;
		return true;
	}

	private boolean activateChunk() {
		if (activeChunk != null) return true;
		if (loadingChunk == null) {
			loadingChunk = world.getChunkAtAsync(chunkX, chunkZ, true);
		}
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

	private void clearDroppedItemsInActiveChunk() {
		double minX = Math.max(cuboid.minX, chunkX << 4);
		double maxX = Math.min(cuboid.maxX + 1.0, (chunkX << 4) + 16.0);
		double minZ = Math.max(cuboid.minZ, chunkZ << 4);
		double maxZ = Math.min(cuboid.maxZ + 1.0, (chunkZ << 4) + 16.0);
		BoundingBox bounds = new BoundingBox(
			minX, cuboid.minY, minZ,
			maxX, cuboid.maxY + 1.0, maxZ
		);
		for (var entity : world.getNearbyEntities(bounds, entity -> entity instanceof Item)) {
			entity.remove();
		}
	}

	private void releaseActiveChunk() {
		if (activeChunk == null) return;
		activeChunk.removePluginChunkTicket(plugin);
		activeChunk = null;
	}

	private void advanceChunk() {
		if (chunkZ < cuboid.maxChunkZ()) {
			chunkZ++;
			return;
		}
		chunkZ = cuboid.minChunkZ();
		if (chunkX < cuboid.maxChunkX()) {
			chunkX++;
			return;
		}
		finished = true;
	}
}


