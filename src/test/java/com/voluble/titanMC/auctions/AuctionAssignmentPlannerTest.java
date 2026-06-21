package com.voluble.titanMC.auctions;

import com.voluble.titanMC.ranks.model.WardId;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionAssignmentPlannerTest {
	private static final WardId E = WardId.of("e");
	private static final WardId D = WardId.of("d");

	@Test
	void assignsEachAuctionWithinItsWard() {
		List<AuctionPosition> positions = List.of(position("e-001", E), position("d-001", D));
		List<AuctionLot> lots = List.of(queued(1, E), queued(2, D));

		List<AuctionAssignmentPlanner.Assignment> assignments = AuctionAssignmentPlanner.plan(
			positions, lots, new Random(1)
		);

		assertEquals("e-001", assignment(assignments, 1).positionId());
		assertEquals("d-001", assignment(assignments, 2).positionId());
	}

	@Test
	void leavesAuctionQueuedWhenItsWardIsFull() {
		AuctionLot occupied = queued(1, E).atPosition("e-001", 1000);
		AuctionLot waiting = queued(2, E);
		AuctionLot otherWard = queued(3, D);

		List<AuctionAssignmentPlanner.Assignment> assignments = AuctionAssignmentPlanner.plan(
			List.of(position("e-001", E), position("d-001", D)),
			List.of(occupied, waiting, otherWard),
			new Random(1)
		);

		assertTrue(assignments.stream().noneMatch(assignment -> assignment.auctionId() == waiting.id()));
		assertEquals("d-001", assignment(assignments, otherWard.id()).positionId());
	}

	@Test
	void neverAssignsOnePositionTwice() {
		List<AuctionAssignmentPlanner.Assignment> assignments = AuctionAssignmentPlanner.plan(
			List.of(position("e-001", E)),
			List.of(queued(1, E), queued(2, E)),
			new Random(1)
		);

		assertEquals(1, assignments.size());
	}

	private static AuctionAssignmentPlanner.Assignment assignment(
		List<AuctionAssignmentPlanner.Assignment> assignments,
		long auctionId
	) {
		return assignments.stream()
			.filter(assignment -> assignment.auctionId() == auctionId)
			.findFirst()
			.orElseThrow();
	}

	private static AuctionPosition position(String id, WardId wardId) {
		return new AuctionPosition(id, wardId, UUID.randomUUID(), 0, 64, 0, BlockFace.NORTH);
	}

	private static AuctionLot queued(long id, WardId wardId) {
		return new AuctionLot(
			id, id, 0, wardId, null, 500, AuctionState.QUEUED,
			null, null, 0, 0, List.of(new byte[]{1})
		);
	}
}
