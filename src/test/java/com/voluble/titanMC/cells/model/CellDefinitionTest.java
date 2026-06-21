package com.voluble.titanMC.cells.model;

import com.voluble.titanMC.ranks.model.WardId;
import com.voluble.titanMC.util.RegionUtils;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CellDefinitionTest {
	@Test
	void cellOwnsExplicitWard() {
		CellDefinition cell = new CellDefinition(
			"e_cell_1",
			WardId.of("E"),
			new RegionUtils.Cuboid(UUID.randomUUID(), 0, 0, 0, 5, 5, 5),
			500,
			86400,
			604800,
			true
		);

		assertEquals(WardId.of("e"), cell.wardId());
	}

	@Test
	void cellRejectsMissingWard() {
		assertThrows(NullPointerException.class, () -> new CellDefinition(
			"cell",
			null,
			new RegionUtils.Cuboid(UUID.randomUUID(), 0, 0, 0, 1, 1, 1),
			0,
			60,
			60,
			true
		));
	}
}
