package com.voluble.titanMC.ranks.service;

import com.voluble.titanMC.ranks.model.PrisonRank;
import com.voluble.titanMC.ranks.model.RankId;
import com.voluble.titanMC.ranks.model.WardDefinition;
import com.voluble.titanMC.ranks.model.WardId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankCatalogTest {
	private static final WardId E = WardId.of("e");
	private static final WardId D = WardId.of("d");
	private static final RankId E4 = RankId.of("e4");
	private static final RankId E3 = RankId.of("e3");
	private static final RankId D4 = RankId.of("d4");

	@Test
	void wardOrderBuildsGlobalProgression() {
		RankCatalog catalog = catalog();

		assertEquals(List.of(E4, E3, D4), catalog.ranks().stream().map(PrisonRank::id).toList());
		assertEquals(E3, catalog.nextRank(E4).orElseThrow().id());
		assertEquals(D4, catalog.nextRank(E3).orElseThrow().id());
		assertTrue(catalog.nextRank(D4).isEmpty());
	}

	@Test
	void laterRanksMeetEarlierRequirements() {
		RankCatalog catalog = catalog();

		assertTrue(catalog.meetsRequirement(D4, E4));
		assertTrue(catalog.meetsRequirement(E3, E3));
		assertFalse(catalog.meetsRequirement(E4, E3));
	}

	@Test
	void rejectsRankAssignedToDifferentWard() {
		WardDefinition ward = new WardDefinition(E, "E Ward", List.of(E4));
		PrisonRank wrong = new PrisonRank(E4, D, "E4");

		assertThrows(IllegalArgumentException.class, () -> new RankCatalog(List.of(ward), List.of(wrong)));
	}

	@Test
	void rejectsRanksMissingFromWardProgression() {
		WardDefinition ward = new WardDefinition(E, "E Ward", List.of(E4));
		List<PrisonRank> ranks = List.of(
			new PrisonRank(E4, E, "E4"),
			new PrisonRank(E3, E, "E3")
		);

		assertThrows(IllegalArgumentException.class, () -> new RankCatalog(List.of(ward), ranks));
	}

	private static RankCatalog catalog() {
		return new RankCatalog(
			List.of(
				new WardDefinition(E, "E Ward", List.of(E4, E3)),
				new WardDefinition(D, "D Ward", List.of(D4))
			),
			List.of(
				new PrisonRank(E4, E, "E4"),
				new PrisonRank(E3, E, "E3"),
				new PrisonRank(D4, D, "D4")
			)
		);
	}
}
