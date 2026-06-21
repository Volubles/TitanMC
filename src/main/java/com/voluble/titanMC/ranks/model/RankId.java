package com.voluble.titanMC.ranks.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record RankId(String value) implements Comparable<RankId> {
	private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9_-]{0,31}");

	public RankId {
		value = Objects.requireNonNull(value, "rank id").trim().toLowerCase(Locale.ROOT);
		if (value.isEmpty()) throw new IllegalArgumentException("rank id must not be blank");
		if (!VALID.matcher(value).matches()) {
			throw new IllegalArgumentException("rank id must match " + VALID.pattern());
		}
	}

	public static RankId of(String value) {
		return new RankId(value);
	}

	@Override
	public int compareTo(RankId other) {
		return value.compareTo(other.value);
	}

	@Override
	public String toString() {
		return value;
	}
}
