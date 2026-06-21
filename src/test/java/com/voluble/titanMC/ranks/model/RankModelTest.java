package com.voluble.titanMC.ranks.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankModelTest {
	@Test
	void idsAreNormalizedForStableStorage() {
		assertEquals("e", WardId.of(" E ").value());
		assertEquals("e4", RankId.of(" E4 ").value());
	}

	@Test
	void idsRejectUnsafeValues() {
		assertThrows(IllegalArgumentException.class, () -> WardId.of("E Ward"));
		assertThrows(IllegalArgumentException.class, () -> RankId.of("4E"));
	}

	@Test
	void wardRequiresUniqueRanks() {
		RankId e4 = RankId.of("e4");
		assertThrows(IllegalArgumentException.class, () ->
			new WardDefinition(WardId.of("e"), "E Ward", List.of(e4, e4))
		);
	}

	@Test
	void definitionsTrimDisplayNames() {
		WardDefinition ward = new WardDefinition(WardId.of("e"), " E Ward ", List.of(RankId.of("e4")));
		PrisonRank rank = new PrisonRank(RankId.of("e4"), WardId.of("e"), " E4 ");

		assertEquals("E Ward", ward.displayName());
		assertEquals("E4", rank.displayName());
	}
}
