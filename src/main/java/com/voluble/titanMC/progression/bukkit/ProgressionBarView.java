package com.voluble.titanMC.progression.bukkit;

import com.voluble.titanMC.progression.model.LevelCurve;
import com.voluble.titanMC.progression.model.PlayerProgression;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

public record ProgressionBarView(String title, double progress) {
	public ProgressionBarView {
		Objects.requireNonNull(title, "title");
		progress = Math.max(0.0D, Math.min(1.0D, progress));
	}

	public static ProgressionBarView from(PlayerProgression progression, LevelCurve curve, int maxLevel) {
		Objects.requireNonNull(progression, "progression");
		Objects.requireNonNull(curve, "curve");
		int level = progression.level();
		long current = progression.totalCred();
		NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.US);
		if (level >= maxLevel) {
			return new ProgressionBarView(
				"Cred Level " + level + " | " + numbers.format(current) + " cred | Max level",
				1.0D
			);
		}
		long currentLevelStart = curve.credForLevel(level);
		long nextLevelStart = curve.credForLevel(level + 1);
		long span = Math.max(1L, nextLevelStart - currentLevelStart);
		long inLevel = Math.max(0L, current - currentLevelStart);
		long remaining = Math.max(0L, nextLevelStart - current);
		double progress = Math.max(0.0D, Math.min(1.0D, (double) inLevel / (double) span));
		int percent = (int) Math.round(progress * 100.0D);
		String title = "Cred Level " + level + " -> " + (level + 1)
			+ " | " + percent + "%"
			+ " | " + numbers.format(remaining) + " cred left";
		return new ProgressionBarView(title, progress);
	}
}
