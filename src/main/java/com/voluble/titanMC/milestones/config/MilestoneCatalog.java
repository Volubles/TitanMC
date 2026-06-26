package com.voluble.titanMC.milestones.config;

import com.voluble.titanMC.milestones.model.MilestoneCategory;
import com.voluble.titanMC.milestones.model.MilestoneMetric;
import com.voluble.titanMC.milestones.model.MilestoneProgressKey;
import com.voluble.titanMC.milestones.model.MilestoneTier;
import com.voluble.titanMC.milestones.model.MilestoneTrack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MilestoneCatalog {
	private final Map<String, MilestoneCategory> categories;
	private final Map<String, MilestoneTrack> tracks;
	private final Map<String, List<MilestoneTrack>> tracksByCategory;
	private final Map<MetricKey, List<MilestoneTrack>> tracksByMetric;
	private final Map<String, MilestoneTrack> tracksByTierId;

	public MilestoneCatalog(Map<String, MilestoneCategory> categories, Map<String, MilestoneTrack> tracks) {
		this.categories = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(categories, "categories")));
		this.tracks = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(tracks, "tracks")));
		Map<String, List<MilestoneTrack>> byCategory = new LinkedHashMap<>();
		Map<MetricKey, List<MilestoneTrack>> byMetric = new LinkedHashMap<>();
		Map<String, MilestoneTrack> byTier = new LinkedHashMap<>();
		for (MilestoneTrack track : tracks.values()) {
			byCategory.computeIfAbsent(track.categoryId(), ignored -> new ArrayList<>()).add(track);
			byMetric.computeIfAbsent(MetricKey.of(track.metric(), track.subject()), ignored -> new ArrayList<>()).add(track);
			for (MilestoneTier tier : track.tiers()) {
				MilestoneTrack duplicate = byTier.putIfAbsent(tier.id(), track);
				if (duplicate != null) throw new IllegalArgumentException("duplicate milestone tier id: " + tier.id());
			}
		}
		byCategory.replaceAll((ignored, value) -> List.copyOf(value));
		byMetric.replaceAll((ignored, value) -> List.copyOf(value));
		tracksByCategory = Collections.unmodifiableMap(new LinkedHashMap<>(byCategory));
		tracksByMetric = Collections.unmodifiableMap(new LinkedHashMap<>(byMetric));
		tracksByTierId = Collections.unmodifiableMap(new LinkedHashMap<>(byTier));
	}

	public List<MilestoneCategory> categories() {
		return categories.values().stream()
			.sorted(Comparator.comparingInt(category -> category.enabled() ? 0 : 1))
			.toList();
	}

	public Optional<MilestoneCategory> category(String id) {
		return Optional.ofNullable(categories.get(Objects.requireNonNull(id, "id").toLowerCase(java.util.Locale.ROOT)));
	}

	public List<MilestoneTrack> tracks(String categoryId) {
		return tracksByCategory.getOrDefault(Objects.requireNonNull(categoryId, "categoryId"), List.of());
	}

	public List<MilestoneTrack> tracks(MilestoneMetric metric, String subject) {
		return tracksByMetric.getOrDefault(MetricKey.of(metric, subject), List.of());
	}

	public List<MilestoneTrack> tracks(MilestoneProgressKey key) {
		return tracks(key.metric(), key.subject());
	}

	public Optional<MilestoneTrack> trackForTier(String tierId) {
		return Optional.ofNullable(tracksByTierId.get(Objects.requireNonNull(tierId, "tierId").toLowerCase(java.util.Locale.ROOT)));
	}

	private record MetricKey(MilestoneMetric metric, String subject) {
		private MetricKey {
			Objects.requireNonNull(metric, "metric");
			subject = subject == null ? "" : subject.trim().toLowerCase(java.util.Locale.ROOT);
		}

		private static MetricKey of(MilestoneMetric metric, String subject) {
			return new MetricKey(metric, subject);
		}
	}
}
