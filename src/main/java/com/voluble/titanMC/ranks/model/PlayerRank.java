package com.voluble.titanMC.ranks.model;

import java.util.Objects;
import java.util.UUID;

public record PlayerRank(UUID playerId, RankId rankId, long assignedAtEpochMillis) {
	public PlayerRank {
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(rankId, "rankId");
		if (assignedAtEpochMillis < 0) {
			throw new IllegalArgumentException("assignedAtEpochMillis must not be negative");
		}
	}

	public PlayerRank withRank(RankId rankId, long assignedAtEpochMillis) {
		return new PlayerRank(playerId, rankId, assignedAtEpochMillis);
	}
}
