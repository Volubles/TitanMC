package com.voluble.titanMC.cinematics.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record CinematicId(String value) {
	private static final Pattern VALID = Pattern.compile("[a-z0-9_-]{2,48}");

	public CinematicId {
		value = Objects.requireNonNull(value, "value").trim().toLowerCase(Locale.ROOT);
		if (!VALID.matcher(value).matches()) {
			throw new IllegalArgumentException("cinematic id must match " + VALID.pattern());
		}
	}

	public static CinematicId of(String value) {
		return new CinematicId(value);
	}

	@Override
	public String toString() {
		return value;
	}
}
