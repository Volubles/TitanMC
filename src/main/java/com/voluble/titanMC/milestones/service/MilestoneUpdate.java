package com.voluble.titanMC.milestones.service;

import com.voluble.titanMC.milestones.model.MilestoneCompletion;
import com.voluble.titanMC.milestones.model.MilestoneProgress;

import java.util.List;
import java.util.Objects;

public record MilestoneUpdate(MilestoneProgress previous, MilestoneProgress current, List<MilestoneCompletion> completions) {
	public MilestoneUpdate {
		Objects.requireNonNull(previous, "previous");
		Objects.requireNonNull(current, "current");
		completions = List.copyOf(Objects.requireNonNull(completions, "completions"));
	}

	public boolean completedAny() {
		return !completions.isEmpty();
	}
}
