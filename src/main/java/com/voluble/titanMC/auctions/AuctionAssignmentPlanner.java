package com.voluble.titanMC.auctions;

import com.voluble.titanMC.ranks.model.WardId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

public final class AuctionAssignmentPlanner {
	private AuctionAssignmentPlanner() {
	}

	public static List<Assignment> plan(
		Collection<AuctionPosition> positions,
		Collection<AuctionLot> auctions,
		RandomGenerator random
	) {
		Objects.requireNonNull(positions, "positions");
		Objects.requireNonNull(auctions, "auctions");
		Objects.requireNonNull(random, "random");
		Set<String> occupied = new HashSet<>();
		for (AuctionLot lot : auctions) {
			if (lot.positionId() != null) occupied.add(lot.positionId());
		}

		Map<WardId, List<AuctionPosition>> availableByWard = new HashMap<>();
		for (AuctionPosition position : positions) {
			if (!occupied.contains(position.id())) {
				availableByWard.computeIfAbsent(position.wardId(), ignored -> new ArrayList<>()).add(position);
			}
		}
		for (List<AuctionPosition> available : availableByWard.values()) shuffle(available, random);

		List<Assignment> assignments = new ArrayList<>();
		for (AuctionLot lot : auctions) {
			if (lot.state() != AuctionState.QUEUED || lot.positionId() != null) continue;
			List<AuctionPosition> available = availableByWard.get(lot.wardId());
			if (available == null || available.isEmpty()) continue;
			assignments.add(new Assignment(lot.id(), available.removeLast().id()));
		}
		return List.copyOf(assignments);
	}

	private static void shuffle(List<AuctionPosition> positions, RandomGenerator random) {
		for (int index = positions.size() - 1; index > 0; index--) {
			int other = random.nextInt(index + 1);
			AuctionPosition value = positions.get(index);
			positions.set(index, positions.get(other));
			positions.set(other, value);
		}
	}

	public record Assignment(long auctionId, String positionId) {
		public Assignment {
			if (auctionId < 1) throw new IllegalArgumentException("auctionId must be positive");
			Objects.requireNonNull(positionId, "positionId");
		}
	}
}
