package com.voluble.titanMC.progression.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolynomialLevelCurveTest {
	@Test
	void levelOneRequiresZeroCred() {
		LevelCurve curve = new PolynomialLevelCurve(100.0, 1.8);

		assertEquals(0L, curve.credForLevel(1));
		assertEquals(1, curve.levelAt(0L));
	}

	@Test
	void credForLevelIsMonotonicallyIncreasing() {
		LevelCurve curve = new PolynomialLevelCurve(100.0, 1.8);

		long previous = -1L;
		for (int level = 1; level <= 100; level++) {
			long needed = curve.credForLevel(level);
			assertTrue(needed > previous, "level " + level + " needed " + needed + " <= prev " + previous);
			previous = needed;
		}
	}

	@Test
	void levelAtAtThresholdAdvances() {
		LevelCurve curve = new PolynomialLevelCurve(100.0, 1.8);

		long threshold = curve.credForLevel(5);
		assertEquals(5, curve.levelAt(threshold));
		assertEquals(4, curve.levelAt(threshold - 1));
	}

	@Test
	void roundTripsFromCredForLevelToLevelAt() {
		LevelCurve curve = new PolynomialLevelCurve(150.0, 1.6);

		for (int level = 1; level <= 50; level++) {
			long needed = curve.credForLevel(level);
			assertEquals(level, curve.levelAt(needed),
				"level " + level + " requires " + needed + " but levelAt returned " + curve.levelAt(needed));
		}
	}

	@Test
	void linearExponentMatchesArithmetic() {
		LevelCurve curve = new PolynomialLevelCurve(100.0, 1.0);

		assertEquals(0L, curve.credForLevel(1));
		assertEquals(100L, curve.credForLevel(2));
		assertEquals(900L, curve.credForLevel(10));
	}

	@Test
	void rejectsBadParameters() {
		assertThrows(IllegalArgumentException.class, () -> new PolynomialLevelCurve(0.0, 1.8));
		assertThrows(IllegalArgumentException.class, () -> new PolynomialLevelCurve(-1.0, 1.8));
		assertThrows(IllegalArgumentException.class, () -> new PolynomialLevelCurve(100.0, 0.0));
	}
}
