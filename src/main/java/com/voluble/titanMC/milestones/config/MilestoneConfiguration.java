package com.voluble.titanMC.milestones.config;

import com.voluble.titanMC.milestones.model.MilestoneCategory;
import com.voluble.titanMC.milestones.model.MilestoneMetric;
import com.voluble.titanMC.milestones.model.MilestoneObjective;
import com.voluble.titanMC.milestones.model.MilestoneRewards;
import com.voluble.titanMC.milestones.model.MilestoneTier;
import com.voluble.titanMC.milestones.model.MilestoneTrack;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record MilestoneConfiguration(
	MilestoneMenuConfig overviewMenu,
	MilestoneMenuConfig categoryMenu,
	MilestoneMenuConfig trackMenu,
	MilestoneNotificationConfig notifications,
	MilestoneCatalog catalog,
	FileConfiguration yaml
) {
	public MilestoneConfiguration {
		Objects.requireNonNull(overviewMenu, "overviewMenu");
		Objects.requireNonNull(categoryMenu, "categoryMenu");
		Objects.requireNonNull(trackMenu, "trackMenu");
		Objects.requireNonNull(notifications, "notifications");
		Objects.requireNonNull(catalog, "catalog");
		Objects.requireNonNull(yaml, "yaml");
	}

	public static MilestoneConfiguration load(FileConfiguration yaml) {
		Objects.requireNonNull(yaml, "yaml");
		MilestoneMenuConfig overviewMenu = menu(yaml, "overview", 4, "<gold><bold>Milestones</bold>");
		MilestoneMenuConfig categoryMenu = menu(yaml, "category", 4, "<gold><bold>{category}</bold>");
		MilestoneMenuConfig trackMenu = menu(yaml, "track", 4, "<gold><bold>{category}</bold>");
		MilestoneNotificationConfig notifications = notifications(yaml);
		Map<String, MilestoneCategory> categories = categories(requireSection(yaml, "categories"));
		Map<String, MilestoneTrack> tracks = tracks(requireSection(yaml, "tracks"), categories);
		return new MilestoneConfiguration(overviewMenu, categoryMenu, trackMenu, notifications, new MilestoneCatalog(categories, tracks), yaml);
	}

	private static MilestoneMenuConfig menu(FileConfiguration yaml, String key, int rows, String title) {
		ConfigurationSection section = yaml.getConfigurationSection("menus." + key);
		if (section == null) return new MilestoneMenuConfig(rows, title);
		return new MilestoneMenuConfig(section.getInt("rows", rows), section.getString("title", title));
	}

	private static Map<String, MilestoneCategory> categories(ConfigurationSection section) {
		Map<String, MilestoneCategory> categories = new LinkedHashMap<>();
		for (String id : section.getKeys(false)) {
			ConfigurationSection entry = requireSection(section, id);
			MilestoneCategory category = new MilestoneCategory(
				id,
				requiredString(entry, "name"),
				material(entry, "icon"),
				entry.getBoolean("enabled", true)
			);
			categories.put(category.id(), category);
		}
		if (categories.isEmpty()) throw new IllegalArgumentException("milestones.yml must define at least one category");
		return categories;
	}

	private static Map<String, MilestoneTrack> tracks(ConfigurationSection section, Map<String, MilestoneCategory> categories) {
		Map<String, MilestoneTrack> tracks = new LinkedHashMap<>();
		for (String id : section.getKeys(false)) {
			ConfigurationSection entry = requireSection(section, id);
			String categoryId = requiredString(entry, "category").toLowerCase(Locale.ROOT);
			if (!categories.containsKey(categoryId)) {
				throw new IllegalArgumentException("track " + id + " uses unknown category " + categoryId);
			}
			MilestoneMetric metric = metric(entry, "metric");
			String subject = entry.getString("subject", "");
			MilestoneTrack track = new MilestoneTrack(
				id,
				categoryId,
				requiredString(entry, "name"),
				material(entry, "icon"),
				metric,
				subject,
				entry.getBoolean("linear", true),
				entry.getInt("slot", -1),
				tiers(entry, id, metric, subject)
			);
			tracks.put(track.id(), track);
		}
		return tracks;
	}

	private static List<MilestoneTier> tiers(
		ConfigurationSection track,
		String trackId,
		MilestoneMetric defaultMetric,
		String defaultSubject
	) {
		List<Map<?, ?>> configured = track.getMapList("tiers");
		if (configured.isEmpty()) throw new IllegalArgumentException("track " + trackId + " must define tiers");
		List<MilestoneTier> tiers = new ArrayList<>();
		for (Map<?, ?> values : configured) {
			String id = requiredString(values, "id", "track " + trackId + " tier");
			String name = requiredString(values, "name", "track " + trackId + " tier " + id);
			Object targetValue = values.get("target");
			if (!(targetValue instanceof Number number)) {
				throw new IllegalArgumentException("track " + trackId + " tier " + id + " target must be a number");
			}
			tiers.add(new MilestoneTier(
				id,
				name,
				new MilestoneObjective(
					metric(values, "metric", defaultMetric, "track " + trackId + " tier " + id),
					string(values, "subject", defaultSubject),
					number.longValue()
				),
				rewards(values),
				integer(values, "slot", -1, "track " + trackId + " tier " + id)
			));
		}
		return tiers;
	}

	private static MilestoneRewards rewards(Map<?, ?> values) {
		Object raw = values.get("rewards");
		if (!(raw instanceof Map<?, ?> rewards)) return MilestoneRewards.NONE;
		return new MilestoneRewards(asLong(rewards.get("cred")), asLong(rewards.get("money")));
	}

	private static long asLong(Object value) {
		if (value == null) return 0L;
		if (!(value instanceof Number number)) throw new IllegalArgumentException("milestone reward values must be numbers");
		return number.longValue();
	}

	private static MilestoneNotificationConfig notifications(FileConfiguration yaml) {
		ConfigurationSection completion = yaml.getConfigurationSection("notifications.completion");
		if (completion == null) {
			return new MilestoneNotificationConfig(new MilestoneNotificationConfig.Completion(
				true,
				50L,
				20L,
				true,
				true,
				List.of("<green>Milestone completed: <yellow>{milestone}</yellow>"),
				Optional.of("entity.player.levelup"),
				new MilestoneNotificationConfig.Broadcast(false, true, 0L, List.of(), Optional.empty())
			));
		}
		ConfigurationSection playerMessage = completion.getConfigurationSection("player-message");
		ConfigurationSection broadcast = completion.getConfigurationSection("broadcast");
		return new MilestoneNotificationConfig(new MilestoneNotificationConfig.Completion(
			completion.getBoolean("enabled", true),
			longValue(completion, "initial-delay-ticks", 50L),
			longValue(completion, "spacing-ticks", 20L),
			playerMessage == null || playerMessage.getBoolean("enabled", true),
			playerMessage == null || booleanValue(playerMessage, "centered", true),
			playerMessage == null
				? List.of("<green>Milestone completed: <yellow>{milestone}</yellow>")
				: playerMessage.getStringList("lines"),
			optionalString(completion, "sound"),
			new MilestoneNotificationConfig.Broadcast(
				broadcast != null && broadcast.getBoolean("enabled", false),
				broadcast == null || booleanValue(broadcast, "centered", true),
				broadcast == null ? 0L : broadcast.getLong("minimum-target", 0L),
				broadcast == null ? List.of() : broadcast.getStringList("lines"),
				broadcast == null ? Optional.empty() : optionalString(broadcast, "sound")
			)
		));
	}

	private static Optional<String> optionalString(ConfigurationSection section, String key) {
		String value = section.getString(key);
		return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
	}

	private static boolean booleanValue(ConfigurationSection section, String key, boolean fallback) {
		if (!section.contains(key)) return fallback;
		if (!section.isBoolean(key)) {
			throw new IllegalArgumentException(section.getCurrentPath() + "." + key + " must be true or false");
		}
		return section.getBoolean(key);
	}

	private static long longValue(ConfigurationSection section, String key, long fallback) {
		if (!section.contains(key)) return fallback;
		if (!section.isLong(key) && !section.isInt(key)) {
			throw new IllegalArgumentException(section.getCurrentPath() + "." + key + " must be a number");
		}
		long value = section.getLong(key);
		if (value < 0L) {
			throw new IllegalArgumentException(section.getCurrentPath() + "." + key + " must not be negative");
		}
		return value;
	}

	private static MilestoneMetric metric(ConfigurationSection section, String key) {
		String value = requiredString(section, key).trim().toUpperCase(Locale.ROOT);
		try {
			return MilestoneMetric.valueOf(value);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException(section.getCurrentPath() + "." + key + " has unknown metric " + value);
		}
	}

	private static MilestoneMetric metric(Map<?, ?> values, String key, MilestoneMetric fallback, String path) {
		Object value = values.get(key);
		if (value == null) return fallback;
		if (!(value instanceof String text) || text.isBlank()) {
			throw new IllegalArgumentException(path + "." + key + " must be a metric name");
		}
		try {
			return MilestoneMetric.valueOf(text.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException(path + "." + key + " has unknown metric " + text);
		}
	}

	private static String string(Map<?, ?> values, String key, String fallback) {
		Object value = values.get(key);
		if (value == null) return fallback;
		if (!(value instanceof String text)) throw new IllegalArgumentException(key + " must be a string");
		return text.trim();
	}

	private static int integer(Map<?, ?> values, String key, int fallback, String path) {
		Object value = values.get(key);
		if (value == null) return fallback;
		if (!(value instanceof Number number)) throw new IllegalArgumentException(path + "." + key + " must be a number");
		return number.intValue();
	}

	private static Material material(ConfigurationSection section, String key) {
		Material material = Material.matchMaterial(requiredString(section, key));
		if (material == null || !material.isItem()) {
			throw new IllegalArgumentException(section.getCurrentPath() + "." + key + " must be a valid item material");
		}
		return material;
	}

	private static ConfigurationSection requireSection(FileConfiguration yaml, String path) {
		ConfigurationSection section = yaml.getConfigurationSection(path);
		if (section == null) throw new IllegalArgumentException("Missing section: " + path);
		return section;
	}

	private static ConfigurationSection requireSection(ConfigurationSection parent, String key) {
		ConfigurationSection section = parent.getConfigurationSection(key);
		if (section == null) throw new IllegalArgumentException("Missing section: " + parent.getCurrentPath() + "." + key);
		return section;
	}

	private static String requiredString(ConfigurationSection section, String key) {
		String value = section.getString(key);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(section.getCurrentPath() + "." + key + " must be set");
		}
		return value.trim();
	}

	private static String requiredString(Map<?, ?> values, String key, String path) {
		Object value = values.get(key);
		if (!(value instanceof String text) || text.isBlank()) {
			throw new IllegalArgumentException(path + "." + key + " must be set");
		}
		return text.trim();
	}
}
