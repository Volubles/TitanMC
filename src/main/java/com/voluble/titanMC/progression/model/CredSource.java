package com.voluble.titanMC.progression.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record CredSource(String value) {
	private static final Pattern PATTERN = Pattern.compile("[a-z0-9][a-z0-9_:-]{0,63}");

	public CredSource {
		Objects.requireNonNull(value, "value");
	}

	public static CredSource of(String raw) {
		String normalized = Objects.requireNonNull(raw, "raw").trim().toLowerCase(Locale.ROOT);
		if (!PATTERN.matcher(normalized).matches()) {
			throw new IllegalArgumentException("Invalid cred source id: '" + raw + "'");
		}
		return new CredSource(normalized);
	}

	@Override
	public String toString() {
		return value;
	}
}
