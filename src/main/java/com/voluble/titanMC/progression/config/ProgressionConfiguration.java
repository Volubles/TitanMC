package com.voluble.titanMC.progression.config;

import com.voluble.titanMC.progression.model.CredAmount;
import com.voluble.titanMC.progression.model.CredSource;
import com.voluble.titanMC.progression.model.LevelCurve;
import com.voluble.titanMC.progression.model.PolynomialLevelCurve;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ProgressionConfiguration(
		LevelCurve curve,
		int maxLevel,
		Map<CredSource, ProgressionSourceConfig> sources,
		NotificationConfig notifications
) {
	public ProgressionConfiguration {
		Objects.requireNonNull(curve, "curve");
		if (maxLevel < 1) throw new IllegalArgumentException("max-level must be >= 1");
		sources = Map.copyOf(Objects.requireNonNull(sources, "sources"));
		Objects.requireNonNull(notifications, "notifications");
	}

	public static ProgressionConfiguration load(ConfigurationSection yaml) {
		Objects.requireNonNull(yaml, "yaml");
		LevelCurve curve = readCurve(requireSection(yaml, "level-curve"));
		int maxLevel = yaml.getInt("max-level", 100);
		if (maxLevel < 1) throw new IllegalArgumentException("max-level must be >= 1 (was " + maxLevel + ")");

		ConfigurationSection sourcesSection = requireSection(yaml, "sources");
		Map<CredSource, ProgressionSourceConfig> sources = new LinkedHashMap<>();
		for (String key : sourcesSection.getKeys(false)) {
			ConfigurationSection sourceSection = sourcesSection.getConfigurationSection(key);
			if (sourceSection == null) {
				throw new IllegalArgumentException("sources." + key + " must be a mapping");
			}
			CredSource id = CredSource.of(key);
			if (sources.containsKey(id)) {
				throw new IllegalArgumentException("duplicate source id: " + id.value());
			}
			sources.put(id, readSource(id, sourceSection));
		}
		if (sources.isEmpty()) throw new IllegalArgumentException("sources must contain at least one entry");

		NotificationConfig notifications = readNotifications(yaml.getConfigurationSection("notifications"));
		return new ProgressionConfiguration(curve, maxLevel, sources, notifications);
	}

	private static NotificationConfig readNotifications(ConfigurationSection section) {
		NotificationConfig defaults = NotificationConfig.defaults();
		if (section == null) return defaults;

		String playerMessage = section.getString("player-message", defaults.playerMessage());
		String broadcastMessage = section.getString("broadcast-message", defaults.broadcastMessage());
		int broadcastEvery = section.getInt("broadcast-every", defaults.broadcastEvery());
		if (broadcastEvery < 0) {
			throw new IllegalArgumentException("notifications.broadcast-every must be >= 0 (was " + broadcastEvery + ")");
		}
		Optional<String> playerSound = readOptionalString(section, "sound", defaults.playerSound());
		Optional<String> broadcastSound = readOptionalString(section, "broadcast-sound", defaults.broadcastSound());

		Map<Integer, String> overrides = new LinkedHashMap<>();
		ConfigurationSection overridesSection = section.getConfigurationSection("sound-overrides");
		if (overridesSection != null) {
			for (String key : overridesSection.getKeys(false)) {
				int level;
				try {
					level = Integer.parseInt(key);
				} catch (NumberFormatException nfe) {
					throw new IllegalArgumentException(
						"notifications.sound-overrides." + key + " must be a level number"
					);
				}
				if (level < 1) {
					throw new IllegalArgumentException(
						"notifications.sound-overrides." + key + " must be a positive level"
					);
				}
				String sound = overridesSection.getString(key);
				if (sound == null || sound.isBlank()) {
					throw new IllegalArgumentException(
						"notifications.sound-overrides." + key + " must be a non-blank sound id"
					);
				}
				overrides.put(level, sound);
			}
		}

		return new NotificationConfig(playerMessage, broadcastMessage, broadcastEvery,
			playerSound, broadcastSound, overrides);
	}

	private static Optional<String> readOptionalString(ConfigurationSection section, String key, Optional<String> fallback) {
		if (!section.isSet(key)) return fallback;
		String value = section.getString(key);
		if (value == null || value.isBlank()) return Optional.empty();
		return Optional.of(value);
	}

	private static LevelCurve readCurve(ConfigurationSection section) {
		String type = section.getString("type", "polynomial");
		if (!"polynomial".equalsIgnoreCase(type)) {
			throw new IllegalArgumentException("level-curve.type only supports 'polynomial' (was '" + type + "')");
		}
		if (!section.isSet("base")) throw new IllegalArgumentException("level-curve.base must be set");
		if (!section.isSet("exponent")) throw new IllegalArgumentException("level-curve.exponent must be set");
		return new PolynomialLevelCurve(section.getDouble("base"), section.getDouble("exponent"));
	}

	private static ProgressionSourceConfig readSource(CredSource id, ConfigurationSection section) {
		String displayName = section.getString("display-name");
		if (displayName == null || displayName.isBlank()) {
			throw new IllegalArgumentException("sources." + id.value() + ".display-name must be a non-blank string");
		}
		boolean enabled = section.getBoolean("enabled", true);
		Map<Material, CredAmount> blocks = new LinkedHashMap<>();
		ConfigurationSection blocksSection = section.getConfigurationSection("blocks");
		if (blocksSection != null) {
			for (String materialKey : blocksSection.getKeys(false)) {
				Material material = parseMaterial(materialKey, id);
				long value = blocksSection.getLong(materialKey);
				if (value < 0) {
					throw new IllegalArgumentException(
						"sources." + id.value() + ".blocks." + materialKey + " must not be negative"
					);
				}
				if (value == 0) continue;
				blocks.put(material, CredAmount.of(value));
			}
		}
		return new ProgressionSourceConfig(displayName, enabled, blocks);
	}

	private static Material parseMaterial(String key, CredSource source) {
		Material material = Material.matchMaterial(key.toUpperCase(Locale.ROOT));
		if (material == null) {
			throw new IllegalArgumentException(
				"sources." + source.value() + ".blocks." + key + " is not a known Material"
			);
		}
		return material;
	}

	private static ConfigurationSection requireSection(ConfigurationSection yaml, String key) {
		ConfigurationSection section = yaml.getConfigurationSection(key);
		if (section == null) throw new IllegalArgumentException(key + " must be a mapping");
		return section;
	}
}
