package com.voluble.titanMC.mines.template;

import com.voluble.titanMC.mines.Mine;
import com.voluble.titanMC.util.RegionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class MineTemplateService implements AutoCloseable {
	private static final long CAPTURE_BUDGET_NANOS = 4_000_000L;
	private final Plugin plugin;
	private final MineTemplateStorage storage;
	private final Map<String, CaptureTask> captures = new LinkedHashMap<>();

	public MineTemplateService(Plugin plugin, Path directory) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.storage = new MineTemplateStorage(directory);
	}

	public MineTemplateStorage storage() {
		return storage;
	}

	public boolean capture(Mine mine, String templateId, Consumer<CaptureResult> completion) {
		Objects.requireNonNull(mine, "mine");
		Objects.requireNonNull(completion, "completion");
		String id = com.voluble.titanMC.mines.MineResetDefinition.normalizeTemplateId(templateId);
		if (captures.containsKey(mine.getName())) return false;
		CaptureTask task = new CaptureTask(mine, id, completion);
		captures.put(mine.getName(), task);
		task.runTaskTimer(plugin, 1L, 1L);
		return true;
	}

	public boolean isCapturing(String mineName) {
		return captures.containsKey(mineName);
	}

	public void cancel(String mineName) {
		CaptureTask task = captures.remove(mineName);
		if (task != null) task.stop();
	}

	@Override
	public void close() {
		for (CaptureTask task : new ArrayList<>(captures.values())) task.stop();
		captures.clear();
		storage.close();
	}

	public record CaptureResult(boolean successful, String message) {
	}

	private final class CaptureTask extends BukkitRunnable {
		private final Mine mine;
		private final String templateId;
		private final Consumer<CaptureResult> completion;
		private final RegionUtils.Cuboid cuboid;
		private final int sizeX;
		private final int sizeY;
		private final int sizeZ;
		private final int[] blocks;
		private final Map<String, Integer> paletteIndexes = new LinkedHashMap<>();
		private final ArrayList<String> palette = new ArrayList<>();
		private World world;
		private int chunkX;
		private int chunkZ;
		private int x;
		private int y;
		private int z;
		private Chunk activeChunk;
		private CompletableFuture<Chunk> loadingChunk;
		private boolean initialized;
		private boolean stopped;

		private CaptureTask(Mine mine, String templateId, Consumer<CaptureResult> completion) {
			this.mine = mine;
			this.templateId = templateId;
			this.completion = completion;
			this.cuboid = mine.getCuboid();
			this.sizeX = cuboid.maxX - cuboid.minX + 1;
			this.sizeY = cuboid.maxY - cuboid.minY + 1;
			this.sizeZ = cuboid.maxZ - cuboid.minZ + 1;
			this.blocks = new int[Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ)];
		}

		@Override
		public void run() {
			if (!initialized && !initialize()) return;
			long deadline = System.nanoTime() + CAPTURE_BUDGET_NANOS;
			int scanned = 0;
			while (!stopped && scanned < mine.getBatchSizePerTick()
				&& (scanned == 0 || System.nanoTime() < deadline)) {
				if (!activateChunk()) return;
				String blockData = activeChunk.getBlock(x & 15, y, z & 15).getBlockData().getAsString();
				int paletteIndex = paletteIndexes.computeIfAbsent(blockData, value -> {
					palette.add(value);
					return palette.size() - 1;
				});
				blocks[((y - cuboid.minY) * sizeZ + (z - cuboid.minZ)) * sizeX + (x - cuboid.minX)] = paletteIndex;
				scanned++;
				if (advanceCursor()) {
					finishCapture();
					return;
				}
			}
		}

		private boolean initialize() {
			world = Bukkit.getWorld(cuboid.worldId);
			if (world == null) {
				finish(new CaptureResult(false, "Mine world is unavailable."));
				return false;
			}
			chunkX = cuboid.minChunkX();
			chunkZ = cuboid.minChunkZ();
			initialized = true;
			return true;
		}

		private boolean activateChunk() {
			if (activeChunk != null) return true;
			if (loadingChunk == null) loadingChunk = world.getChunkAtAsync(chunkX, chunkZ, true);
			if (!loadingChunk.isDone()) return false;
			try {
				activeChunk = loadingChunk.join();
			} catch (RuntimeException failure) {
				plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not load a chunk while capturing " + mine.getName(), failure);
				finish(new CaptureResult(false, "A mine chunk could not be loaded."));
				return false;
			}
			loadingChunk = null;
			activeChunk.addPluginChunkTicket(plugin);
			x = Math.max(cuboid.minX, chunkX << 4);
			y = cuboid.minY;
			z = Math.max(cuboid.minZ, chunkZ << 4);
			return true;
		}

		private boolean advanceCursor() {
			int chunkMaxZ = Math.min(cuboid.maxZ, (chunkZ << 4) + 15);
			int chunkMinZ = Math.max(cuboid.minZ, chunkZ << 4);
			int chunkMaxX = Math.min(cuboid.maxX, (chunkX << 4) + 15);
			int chunkMinX = Math.max(cuboid.minX, chunkX << 4);
			if (z < chunkMaxZ) { z++; return false; }
			z = chunkMinZ;
			if (y < cuboid.maxY) { y++; return false; }
			y = cuboid.minY;
			if (x < chunkMaxX) { x++; return false; }
			x = chunkMinX;
			releaseActiveChunk();
			if (chunkZ < cuboid.maxChunkZ()) { chunkZ++; return false; }
			chunkZ = cuboid.minChunkZ();
			if (chunkX < cuboid.maxChunkX()) { chunkX++; return false; }
			return true;
		}

		private void finishCapture() {
			MineTemplate template = MineTemplate.takeOwnership(templateId, sizeX, sizeY, sizeZ, palette, blocks);
			storage.save(template).whenComplete((ignored, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
				if (failure == null) finish(new CaptureResult(true, "Captured template " + templateId + "."));
				else {
					plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not save mine template " + templateId, failure);
					finish(new CaptureResult(false, "The template could not be saved."));
				}
			}));
			stopWorkOnly();
		}

		private void finish(CaptureResult result) {
			if (stopped && !captures.containsKey(mine.getName())) return;
			stopWorkOnly();
			captures.remove(mine.getName(), this);
			completion.accept(result);
		}

		private void stop() {
			stopWorkOnly();
			captures.remove(mine.getName(), this);
		}

		private void stopWorkOnly() {
			if (stopped) return;
			stopped = true;
			cancel();
			releaseActiveChunk();
			if (loadingChunk != null) loadingChunk.cancel(false);
		}

		private void releaseActiveChunk() {
			if (activeChunk == null) return;
			activeChunk.removePluginChunkTicket(plugin);
			activeChunk = null;
		}
	}
}
