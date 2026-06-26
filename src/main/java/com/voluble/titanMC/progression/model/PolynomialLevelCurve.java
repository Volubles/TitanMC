package com.voluble.titanMC.progression.model;

public record PolynomialLevelCurve(double base, double exponent) implements LevelCurve {
	public PolynomialLevelCurve {
		if (!(base > 0.0)) throw new IllegalArgumentException("base must be positive (was " + base + ")");
		if (!(exponent > 0.0)) throw new IllegalArgumentException("exponent must be positive (was " + exponent + ")");
	}

	@Override
	public long credForLevel(int level) {
		if (level <= 1) return 0L;
		double raw = base * Math.pow(level - 1, exponent);
		return raw >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.floor(raw);
	}

	@Override
	public int levelAt(long totalCred) {
		if (totalCred <= 0L) return 1;
		double offset = Math.pow(totalCred / base, 1.0 / exponent);
		int candidate = Math.max(1, 1 + (int) Math.floor(offset));
		// Floor on a floating-point inverse can land one level low (3.9999...) or
		// one level high near boundaries. Correct by checking the actual cred cost
		// against the candidate and its neighbours.
		while (candidate > 1 && credForLevel(candidate) > totalCred) candidate--;
		while (credForLevel(candidate + 1) <= totalCred) candidate++;
		return candidate;
	}
}
