package com.voluble.titanMC.milestones.model;

import java.util.Objects;
import java.util.UUID;

public record MilestoneProgressKey(UUID playerId, MilestoneMetric metric, String subject) {
	public MilestoneProgressKey {
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(metric, "metric");
		subject = subject == null ? "" : subject.trim().toLowerCase(java.util.Locale.ROOT);
	}
}
