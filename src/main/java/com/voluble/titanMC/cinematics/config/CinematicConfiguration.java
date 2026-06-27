package com.voluble.titanMC.cinematics.config;

import com.voluble.titanMC.cinematics.model.CameraPathDefinition;
import com.voluble.titanMC.cinematics.model.CameraPoint;
import com.voluble.titanMC.cinematics.model.CinematicDefinition;
import com.voluble.titanMC.cinematics.model.CinematicId;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record CinematicConfiguration(
	boolean enabled,
	int defaultPointDurationTicks,
	boolean defaultRestorePlayer,
	Map<CinematicId, CinematicDefinition> cinematics
) {
	public CinematicConfiguration {
		if (defaultPointDurationTicks <= 0) throw new IllegalArgumentException("default point duration must be positive");
		cinematics = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(cinematics, "cinematics")));
	}

	public static CinematicConfiguration load(FileConfiguration yaml) {
		boolean enabled = yaml.getBoolean("enabled", true);
		ConfigurationSection defaults = yaml.getConfigurationSection("defaults");
		int pointDuration = defaults == null ? 40 : defaults.getInt("point-duration-ticks", 40);
		boolean restorePlayer = defaults == null || defaults.getBoolean("restore-player", true);
		Map<CinematicId, CinematicDefinition> definitions = new LinkedHashMap<>();
		ConfigurationSection section = yaml.getConfigurationSection("cinematics");
		if (section != null) {
			for (String key : section.getKeys(false)) {
				CinematicId id = CinematicId.of(key);
				definitions.put(id, cinematic(id, requireSection(section, key), restorePlayer));
			}
		}
		return new CinematicConfiguration(enabled, pointDuration, restorePlayer, definitions);
	}

	public Optional<CinematicDefinition> find(CinematicId id) {
		return Optional.ofNullable(cinematics.get(Objects.requireNonNull(id, "id")));
	}

	private static CinematicDefinition cinematic(CinematicId id, ConfigurationSection section, boolean defaultRestorePlayer) {
		int duration = section.getInt("duration-ticks", 120);
		ConfigurationSection camera = requireSection(section, "camera");
		boolean restorePlayer = camera.getBoolean("restore-player", defaultRestorePlayer);
		List<CameraPoint> points = camera.getMapList("points").stream()
			.map(CinematicConfiguration::point)
			.toList();
		return new CinematicDefinition(id, duration, new CameraPathDefinition(restorePlayer, points));
	}

	private static CameraPoint point(Map<?, ?> raw) {
		return new CameraPoint(
			number(raw, "tick").intValue(),
			text(raw, "world"),
			number(raw, "x").doubleValue(),
			number(raw, "y").doubleValue(),
			number(raw, "z").doubleValue(),
			number(raw, "yaw").floatValue(),
			number(raw, "pitch").floatValue()
		);
	}

	private static Number number(Map<?, ?> raw, String key) {
		Object value = raw.get(key);
		if (value instanceof Number number) return number;
		if (value instanceof String text) return Double.parseDouble(text);
		throw new IllegalArgumentException("camera point is missing numeric field: " + key);
	}

	private static String text(Map<?, ?> raw, String key) {
		Object value = raw.get(key);
		if (value == null || value.toString().isBlank()) {
			throw new IllegalArgumentException("camera point is missing text field: " + key);
		}
		return value.toString();
	}

	private static ConfigurationSection requireSection(ConfigurationSection parent, String key) {
		ConfigurationSection section = parent.getConfigurationSection(key);
		if (section == null) throw new IllegalArgumentException("Missing section: " + parent.getCurrentPath() + "." + key);
		return section;
	}
}
