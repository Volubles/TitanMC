package com.voluble.titanMC.ranks.model;

import java.util.Objects;

public record PrisonRank(RankId id, WardId wardId, String displayName) {
	public PrisonRank {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(wardId, "wardId");
		displayName = requireDisplayName(displayName, "rank display name");
	}

	private static String requireDisplayName(String value, String name) {
		String normalized = Objects.requireNonNull(value, name).trim();
		if (normalized.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
		return normalized;
	}
}
