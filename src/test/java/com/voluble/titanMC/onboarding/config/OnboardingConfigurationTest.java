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
			preview:
			  mode: carousel
			  carousel:
			    focus: { world: world, x: 6, y: 7, z: 8, yaw: 9, pitch: 10 }
			input:
			  repeat-cooldown-ms: 300
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
			preview:
			  carousel:
			    focus: { world: world, x: 6, y: 7, z: 8, yaw: 9, pitch: 10 }
			outfits:
			  - prison
			""")));
	}

	@Test
	void rejectsBlankTemplateValues() {
		assertThrows(IllegalArgumentException.class, () -> OnboardingConfiguration.load(yaml("""
			enabled:
			first-join:
			  enabled:
			  delay-ticks:
			cinematic:
			preview:
			  mode:
			input:
			  repeat-cooldown-ms:
			outfits:
			""")));
	}

	@Test
	void rejectsNonNumericLocationValues() {
		assertThrows(IllegalArgumentException.class, () -> OnboardingConfiguration.load(yaml("""
			enabled: true
			first-join:
			  enabled: true
			  delay-ticks: 40
			cinematic: onboarding_intro
			preview:
			  mode: runway
			  runway:
			    entrance: { world: world, x: soon, y: 2, z: 3, yaw: 4, pitch: 5 }
			    focus: { world: world, x: 6, y: 7, z: 8, yaw: 9, pitch: 10 }
			    exit: { world: world, x: 11, y: 12, z: 13, yaw: 14, pitch: 15 }
			input:
			  repeat-cooldown-ms: 300
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
			preview:
			  mode: carousel
			  carousel:
			    focus: { world: world, x: 6, y: 7, z: 8, yaw: 9, pitch: 10 }
			    left:
			      entrance: { world: world, x: 16, y: 2, z: 3, yaw: 4, pitch: 5 }
			      stage: { world: world, x: 17, y: 2, z: 3, yaw: 4, pitch: 5 }
			      exit: { world: world, x: 18, y: 2, z: 3, yaw: 4, pitch: 5 }
			    right:
			      entrance: { world: world, x: 19, y: 2, z: 3, yaw: 4, pitch: 5 }
			      stage: { world: world, x: 20, y: 2, z: 3, yaw: 4, pitch: 5 }
			      exit: { world: world, x: 21, y: 2, z: 3, yaw: 4, pitch: 5 }
			input:
			  repeat-cooldown-ms: 300
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
			preview:
			  mode: runway
			  runway:
			    entrance: { world: world, x: 1, y: 2, z: 3, yaw: 4, pitch: 5 }
			    focus: { world: world, x: 6, y: 7, z: 8, yaw: 9, pitch: 10 }
			    exit: { world: world, x: 11, y: 12, z: 13, yaw: 14, pitch: 15 }
			input:
			  repeat-cooldown-ms: 300
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
