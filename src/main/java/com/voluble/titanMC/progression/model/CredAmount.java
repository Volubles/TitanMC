package com.voluble.titanMC.progression.model;

public record CredAmount(long value) {
	public CredAmount {
		if (value < 0) throw new IllegalArgumentException("cred amount must not be negative (was " + value + ")");
	}

	public static final CredAmount ZERO = new CredAmount(0L);

	public static CredAmount of(long value) {
		return value == 0L ? ZERO : new CredAmount(value);
	}

	public boolean isZero() {
		return value == 0L;
	}

	public CredAmount plus(CredAmount other) {
		return CredAmount.of(Math.addExact(value, other.value));
	}
}
