package com.voluble.titanMC.progression.service;

import com.voluble.titanMC.progression.model.PlayerProgression;

import java.util.Objects;

public record ProgressionUpdate(PlayerProgression previous, PlayerProgression current, long applied) {
	public ProgressionUpdate {
		Objects.requireNonNull(previous, "previous");
		Objects.requireNonNull(current, "current");
		if (!previous.playerId().equals(current.playerId())) {
			throw new IllegalArgumentException("previous/current player id mismatch");
		}
	}

	public boolean changedLevel() {
		return previous.level() != current.level();
	}

	public boolean leveledUp() {
		return current.level() > previous.level();
	}
}
