package com.voluble.titanMC.milestones.model;

import java.util.Objects;

public record MilestoneProgress(MilestoneProgressKey key, long amount, long updatedAtEpochMillis) {
	public MilestoneProgress {
		Objects.requireNonNull(key, "key");
		if (amount < 0) throw new IllegalArgumentException("amount must not be negative");
		if (updatedAtEpochMillis < 0) throw new IllegalArgumentException("updatedAtEpochMillis must not be negative");
	}

	public MilestoneProgress withAmount(long amount, long updatedAtEpochMillis) {
		return new MilestoneProgress(key, amount, updatedAtEpochMillis);
	}
}
