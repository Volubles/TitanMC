package com.voluble.titanMC.onboarding.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OnboardingConfigurationTest {
	@Test
	void loadsCarouselStageWithoutRunwayPoints() {
		OnboardingConfiguration config = OnboardingConfiguration.load(yaml(carouselConfig()));

		assertEquals(6.0, config.previewStage().focus().x());
		assertEquals(17.0, config.previewStage().leftStage().x());
		assertEquals(20.0, config.previewStage().rightStage().x());
	}

	@Test
	void loadsRunwayStageWithoutCarouselPoints() {
		OnboardingConfiguration config = OnboardingConfiguration.load(yaml(runwayConfig()));

		assertEquals(1.0, config.previewStage().runwayEntrance().x());
		assertEquals(6.0, config.previewStage().focus().x());
		assertEquals(11.0, config.previewStage().runwayExit().x());
	}

	@Test
	void carouselRequiresCompleteCarouselStage() {
		assertThrows(IllegalArgumentException.class, () -> OnboardingConfiguration.load(yaml("""
			enabled: true
			first-join:
			  enabled: true
			  delay-ticks: 40
			cinematic: onboarding_intro
			preview-mode: carousel
			input:
			  repeat-cooldown-ms: 300
			preview-stage:
			  focus: { world: world, x: 6, y: 7, z: 8, yaw: 9, pitch: 10 }
			outfits:
			  - prison
			""")));
	}

	@Test
	void loadsPreviewMode() {
		OnboardingConfiguration config = OnboardingConfiguration.load(yaml(carouselConfig()));

		assertEquals(OnboardingPreviewMode.CAROUSEL, config.previewMode());
	}

	@Test
	void requiresPreviewMode() {
		assertThrows(IllegalArgumentException.class, () -> OnboardingConfiguration.load(yaml("""
			enabled: true
			first-join:
			  enabled: true
			  delay-ticks: 40
			cinematic: onboarding_intro
			input:
			  repeat-cooldown-ms: 300
			preview-stage:
			  focus: { world: world, x: 6, y: 7, z: 8, yaw: 9, pitch: 10 }
			outfits:
			  - prison
			""")));
	}

	private static String carouselConfig() {
		return """
			enabled: true
			first-join:
			  enabled: true
			  delay-ticks: 40
			cinematic: onboarding_intro
			preview-mode: carousel
			input:
			  repeat-cooldown-ms: 300
			preview-stage:
			  focus: { world: world, x: 6, y: 7, z: 8, yaw: 9, pitch: 10 }
			  left-entrance: { world: world, x: 16, y: 2, z: 3, yaw: 4, pitch: 5 }
			  left-stage: { world: world, x: 17, y: 2, z: 3, yaw: 4, pitch: 5 }
			  left-exit: { world: world, x: 18, y: 2, z: 3, yaw: 4, pitch: 5 }
			  right-entrance: { world: world, x: 19, y: 2, z: 3, yaw: 4, pitch: 5 }
			  right-stage: { world: world, x: 20, y: 2, z: 3, yaw: 4, pitch: 5 }
			  right-exit: { world: world, x: 21, y: 2, z: 3, yaw: 4, pitch: 5 }
			outfits:
			  - prison
			""";
	}

	private static String runwayConfig() {
		return """
			enabled: true
			first-join:
			  enabled: true
			  delay-ticks: 40
			cinematic: onboarding_intro
			preview-mode: runway
			input:
			  repeat-cooldown-ms: 300
			preview-stage:
			  runway-entrance: { world: world, x: 1, y: 2, z: 3, yaw: 4, pitch: 5 }
			  focus: { world: world, x: 6, y: 7, z: 8, yaw: 9, pitch: 10 }
			  runway-exit: { world: world, x: 11, y: 12, z: 13, yaw: 14, pitch: 15 }
			outfits:
			  - prison
			""";
	}

	private static YamlConfiguration yaml(String source) {
		YamlConfiguration yaml = new YamlConfiguration();
		try {
			yaml.loadFromString(source);
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
		return yaml;
	}
}
