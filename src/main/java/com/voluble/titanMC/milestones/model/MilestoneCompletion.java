package com.voluble.titanMC.milestones.model;

import java.util.Objects;
import java.util.UUID;

public record MilestoneCompletion(UUID playerId, String tierId, long completedAtEpochMillis) {
	public MilestoneCompletion {
		Objects.requireNonNull(playerId, "playerId");
		tierId = Objects.requireNonNull(tierId, "tierId").trim().toLowerCase(java.util.Locale.ROOT);
		if (tierId.isBlank()) throw new IllegalArgumentException("tierId must not be blank");
		if (completedAtEpochMillis < 0) throw new IllegalArgumentException("completedAtEpochMillis must not be negative");
	}
}
