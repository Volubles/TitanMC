package com.voluble.titanMC.progression.config;

import com.voluble.titanMC.progression.model.CredSource;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionConfigurationTest {
	@Test
	void loadsMinimalConfiguration() {
		YamlConfiguration yaml = yaml("""
			level-curve:
			  type: polynomial
			  base: 100.0
			  exponent: 1.5
			max-level: 50
			sources:
			  mining:
			    display-name: Mining
			    enabled: true
			    blocks:
			      stone: 1
			""");

		ProgressionConfiguration config = ProgressionConfiguration.load(yaml);

		assertEquals(50, config.maxLevel());
		assertEquals(0L, config.curve().credForLevel(1));
		assertEquals(100L, config.curve().credForLevel(2));
		ProgressionSourceConfig mining = config.sources().get(CredSource.of("mining"));
		assertNotNull(mining);
		assertEquals("Mining", mining.displayName());
		assertTrue(mining.enabled());
		assertEquals(1, mining.blockValues().size());
		assertEquals(1L, mining.blockValues().get(Material.STONE).value());
	}

	@Test
	void disabledSourceIsCarriedThrough() {
		YamlConfiguration yaml = yaml("""
			level-curve:
			  type: polynomial
			  base: 100.0
			  exponent: 1.5
			max-level: 100
			sources:
			  mining:
			    display-name: Mining
			    enabled: false
			    blocks:
			      stone: 1
			""");

		ProgressionConfiguration config = ProgressionConfiguration.load(yaml);
		assertTrue(!config.sources().get(CredSource.of("mining")).enabled());
	}

	@Test
	void zeroValueBlocksAreIgnored() {
		YamlConfiguration yaml = yaml("""
			level-curve:
			  type: polynomial
			  base: 100.0
			  exponent: 1.5
			max-level: 100
			sources:
			  mining:
			    display-name: Mining
			    blocks:
			      stone: 1
			      dirt: 0
			""");

		ProgressionConfiguration config = ProgressionConfiguration.load(yaml);
		var blocks = config.sources().get(CredSource.of("mining")).blockValues();
		assertEquals(1, blocks.size());
		assertEquals(1L, blocks.get(Material.STONE).value());
	}

	@Test
	void rejectsNegativeBlockValue() {
		assertThrows(IllegalArgumentException.class, () -> ProgressionConfiguration.load(yaml("""
			level-curve: { type: polynomial, base: 100.0, exponent: 1.5 }
			max-level: 100
			sources:
			  mining:
			    display-name: Mining
			    blocks:
			      stone: -1
			""")));
	}

	@Test
	void rejectsUnknownMaterial() {
		assertThrows(IllegalArgumentException.class, () -> ProgressionConfiguration.load(yaml("""
			level-curve: { type: polynomial, base: 100.0, exponent: 1.5 }
			max-level: 100
			sources:
			  mining:
			    display-name: Mining
			    blocks:
			      moon_dust: 1
			""")));
	}

	@Test
	void rejectsUnsupportedCurveType() {
		assertThrows(IllegalArgumentException.class, () -> ProgressionConfiguration.load(yaml("""
			level-curve: { type: exponential, base: 100.0, exponent: 1.5 }
			max-level: 100
			sources:
			  mining:
			    display-name: Mining
			""")));
	}

	@Test
	void rejectsEmptySources() {
		assertThrows(IllegalArgumentException.class, () -> ProgressionConfiguration.load(yaml("""
			level-curve: { type: polynomial, base: 100.0, exponent: 1.5 }
			max-level: 100
			sources: {}
			""")));
	}

	@Test
	void notificationsDefaultWhenMissing() {
		YamlConfiguration yaml = yaml("""
			level-curve: { type: polynomial, base: 100.0, exponent: 1.5 }
			max-level: 100
			sources:
			  mining:
			    display-name: Mining
			    blocks:
			      stone: 1
			""");

		ProgressionConfiguration config = ProgressionConfiguration.load(yaml);
		NotificationConfig notifications = config.notifications();
		assertEquals(5, notifications.broadcastEvery());
		assertTrue(notifications.broadcastsEnabled());
		assertTrue(notifications.playerSound().isPresent());
	}

	@Test
	void notificationsRespectOverridesAndDisabling() {
		YamlConfiguration yaml = yaml("""
			level-curve: { type: polynomial, base: 100.0, exponent: 1.5 }
			max-level: 100
			notifications:
			  player-message: "<aqua>level {level}</aqua>"
			  broadcast-message: "<red>{player} hit {level}</red>"
			  broadcast-every: 0
			  sound: "entity.experience_orb.pickup"
			  broadcast-sound: ""
			  sound-overrides:
			    10: "block.beacon.activate"
			sources:
			  mining:
			    display-name: Mining
			    blocks:
			      stone: 1
			""");

		ProgressionConfiguration config = ProgressionConfiguration.load(yaml);
		NotificationConfig notifications = config.notifications();
		assertTrue(!notifications.broadcastsEnabled());
		assertTrue(notifications.broadcastSound().isEmpty());
		assertEquals("block.beacon.activate", notifications.soundForLevel(10).orElseThrow());
		assertEquals("entity.experience_orb.pickup", notifications.soundForLevel(11).orElseThrow());
	}

	@Test
	void rejectsBadSoundOverrideKey() {
		assertThrows(IllegalArgumentException.class, () -> ProgressionConfiguration.load(yaml("""
			level-curve: { type: polynomial, base: 100.0, exponent: 1.5 }
			max-level: 100
			notifications:
			  sound-overrides:
			    abc: "block.beacon.activate"
			sources:
			  mining:
			    display-name: Mining
			    blocks:
			      stone: 1
			""")));
	}

	@Test
	void bundledConfigurationLoads() throws Exception {
		try (var source = getClass().getClassLoader().getResourceAsStream("progression/progression.yml")) {
			assertNotNull(source);
			YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
				new InputStreamReader(source, StandardCharsets.UTF_8)
			);
			ProgressionConfiguration config = ProgressionConfiguration.load(yaml);

			assertEquals(100, config.maxLevel());
			assertTrue(config.sources().containsKey(CredSource.of("mining")));
			assertTrue(config.sources().containsKey(CredSource.of("woodcutting")));
			assertTrue(config.sources().get(CredSource.of("mining"))
				.blockValues().get(Material.DIAMOND_ORE).value() > 0);
			assertEquals(5, config.notifications().broadcastEvery());
			assertTrue(config.notifications().soundForLevel(100).isPresent());
		}
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
