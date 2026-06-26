package com.voluble.titanMC.milestones.model;

import java.util.Objects;

public record MilestoneTier(String id, String name, long target) {
	public MilestoneTier {
		id = requireId(id, "tier id");
		name = requireText(name, "tier name");
		if (target <= 0) throw new IllegalArgumentException("tier target must be positive");
	}

	private static String requireId(String value, String name) {
		String normalized = Objects.requireNonNull(value, name).trim().toLowerCase(java.util.Locale.ROOT);
		if (normalized.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
		return normalized;
	}

	private static String requireText(String value, String name) {
		String normalized = Objects.requireNonNull(value, name).trim();
		if (normalized.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
		return normalized;
	}
}
