package com.voluble.titanMC.progression.bukkit;

import com.voluble.titanMC.progression.model.LevelCurve;
import com.voluble.titanMC.progression.model.PlayerProgression;
import net.kyori.adventure.bossbar.BossBar;

import java.util.Objects;

public record ProgressionBarView(String title, double progress, BossBar.Color color) {
	public ProgressionBarView {
		Objects.requireNonNull(title, "title");
		progress = Math.max(0.0D, Math.min(1.0D, progress));
		Objects.requireNonNull(color, "color");
	}

	public static ProgressionBarView from(PlayerProgression progression, LevelCurve curve, int maxLevel) {
		Objects.requireNonNull(progression, "progression");
		Objects.requireNonNull(curve, "curve");
		int level = progression.level();
		long current = progression.totalCred();
		if (level >= maxLevel) {
			return new ProgressionBarView("Cred Level " + level + " | Max", 1.0D, BossBar.Color.PURPLE);
		}
		long currentLevelStart = curve.credForLevel(level);
		long nextLevelStart = curve.credForLevel(level + 1);
		long span = Math.max(1L, nextLevelStart - currentLevelStart);
		long inLevel = Math.max(0L, current - currentLevelStart);
		double progress = Math.max(0.0D, Math.min(1.0D, (double) inLevel / (double) span));
		int percent = (int) Math.round(progress * 100.0D);
		return new ProgressionBarView(
			"Cred Level " + level + " | " + percent + "%",
			progress,
			colorFor(progress)
		);
	}

	private static BossBar.Color colorFor(double progress) {
		if (progress >= 0.95D) return BossBar.Color.GREEN;
		if (progress >= 0.75D) return BossBar.Color.YELLOW;
		return BossBar.Color.BLUE;
	}
}
