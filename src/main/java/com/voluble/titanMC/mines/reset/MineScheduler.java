package com.voluble.titanMC.mines.reset;

import com.voluble.titanMC.mines.Mine;
import com.voluble.titanMC.mines.MineManager;
import com.voluble.titanMC.util.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class MineScheduler {

	private static final long RESET_BUDGET_NANOS = 4_000_000L;
	private final Plugin plugin;
	private final MineManager manager;
	private final MineResetQueue resetQueue = new MineResetQueue();
	private final MineResetTaskFactory resetTasks;
	private final Map<String, Long> resetTimes = new HashMap<>(); // Track when depletion resets are triggered
	private BukkitTask resetTask;
	private BukkitTask scheduleTask;
	private long totalScannedBlocks;
	private long totalChangedBlocks;
	private long lastResetTickNanos;
	private static final int COUNTDOWN_WARNING_RADIUS = 20; // blocks

	public MineScheduler(Plugin plugin, MineManager manager) {
		this.plugin = plugin;
		this.manager = manager;
		this.resetTasks = new MineResetTaskFactory(plugin, manager.templates().storage());
	}

	public void start() {
		if (resetTask != null || scheduleTask != null) return;
		resetTask = plugin.getServer().getScheduler().runTaskTimer(
			plugin, this::tickResetWork, 1L, 1L
		);
		scheduleTask = plugin.getServer().getScheduler().runTaskTimer(
			plugin, this::tickSchedules, 20L, 20L
		);
	}

	public void stop() {
		if (resetTask != null) {
			resetTask.cancel();
			resetTask = null;
		}
		if (scheduleTask != null) {
			scheduleTask.cancel();
			scheduleTask = null;
		}
		resetQueue.clear();
		resetTimes.clear();
	}

	public boolean forceReset(String name) {
		Mine mine = manager.get(name);
		if (mine == null || manager.templates().isCapturing(name)) return false;
		cancelReset(name);
		resetQueue.replace(resetTasks.create(mine));
		return true;
	}

	public void scheduleDepletionReset(String name) {
		// Don't schedule if already resetting or already scheduled
		if (resetQueue.contains(name) || resetTimes.containsKey(name)) return;
		// Store when this depletion reset was triggered for countdown
		resetTimes.put(name, System.currentTimeMillis() + 10000); // 10 seconds from now
	}

	public void cancelReset(String name) {
		resetQueue.cancel(name);
		resetTimes.remove(name);
	}

	private void broadcastCountdown(Mine mine, long seconds) {
		World world = Bukkit.getWorld(mine.getCuboid().worldId);
		if (world == null) return;

		String color = seconds <= 3 ? "<red><bold>" : seconds <= 6 ? "<yellow><bold>" : "<gold>";
		String message = color + "Resetting mine " + mine.getName() + " in " + seconds + " seconds";

		for (Player player : world.getPlayers()) {
			double distanceSquared = mine.getCuboid().distanceSquaredTo(player.getLocation());
			if (distanceSquared <= COUNTDOWN_WARNING_RADIUS * COUNTDOWN_WARNING_RADIUS) {
				ChatUtils.sendActionBar(player, message);
			}
		}
	}

	private void tickResetWork() {
		long started = System.nanoTime();
		MineResetTick tick = resetQueue.processTick(RESET_BUDGET_NANOS, manager::completeReset);
		lastResetTickNanos = System.nanoTime() - started;
		totalScannedBlocks += tick.scannedBlocks();
		totalChangedBlocks += tick.changedBlocks();
	}

	private void tickSchedules() {
		long now = System.currentTimeMillis();
		for (Mine mine : manager.getAll()) {
			if (!mine.isEnabled() || resetQueue.contains(mine.getName()) || manager.templates().isCapturing(mine.getName())) continue;
			long remainingMs = mine.getNextResetEpochMs() - now;
			long remainingSeconds = Math.max(0L, (remainingMs + 999L) / 1000L);
			if (remainingMs > 0L && remainingSeconds <= 10L) {
				broadcastCountdown(mine, remainingSeconds);
			}
			if (remainingMs <= 0L) {
				resetQueue.replace(resetTasks.create(mine));
			}
		}
		tickDepletionCountdown(now);
	}

	private void tickDepletionCountdown(long now) {
		// Check for depletion countdowns
		Iterator<Map.Entry<String, Long>> it = resetTimes.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Long> entry = it.next();
			String mineName = entry.getKey();
			long resetTime = entry.getValue();

			// Only show if not already resetting
			if (resetQueue.contains(mineName)) {
				it.remove();
				continue;
			}

			long remainingMs = resetTime - now;
			long remainingSeconds = Math.max(0L, (remainingMs + 999L) / 1000L);

			if (remainingSeconds <= 0) {
				// Time's up! Trigger the actual reset
				it.remove();
				Mine mine = manager.get(mineName);
				if (mine != null && !manager.templates().isCapturing(mine.getName())) {
					resetQueue.replace(resetTasks.create(mine));
				}
				continue;
			}

			if (remainingSeconds <= 10) {
				Mine mine = manager.get(mineName);
				if (mine != null) {
					broadcastCountdown(mine, remainingSeconds);
				}
			}
		}
	}

	public MineSchedulerStats stats() {
		return new MineSchedulerStats(
			resetQueue.size(),
			resetTimes.size(),
			totalScannedBlocks,
			totalChangedBlocks,
			lastResetTickNanos
		);
	}
}


