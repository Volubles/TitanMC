package com.voluble.titanMC.milestones.model;

import org.bukkit.Material;

import java.util.Objects;

public record MilestoneCategory(String id, String name, Material icon, boolean enabled) {
	public MilestoneCategory {
		id = requireId(id, "category id");
		name = requireText(name, "category name");
		Objects.requireNonNull(icon, "icon");
		if (!icon.isItem()) throw new IllegalArgumentException("category icon must be an item");
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
