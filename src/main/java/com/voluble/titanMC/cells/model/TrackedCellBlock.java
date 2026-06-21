package com.voluble.titanMC.cells.model;

import java.util.Objects;
import java.util.UUID;

public record TrackedCellBlock(
	String cellId,
	long leaseGeneration,
	UUID worldId,
	int x,
	int y,
	int z
) {
	public TrackedCellBlock {
		Objects.requireNonNull(cellId, "cellId");
		if (leaseGeneration < 1L) throw new IllegalArgumentException("lease generation must be positive");
		Objects.requireNonNull(worldId, "worldId");
	}
}
