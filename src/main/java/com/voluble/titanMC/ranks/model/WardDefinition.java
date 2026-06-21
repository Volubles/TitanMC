package com.voluble.titanMC.ranks.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public record WardDefinition(WardId id, String displayName, List<RankId> ranks) {
	public WardDefinition {
		Objects.requireNonNull(id, "id");
		displayName = requireDisplayName(displayName);
		ranks = List.copyOf(Objects.requireNonNull(ranks, "ranks"));
		if (ranks.isEmpty()) throw new IllegalArgumentException("ward must contain at least one rank");
		if (ranks.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("ward ranks must not contain null");
		if (new LinkedHashSet<>(ranks).size() != ranks.size()) {
			throw new IllegalArgumentException("ward ranks must be unique");
		}
	}

	private static String requireDisplayName(String value) {
		String normalized = Objects.requireNonNull(value, "displayName").trim();
		if (normalized.isEmpty()) throw new IllegalArgumentException("ward display name must not be blank");
		return normalized;
	}
}
