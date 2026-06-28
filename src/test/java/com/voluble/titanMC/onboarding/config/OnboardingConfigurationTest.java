package com.voluble.titanMC.onboarding.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OnboardingConfigurationTest {
	@Test
	void loadsCarouselStageWithoutRunwayPoints() {
		OnboardingConfiguration config = OnboardingConfiguration.load(yaml(carouselConfig()));

		assertEquals(6.0, config.previewStage().focus().x());
		assertEquals(17.0, config.previewStage().leftStage().x());
		assertEquals(20.0, config.previewStage().rightStage().x());
		assertEquals(2, config.presentation().steps().size());
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
		String source = """
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
			%s
			outfits:
			  - prison
			""".formatted(presentationConfig());

		assertThrows(IllegalArgumentException.class, () -> OnboardingConfiguration.load(yaml(source)));
	}

	@Test
	void loadsPreviewMode() {
		OnboardingConfiguration config = OnboardingConfiguration.load(yaml(carouselConfig()));

		assertEquals(OnboardingPreviewMode.CAROUSEL, config.previewMode());
	}

	@Test
	void synchronizesMissingPresentationAsDisabled() {
		YamlConfiguration yaml = yaml("""
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
			""");

		OnboardingYamlSynchronizer.sync(yaml);
		OnboardingConfiguration config = OnboardingConfiguration.load(yaml);

		assertFalse(config.presentation().enabled());
	}

	@Test
	void requiresPreviewMode() {
		String source = """
			enabled: true
			first-join:
			  enabled: true
			  delay-ticks: 40
			cinematic: onboarding_intro
			input:
			  repeat-cooldown-ms: 300
			%s
			preview:
			  carousel:
			    focus: { world: world, x: 6, y: 7, z: 8, yaw: 9, pitch: 10 }
			outfits:
			  - prison
			""".formatted(presentationConfig());

		assertThrows(IllegalArgumentException.class, () -> OnboardingConfiguration.load(yaml(source)));
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
			presentation:
			  enabled:
			outfits:
			""")));
	}

	@Test
	void rejectsNonNumericLocationValues() {
		String source = """
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
			%s
			outfits:
			  - prison
			""".formatted(presentationConfig());

		assertThrows(IllegalArgumentException.class, () -> OnboardingConfiguration.load(yaml(source)));
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
			%s
			outfits:
			  - prison
			""".formatted(presentationConfig());
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
			%s
			outfits:
			  - prison
			""".formatted(presentationConfig());
	}

	private static String presentationConfig() {
		return """
			presentation:
			  enabled: true
			  steps:
			    - title:
			        text: "Välkommen till"
			        style: "<color:#30bbf1>{{text}}</color>"
			      subtitle:
			        text: "Svea Prison"
			        style: "<color:#42d829><bold>{{text}}</bold></color>"
			      typewriter:
			        total-ticks: 40
			        sound:
			          enabled: false
			      hold-ticks: 20
			    - title:
			        text: "Välj din outfit"
			        style: "<color:#30bbf1>{{text}}</color>"
			      subtitle:
			        text: "Använd A och D"
			        style: "<gray>{{text}}</gray>"
			      typewriter:
			        total-ticks: 25
			        sound:
			          enabled: false
			      hold-ticks: 10
			  complete-sound:
			    enabled: false
			  preview-spawn-sound:
			    enabled: false
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
