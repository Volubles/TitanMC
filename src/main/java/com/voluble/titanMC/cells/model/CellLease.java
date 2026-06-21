package com.voluble.titanMC.cells.model;

import java.util.Objects;
import java.util.UUID;

public record CellLease(
	String cellId,
	UUID ownerId,
	long generation,
	long startedAtEpochMillis,
	long expiresAtEpochMillis,
	boolean autoRenew
) {
	public CellLease {
		Objects.requireNonNull(cellId, "cellId");
		Objects.requireNonNull(ownerId, "ownerId");
		if (generation < 1L) throw new IllegalArgumentException("generation must be positive");
		if (expiresAtEpochMillis <= startedAtEpochMillis) throw new IllegalArgumentException("lease expiry must follow start");
	}
}
