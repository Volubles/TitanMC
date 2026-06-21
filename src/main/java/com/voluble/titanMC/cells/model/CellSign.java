package com.voluble.titanMC.cells.model;

import java.util.Objects;
import java.util.UUID;

public record CellSign(String cellId, UUID worldId, int x, int y, int z) {
	public CellSign {
		Objects.requireNonNull(cellId, "cellId");
		Objects.requireNonNull(worldId, "worldId");
	}
}
