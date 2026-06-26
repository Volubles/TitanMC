package com.voluble.titanMC.milestones.config;

import com.voluble.titanMC.milestones.model.MilestoneCategory;
import com.voluble.titanMC.milestones.model.MilestoneMetric;
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

public record MilestoneConfiguration(
	MilestoneMenuConfig overviewMenu,
	MilestoneMenuConfig categoryMenu,
	MilestoneCatalog catalog,
	FileConfiguration yaml
) {
	public MilestoneConfiguration {
		Objects.requireNonNull(overviewMenu, "overviewMenu");
		Objects.requireNonNull(categoryMenu, "categoryMenu");
		Objects.requireNonNull(catalog, "catalog");
		Objects.requireNonNull(yaml, "yaml");
	}

	public static MilestoneConfiguration load(FileConfiguration yaml) {
		Objects.requireNonNull(yaml, "yaml");
		MilestoneMenuConfig overviewMenu = menu(yaml, "overview", 5, "<gold><bold>Milestones</bold>");
		MilestoneMenuConfig categoryMenu = menu(yaml, "category", 6, "<gold><bold>{category}</bold>");
		Map<String, MilestoneCategory> categories = categories(requireSection(yaml, "categories"));
		Map<String, MilestoneTrack> tracks = tracks(requireSection(yaml, "tracks"), categories);
		return new MilestoneConfiguration(overviewMenu, categoryMenu, new MilestoneCatalog(categories, tracks), yaml);
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
			MilestoneTrack track = new MilestoneTrack(
				id,
				categoryId,
				requiredString(entry, "name"),
				material(entry, "icon"),
				metric(entry, "metric"),
				entry.getString("subject", ""),
				tiers(entry, id)
			);
			tracks.put(track.id(), track);
		}
		return tracks;
	}

	private static List<MilestoneTier> tiers(ConfigurationSection track, String trackId) {
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
			tiers.add(new MilestoneTier(id, name, number.longValue()));
		}
		return tiers;
	}

	private static MilestoneMetric metric(ConfigurationSection section, String key) {
		String value = requiredString(section, key).trim().toUpperCase(Locale.ROOT);
		try {
			return MilestoneMetric.valueOf(value);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException(section.getCurrentPath() + "." + key + " has unknown metric " + value);
		}
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
