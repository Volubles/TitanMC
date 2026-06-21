package com.voluble.titanMC.cells.model;

import com.voluble.titanMC.util.RegionUtils;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record CellDefinition(
	String id,
	RegionUtils.Cuboid cuboid,
	long rentPrice,
	long rentDurationSeconds,
	boolean enabled
) {
	private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");

	public CellDefinition {
		id = Objects.requireNonNull(id, "id").toLowerCase(Locale.ROOT);
		if (!ID.matcher(id).matches()) throw new IllegalArgumentException("Cell id must match " + ID.pattern());
		Objects.requireNonNull(cuboid, "cuboid");
		if (rentPrice < 0L) throw new IllegalArgumentException("rent price must not be negative");
		if (rentDurationSeconds < 60L) throw new IllegalArgumentException("rent duration must be at least 60 seconds");
	}
}
