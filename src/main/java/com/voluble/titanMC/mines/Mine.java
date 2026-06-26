package com.voluble.titanMC.mines;

import com.voluble.titanMC.util.RegionUtils;
import com.voluble.titanMC.mines.breaking.MineBreakProfile;
import org.bukkit.Location;

import java.util.Objects;

public final class Mine {

	private final String name;
	private RegionUtils.Cuboid cuboid;
	private int resetIntervalSeconds;
	private boolean enabled;
	private int batchSizePerTick;
	private WeightedPalette palette;
	private MineResetDefinition resetDefinition = new MineResetDefinition.Palette();
	private MineBreakProfile breakProfile = new MineBreakProfile.Unrestricted();
	private double credMultiplier = 1.0D;
	private long nextResetEpochMs;
	// Safe spawn location to teleport players to during reset. Null means no teleport
	private Location safeSpawn;
	// Depletion-based auto reset: trigger when remaining percent <= threshold. -1 disables
	private int autoResetBelowPercent = -1;
	private int brokenBlocks = 0;
	private int totalBlockCount;
	private int depletionTriggerBrokenBlocks = Integer.MAX_VALUE;

	public Mine(String name, RegionUtils.Cuboid cuboid, int resetIntervalSeconds, boolean enabled, int batchSizePerTick, WeightedPalette palette) {
		this.name = Objects.requireNonNull(name, "name");
		this.cuboid = Objects.requireNonNull(cuboid, "cuboid");
		this.resetIntervalSeconds = Math.max(1, resetIntervalSeconds);
		this.enabled = enabled;
		this.batchSizePerTick = Math.max(1, batchSizePerTick);
		this.palette = palette == null ? new WeightedPalette() : palette;
		this.nextResetEpochMs = System.currentTimeMillis() + (long) this.resetIntervalSeconds * 1000L;
		recalculateVolumeAndThreshold();
	}

	public String getName() { return name; }
	public RegionUtils.Cuboid getCuboid() { return cuboid; }
	public void setCuboid(RegionUtils.Cuboid cuboid) {
		this.cuboid = Objects.requireNonNull(cuboid);
		recalculateVolumeAndThreshold();
		brokenBlocks = Math.min(brokenBlocks, totalBlockCount);
	}
	public int getResetIntervalSeconds() { return resetIntervalSeconds; }
	public void setResetIntervalSeconds(int seconds) { this.resetIntervalSeconds = Math.max(1, seconds); }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public int getBatchSizePerTick() { return batchSizePerTick; }
	public void setBatchSizePerTick(int batchSizePerTick) { this.batchSizePerTick = Math.max(1, batchSizePerTick); }
	public WeightedPalette getPalette() { return palette; }
	public void setPalette(WeightedPalette palette) { this.palette = Objects.requireNonNullElseGet(palette, WeightedPalette::new); }
	public MineResetDefinition getResetDefinition() { return resetDefinition; }
	public void setResetDefinition(MineResetDefinition definition) {
		this.resetDefinition = Objects.requireNonNull(definition, "definition");
	}
	public MineBreakProfile getBreakProfile() { return breakProfile; }
	public void setBreakProfile(MineBreakProfile profile) {
		this.breakProfile = Objects.requireNonNull(profile, "profile");
	}
	public double getCredMultiplier() { return credMultiplier; }
	public void setCredMultiplier(double multiplier) {
		if (!Double.isFinite(multiplier)) throw new IllegalArgumentException("cred multiplier must be finite");
		this.credMultiplier = Math.max(0.0D, Math.min(10.0D, Math.round(multiplier * 100.0D) / 100.0D));
	}
	public long getNextResetEpochMs() { return nextResetEpochMs; }
	public void setNextResetEpochMs(long epochMs) { this.nextResetEpochMs = epochMs; }
	public void scheduleNextAfterInterval() { this.nextResetEpochMs = System.currentTimeMillis() + (long) resetIntervalSeconds * 1000L; }

	public Location getSafeSpawn() { return safeSpawn; }
	public void setSafeSpawn(Location location) { this.safeSpawn = location; }

	public int getAutoResetBelowPercent() { return autoResetBelowPercent; }
	public void setAutoResetBelowPercent(int percent) {
		this.autoResetBelowPercent = Math.max(-1, Math.min(100, percent));
		recalculateDepletionThreshold();
	}

	public void resetDepletionCounters() { this.brokenBlocks = 0; }
	public void incrementBroken(int amount) { if (amount > 0) this.brokenBlocks = Math.min(getTotalBlockCountSafe(), this.brokenBlocks + amount); }
	public void decrementBroken(int amount) { if (amount > 0) this.brokenBlocks = Math.max(0, this.brokenBlocks - amount); }
	public int getBrokenBlocks() { return brokenBlocks; }
	public void setBrokenBlocks(int amount) { this.brokenBlocks = Math.max(0, Math.min(getTotalBlockCountSafe(), amount)); }

	public int getTotalBlockCountSafe() {
		return totalBlockCount;
	}

	private int calculateTotalBlockCount() {
		long dx = (long) (cuboid.maxX - cuboid.minX + 1);
		long dy = (long) (cuboid.maxY - cuboid.minY + 1);
		long dz = (long) (cuboid.maxZ - cuboid.minZ + 1);
		long vol = dx * dy * dz;
		return vol > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) vol;
	}

	public int getRemainingPercent() {
		int total = getTotalBlockCountSafe();
		if (total <= 0) return 0;
		int remaining = Math.max(0, total - brokenBlocks);
		return (int) Math.round(remaining * 100.0 / total);
	}

	public boolean shouldAutoResetByDepletion() {
		return autoResetBelowPercent >= 0 && brokenBlocks >= depletionTriggerBrokenBlocks;
	}

	private void recalculateVolumeAndThreshold() {
		totalBlockCount = calculateTotalBlockCount();
		recalculateDepletionThreshold();
	}

	private void recalculateDepletionThreshold() {
		if (autoResetBelowPercent < 0) {
			depletionTriggerBrokenBlocks = Integer.MAX_VALUE;
			return;
		}
		int low = 0;
		int high = totalBlockCount;
		while (low < high) {
			int middle = low + (high - low) / 2;
			if (remainingPercentAt(middle) <= autoResetBelowPercent) high = middle;
			else low = middle + 1;
		}
		depletionTriggerBrokenBlocks = low;
	}

	private int remainingPercentAt(int broken) {
		if (totalBlockCount <= 0) return 0;
		int remaining = Math.max(0, totalBlockCount - broken);
		return (int) Math.round(remaining * 100.0 / totalBlockCount);
	}
}


