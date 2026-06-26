package com.voluble.titanMC.milestones.model;

import org.bukkit.Material;

import java.util.List;
import java.util.Objects;

public record MilestoneTrack(
	String id,
	String categoryId,
	String name,
	Material icon,
	MilestoneMetric metric,
	String subject,
	List<MilestoneTier> tiers
) {
	public MilestoneTrack {
		id = requireId(id, "track id");
		categoryId = requireId(categoryId, "track category id");
		name = requireText(name, "track name");
		Objects.requireNonNull(icon, "icon");
		if (!icon.isItem()) throw new IllegalArgumentException("track icon must be an item");
		Objects.requireNonNull(metric, "metric");
		subject = subject == null ? "" : subject.trim().toLowerCase(java.util.Locale.ROOT);
		tiers = List.copyOf(Objects.requireNonNull(tiers, "tiers"));
		if (tiers.isEmpty()) throw new IllegalArgumentException("track must contain at least one tier");
		long previous = 0L;
		for (MilestoneTier tier : tiers) {
			Objects.requireNonNull(tier, "tiers must not contain null");
			if (tier.target() <= previous) throw new IllegalArgumentException("track tiers must have increasing targets");
			previous = tier.target();
		}
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
