package com.voluble.titanMC.progression.bukkit;

import com.voluble.titanMC.progression.model.PlayerProgression;
import com.voluble.titanMC.progression.model.PolynomialLevelCurve;
import net.kyori.adventure.bossbar.BossBar;
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

		assertEquals("Cred Level 2 | 50%", view.title());
		assertEquals(0.5D, view.progress());
		assertEquals(BossBar.Color.BLUE, view.color());
	}

	@Test
	void formatsMaxLevel() {
		ProgressionBarView view = ProgressionBarView.from(
			new PlayerProgression(UUID.randomUUID(), 9_900L, 100, 1L),
			new PolynomialLevelCurve(100.0D, 1.0D),
			100
		);

		assertEquals("Cred Level 100 | Max", view.title());
		assertEquals(1.0D, view.progress());
		assertEquals(BossBar.Color.PURPLE, view.color());
	}

	@Test
	void usesWarmerColorsNearNextLevel() {
		PolynomialLevelCurve curve = new PolynomialLevelCurve(100.0D, 1.0D);

		assertEquals(BossBar.Color.YELLOW, ProgressionBarView.from(
			new PlayerProgression(UUID.randomUUID(), 175L, 2, 1L), curve, 100
		).color());
		assertEquals(BossBar.Color.GREEN, ProgressionBarView.from(
			new PlayerProgression(UUID.randomUUID(), 195L, 2, 1L), curve, 100
		).color());
	}
}
