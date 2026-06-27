package com.voluble.titanMC.outfits.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record OutfitId(String value) {
	private static final Pattern VALID = Pattern.compile("[a-z0-9_-]{2,32}");

	public OutfitId {
		value = Objects.requireNonNull(value, "value").trim().toLowerCase(Locale.ROOT);
		if (!VALID.matcher(value).matches()) {
			throw new IllegalArgumentException("outfit id must match " + VALID.pattern());
		}
	}

	public static OutfitId of(String value) {
		return new OutfitId(value);
	}

	@Override
	public String toString() {
		return value;
	}
}
