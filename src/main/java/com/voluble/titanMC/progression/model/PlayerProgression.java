package com.voluble.titanMC.progression.model;

import java.util.Objects;
import java.util.UUID;

public record PlayerProgression(UUID playerId, long totalCred, int level, long updatedAtEpochMillis) {
	public PlayerProgression {
		Objects.requireNonNull(playerId, "playerId");
		if (totalCred < 0) throw new IllegalArgumentException("totalCred must not be negative (was " + totalCred + ")");
		if (level < 1) throw new IllegalArgumentException("level must be >= 1 (was " + level + ")");
		if (updatedAtEpochMillis < 0) {
			throw new IllegalArgumentException("updatedAtEpochMillis must not be negative");
		}
	}

	public static PlayerProgression initial(UUID playerId, long nowEpochMillis) {
		return new PlayerProgression(playerId, 0L, 1, nowEpochMillis);
	}

	public PlayerProgression with(long totalCred, int level, long updatedAtEpochMillis) {
		return new PlayerProgression(playerId, totalCred, level, updatedAtEpochMillis);
	}
}
