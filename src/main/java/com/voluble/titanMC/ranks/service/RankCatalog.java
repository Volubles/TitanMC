package com.voluble.titanMC.ranks.service;

import com.voluble.titanMC.ranks.model.PrisonRank;
import com.voluble.titanMC.ranks.model.RankId;
import com.voluble.titanMC.ranks.model.WardDefinition;
import com.voluble.titanMC.ranks.model.WardId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RankCatalog {
	private final List<WardDefinition> wards;
	private final List<PrisonRank> progression;
	private final Map<WardId, WardDefinition> wardsById;
	private final Map<RankId, PrisonRank> ranksById;
	private final Map<RankId, Integer> progressionIndexes;

	public RankCatalog(List<WardDefinition> wards, List<PrisonRank> ranks) {
		this.wards = List.copyOf(Objects.requireNonNull(wards, "wards"));
		List<PrisonRank> rankDefinitions = List.copyOf(Objects.requireNonNull(ranks, "ranks"));
		if (this.wards.isEmpty()) throw new IllegalArgumentException("rank catalog must contain at least one ward");

		wardsById = indexWards(this.wards);
		ranksById = indexRanks(rankDefinitions);
		progression = buildProgression(this.wards, wardsById, ranksById);
		if (progression.size() != ranksById.size()) {
			throw new IllegalArgumentException("every rank must appear in exactly one ward progression");
		}
		progressionIndexes = new LinkedHashMap<>();
		for (int index = 0; index < progression.size(); index++) {
			progressionIndexes.put(progression.get(index).id(), index);
		}
	}

	public List<WardDefinition> wards() {
		return wards;
	}

	public List<PrisonRank> ranks() {
		return progression;
	}

	public Optional<WardDefinition> findWard(WardId id) {
		return Optional.ofNullable(wardsById.get(Objects.requireNonNull(id, "id")));
	}

	public Optional<PrisonRank> findRank(RankId id) {
		return Optional.ofNullable(ranksById.get(Objects.requireNonNull(id, "id")));
	}

	public WardDefinition requireWard(WardId id) {
		return findWard(id).orElseThrow(() -> new IllegalArgumentException("Unknown ward: " + id));
	}

	public PrisonRank requireRank(RankId id) {
		return findRank(id).orElseThrow(() -> new IllegalArgumentException("Unknown rank: " + id));
	}

	public int progressionIndex(RankId id) {
		Integer index = progressionIndexes.get(Objects.requireNonNull(id, "id"));
		if (index == null) throw new IllegalArgumentException("Unknown rank: " + id);
		return index;
	}

	public boolean meetsRequirement(RankId current, RankId required) {
		return progressionIndex(current) >= progressionIndex(required);
	}

	public Optional<PrisonRank> nextRank(RankId current) {
		int nextIndex = progressionIndex(current) + 1;
		return nextIndex < progression.size() ? Optional.of(progression.get(nextIndex)) : Optional.empty();
	}

	private static Map<WardId, WardDefinition> indexWards(List<WardDefinition> wards) {
		Map<WardId, WardDefinition> indexed = new LinkedHashMap<>();
		for (WardDefinition ward : wards) {
			Objects.requireNonNull(ward, "wards must not contain null");
			if (indexed.putIfAbsent(ward.id(), ward) != null) {
				throw new IllegalArgumentException("duplicate ward: " + ward.id());
			}
		}
		return Map.copyOf(indexed);
	}

	private static Map<RankId, PrisonRank> indexRanks(List<PrisonRank> ranks) {
		Map<RankId, PrisonRank> indexed = new LinkedHashMap<>();
		for (PrisonRank rank : ranks) {
			Objects.requireNonNull(rank, "ranks must not contain null");
			if (indexed.putIfAbsent(rank.id(), rank) != null) {
				throw new IllegalArgumentException("duplicate rank: " + rank.id());
			}
		}
		return Map.copyOf(indexed);
	}

	private static List<PrisonRank> buildProgression(
		List<WardDefinition> wards,
		Map<WardId, WardDefinition> wardsById,
		Map<RankId, PrisonRank> ranksById
	) {
		List<PrisonRank> progression = new ArrayList<>();
		for (WardDefinition ward : wards) {
			for (RankId rankId : ward.ranks()) {
				PrisonRank rank = ranksById.get(rankId);
				if (rank == null) throw new IllegalArgumentException("ward references unknown rank: " + rankId);
				if (!rank.wardId().equals(ward.id())) {
					throw new IllegalArgumentException("rank " + rankId + " belongs to " + rank.wardId() + ", not " + ward.id());
				}
				if (!wardsById.containsKey(rank.wardId())) {
					throw new IllegalArgumentException("rank references unknown ward: " + rank.wardId());
				}
				progression.add(rank);
			}
		}
		return List.copyOf(progression);
	}
}
