package com.voluble.titanMC.cells;

import com.voluble.titanMC.ranks.model.PrisonRank;
import com.voluble.titanMC.ranks.model.RankId;
import com.voluble.titanMC.ranks.model.WardDefinition;
import com.voluble.titanMC.ranks.model.WardId;
import com.voluble.titanMC.ranks.service.RankCatalog;
import com.voluble.titanMC.ranks.service.WardRankRequirements;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CellRentalEligibilityTest {
	private static final WardId E = WardId.of("e");
	private static final WardId D = WardId.of("d");
	private static final RankId E4 = RankId.of("e4");
	private static final RankId E3 = RankId.of("e3");
	private static final RankId E1 = RankId.of("e1");
	private static final RankId D4 = RankId.of("d4");
	private static final RankId D3 = RankId.of("d3");

	@Test
	void minimumRankAndLaterRanksMayRentInUnlockedWard() {
		WardRankRequirements eligibility = eligibility();

		assertFalse(eligibility.allows(E4, E));
		assertTrue(eligibility.allows(E3, E));
		assertTrue(eligibility.allows(E1, E));
		assertTrue(eligibility.allows(D4, E));
		assertFalse(eligibility.allows(D4, D));
		assertTrue(eligibility.allows(D3, D));
	}

	@Test
	void requirementMustBelongToItsWard() {
		assertThrows(IllegalArgumentException.class, () -> new WardRankRequirements(catalog(), Map.of(E, D3)));
	}

	@Test
	void everyWardMustHaveARequirement() {
		assertThrows(IllegalArgumentException.class, () -> new WardRankRequirements(catalog(), Map.of(E, E3)));
	}

	private static WardRankRequirements eligibility() {
		return new WardRankRequirements(catalog(), Map.of(E, E3, D, D3));
	}

	private static RankCatalog catalog() {
		List<PrisonRank> ranks = List.of(
			new PrisonRank(E4, E, "E4"), new PrisonRank(E3, E, "E3"), new PrisonRank(E1, E, "E1"),
			new PrisonRank(D4, D, "D4"), new PrisonRank(D3, D, "D3")
		);
		return new RankCatalog(
			List.of(
				new WardDefinition(E, "E Ward", List.of(E4, E3, E1)),
				new WardDefinition(D, "D Ward", List.of(D4, D3))
			),
			ranks
		);
	}
}
