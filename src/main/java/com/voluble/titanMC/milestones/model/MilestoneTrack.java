package com.voluble.titanMC.milestones.model;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record MilestoneTrack(
	String id,
	String categoryId,
	String name,
	Material icon,
	MilestoneMetric metric,
	String subject,
	boolean linear,
	int menuSlot,
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
		if (menuSlot < -1) throw new IllegalArgumentException("track menu slot must be -1 or greater");
		tiers = List.copyOf(Objects.requireNonNull(tiers, "tiers"));
		if (tiers.isEmpty()) throw new IllegalArgumentException("track must contain at least one tier");
		Map<MilestoneObjectiveKey, Long> previousTargets = new LinkedHashMap<>();
		for (MilestoneTier tier : tiers) {
			Objects.requireNonNull(tier, "tiers must not contain null");
			MilestoneObjectiveKey key = MilestoneObjectiveKey.of(tier);
			long previous = previousTargets.getOrDefault(key, 0L);
			if (tier.target() <= previous) {
				throw new IllegalArgumentException("track tiers must have increasing targets per objective");
			}
			previousTargets.put(key, tier.target());
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

	private record MilestoneObjectiveKey(MilestoneMetric metric, String subject) {
		private static MilestoneObjectiveKey of(MilestoneTier tier) {
			return new MilestoneObjectiveKey(tier.metric(), tier.subject());
		}
	}
}
