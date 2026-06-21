package com.voluble.titanMC.ranks.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record WardId(String value) implements Comparable<WardId> {
	private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9_-]{0,15}");

	public WardId {
		value = normalize(value, "ward id");
		if (!VALID.matcher(value).matches()) {
			throw new IllegalArgumentException("ward id must match " + VALID.pattern());
		}
	}

	public static WardId of(String value) {
		return new WardId(value);
	}

	@Override
	public int compareTo(WardId other) {
		return value.compareTo(other.value);
	}

	@Override
	public String toString() {
		return value;
	}

	private static String normalize(String value, String name) {
		String normalized = Objects.requireNonNull(value, name).trim().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
		return normalized;
	}
}
