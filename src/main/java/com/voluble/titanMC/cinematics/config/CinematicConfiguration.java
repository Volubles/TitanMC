package com.voluble.titanMC.cinematics.config;

import com.voluble.titanMC.cinematics.model.CameraPathDefinition;
import com.voluble.titanMC.cinematics.model.CameraPoint;
import com.voluble.titanMC.cinematics.model.CinematicEvent;
import com.voluble.titanMC.cinematics.model.CinematicEventPosition;
import com.voluble.titanMC.cinematics.model.CinematicEventType;
import com.voluble.titanMC.cinematics.model.CinematicDefinition;
import com.voluble.titanMC.cinematics.model.CinematicId;
import com.voluble.titanMC.cinematics.model.CinematicTimeline;
import com.voluble.titanMC.cinematics.model.CommandCinematicEvent;
import com.voluble.titanMC.cinematics.model.HeadCinematicEvent;
import com.voluble.titanMC.cinematics.model.ParticleCinematicEvent;
import com.voluble.titanMC.cinematics.model.ScreenCinematicEvent;
import com.voluble.titanMC.cinematics.model.SoundCinematicEvent;
import com.voluble.titanMC.display.screen.ScreenEffectId;
import com.voluble.titanMC.display.screen.ScreenEffectTiming;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
	private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

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
		return new CinematicDefinition(id, duration, new CameraPathDefinition(restorePlayer, points), timeline(section));
	}

	private static CinematicTimeline timeline(ConfigurationSection cinematic) {
		ConfigurationSection section = cinematic.getConfigurationSection("timeline");
		if (section == null) return CinematicTimeline.EMPTY;
		List<CinematicEvent> events = section.getMapList("events").stream()
			.map(CinematicConfiguration::event)
			.toList();
		return new CinematicTimeline(events);
	}

	private static CinematicEvent event(Map<?, ?> raw) {
		CinematicEventType type = CinematicEventType.parse(text(raw, "type"));
		int tick = number(raw, "tick").intValue();
		int slot = number(raw, "slot", tick).intValue();
		int row = number(raw, "row", 1).intValue();
		return switch (type) {
			case COMMAND -> new CommandCinematicEvent(
				tick,
				slot,
				row,
				text(raw, "command"),
				bool(raw, "console", true)
			);
			case HEAD -> new HeadCinematicEvent(
				tick,
				slot,
				row,
				text(raw, "material")
			);
			case PARTICLE -> new ParticleCinematicEvent(
				tick,
				slot,
				row,
				position(raw),
				text(raw, "particle", "CLOUD"),
				number(raw, "count", 8).intValue(),
				number(raw, "offset-x", 0.0).doubleValue(),
				number(raw, "offset-y", 0.0).doubleValue(),
				number(raw, "offset-z", 0.0).doubleValue(),
				number(raw, "speed", 0.0).doubleValue()
			);
			case SCREEN -> new ScreenCinematicEvent(
				tick,
				slot,
				row,
				ScreenEffectId.of(text(raw, "screen")),
				optionalText(raw, "title").map(MINI_MESSAGE::deserialize),
				optionalTiming(raw)
			);
			case SOUND -> new SoundCinematicEvent(
				tick,
				slot,
				row,
				position(raw),
				text(raw, "key"),
				number(raw, "volume", 1.0).floatValue(),
				number(raw, "pitch", 1.0).floatValue(),
				text(raw, "category", "MASTER")
			);
		};
	}

	private static CameraPoint point(Map<?, ?> raw) {
		int tick = number(raw, "tick").intValue();
		return new CameraPoint(
			tick,
			number(raw, "slot", tick).intValue(),
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

	private static Number number(Map<?, ?> raw, String key, Number fallback) {
		Object value = raw.get(key);
		if (value == null) return fallback;
		if (value instanceof Number number) return number;
		if (value instanceof String text) return Double.parseDouble(text);
		throw new IllegalArgumentException("cinematic event has invalid numeric field: " + key);
	}

	private static String text(Map<?, ?> raw, String key) {
		Object value = raw.get(key);
		if (value == null || value.toString().isBlank()) {
			throw new IllegalArgumentException("cinematic entry is missing text field: " + key);
		}
		return value.toString();
	}

	private static String text(Map<?, ?> raw, String key, String fallback) {
		Object value = raw.get(key);
		if (value == null || value.toString().isBlank()) return fallback;
		return value.toString();
	}

	private static Optional<String> optionalText(Map<?, ?> raw, String key) {
		Object value = raw.get(key);
		if (value == null || value.toString().isBlank()) return Optional.empty();
		return Optional.of(value.toString());
	}

	private static Optional<ScreenEffectTiming> optionalTiming(Map<?, ?> raw) {
		if (!raw.containsKey("fade-in-ticks") && !raw.containsKey("hold-ticks") && !raw.containsKey("fade-out-ticks")) {
			return Optional.empty();
		}
		return Optional.of(new ScreenEffectTiming(
			number(raw, "fade-in-ticks", 10).longValue(),
			number(raw, "hold-ticks", 20).longValue(),
			number(raw, "fade-out-ticks", 10).longValue()
		));
	}

	private static boolean bool(Map<?, ?> raw, String key, boolean fallback) {
		Object value = raw.get(key);
		if (value == null) return fallback;
		if (value instanceof Boolean bool) return bool;
		return Boolean.parseBoolean(value.toString());
	}

	private static CinematicEventPosition position(Map<?, ?> raw) {
		return new CinematicEventPosition(
			text(raw, "world"),
			number(raw, "x").doubleValue(),
			number(raw, "y").doubleValue(),
			number(raw, "z").doubleValue()
		);
	}

	private static ConfigurationSection requireSection(ConfigurationSection parent, String key) {
		ConfigurationSection section = parent.getConfigurationSection(key);
		if (section == null) throw new IllegalArgumentException("Missing section: " + parent.getCurrentPath() + "." + key);
		return section;
	}
}
