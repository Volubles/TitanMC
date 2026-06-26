package com.voluble.titanMC.progression.bukkit;

import com.voluble.titanMC.progression.model.PlayerProgression;
import com.voluble.titanMC.progression.model.PolynomialLevelCurve;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressionBarViewTest {
	@Test
	void formatsProgressTowardsNextCredLevel() {
		ProgressionBarView view = ProgressionBarView.from(
			new PlayerProgression(UUID.randomUUID(), 150L, 2, 1L),
			new PolynomialLevelCurve(100.0D, 1.0D),
			100
		);

		assertEquals("Cred Level 2 -> 3 | 50% | 50 cred left", view.title());
		assertEquals(0.5D, view.progress());
	}

	@Test
	void formatsMaxLevel() {
		ProgressionBarView view = ProgressionBarView.from(
			new PlayerProgression(UUID.randomUUID(), 9_900L, 100, 1L),
			new PolynomialLevelCurve(100.0D, 1.0D),
			100
		);

		assertEquals("Cred Level 100 | 9,900 cred | Max level", view.title());
		assertEquals(1.0D, view.progress());
	}
}
