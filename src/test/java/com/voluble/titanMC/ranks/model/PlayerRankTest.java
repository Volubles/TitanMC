package com.voluble.titanMC.ranks.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerRankTest {
	@Test
	void recordCarriesAllFields() {
		UUID id = UUID.randomUUID();
		PlayerRank rank = new PlayerRank(id, RankId.of("e4"), 1_000L);

		assertEquals(id, rank.playerId());
		assertEquals(RankId.of("e4"), rank.rankId());
		assertEquals(1_000L, rank.assignedAtEpochMillis());
	}

	@Test
	void rejectsNegativeAssignedAt() {
		assertThrows(IllegalArgumentException.class, () -> new PlayerRank(UUID.randomUUID(), RankId.of("e4"), -1L));
	}

	@Test
	void rejectsNullFields() {
		assertThrows(NullPointerException.class, () -> new PlayerRank(null, RankId.of("e4"), 0L));
		assertThrows(NullPointerException.class, () -> new PlayerRank(UUID.randomUUID(), null, 0L));
	}

	@Test
	void withRankRetainsPlayerId() {
		UUID id = UUID.randomUUID();
		PlayerRank rank = new PlayerRank(id, RankId.of("e4"), 1_000L);
		PlayerRank updated = rank.withRank(RankId.of("e3"), 2_000L);

		assertEquals(id, updated.playerId());
		assertEquals(RankId.of("e3"), updated.rankId());
		assertEquals(2_000L, updated.assignedAtEpochMillis());
		assertNotEquals(rank, updated);
	}
}
