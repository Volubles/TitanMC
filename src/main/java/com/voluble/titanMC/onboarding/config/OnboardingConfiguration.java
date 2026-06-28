package com.voluble.titanMC.onboarding.config;

import com.voluble.titanMC.cinematics.model.CinematicId;
import com.voluble.titanMC.outfits.model.OutfitId;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Objects;

public record OnboardingConfiguration(
	boolean enabled,
	boolean firstJoinEnabled,
	long firstJoinDelayTicks,
	CinematicId cinematic,
	long inputCooldownMillis,
	OnboardingPreviewMode previewMode,
	OnboardingPreviewStage previewStage,
	List<OutfitId> outfits
) {
	public OnboardingConfiguration {
		Objects.requireNonNull(cinematic, "cinematic");
		Objects.requireNonNull(previewMode, "previewMode");
		Objects.requireNonNull(previewStage, "previewStage");
		outfits = List.copyOf(Objects.requireNonNull(outfits, "outfits"));
		if (firstJoinDelayTicks < 0L) throw new IllegalArgumentException("first join delay must not be negative");
		if (inputCooldownMillis < 0L) throw new IllegalArgumentException("input cooldown must not be negative");
		if (outfits.isEmpty()) throw new IllegalArgumentException("onboarding.yml must define at least one outfit");
	}

	public static OnboardingConfiguration load(FileConfiguration yaml) {
		ConfigurationSection firstJoin = yaml.getConfigurationSection("first-join");
		ConfigurationSection input = yaml.getConfigurationSection("input");
		ConfigurationSection preview = requiredSection(yaml, "preview");
		OnboardingPreviewMode previewMode = OnboardingPreviewMode.parse(requiredString(preview, "preview.mode"));
		return new OnboardingConfiguration(
			requiredBoolean(yaml, "enabled"),
			requiredBoolean(firstJoin, "first-join.enabled"),
			requiredLong(firstJoin, "first-join.delay-ticks"),
			CinematicId.of(requiredString(yaml, "cinematic")),
			requiredLong(input, "input.repeat-cooldown-ms"),
			previewMode,
			OnboardingPreviewStage.load(preview, previewMode),
			requiredStringList(yaml, "outfits").stream().map(OutfitId::of).toList()
		);
	}

	public record LocationSpec(String world, double x, double y, double z, float yaw, float pitch) {
		public LocationSpec {
			world = Objects.requireNonNull(world, "world").trim();
			if (world.isBlank()) throw new IllegalArgumentException("location world must not be blank");
		}

		public static LocationSpec load(ConfigurationSection section) {
			return new LocationSpec(
				requiredString(section, "world"),
				requiredDouble(section, "x"),
				requiredDouble(section, "y"),
				requiredDouble(section, "z"),
				(float) requiredDouble(section, "yaw"),
				(float) requiredDouble(section, "pitch")
			);
		}

		public static LocationSpec from(Location location) {
			World world = Objects.requireNonNull(location.getWorld(), "location world");
			return new LocationSpec(
				world.getName(),
				location.getX(),
				location.getY(),
				location.getZ(),
				location.getYaw(),
				location.getPitch()
			);
		}

		public Location toLocation() {
			World bukkitWorld = Bukkit.getWorld(world);
			if (bukkitWorld == null) throw new IllegalStateException("Unknown onboarding world: " + world);
			return new Location(bukkitWorld, x, y, z, yaw, pitch);
		}
	}

	private static boolean requiredBoolean(ConfigurationSection section, String path) {
		if (section == null) throw new IllegalArgumentException("Missing onboarding config section for " + path);
		if (!section.contains(lastPathPart(path))) throw new IllegalArgumentException("Missing onboarding config value: " + path);
		if (!section.isBoolean(lastPathPart(path))) throw new IllegalArgumentException("Onboarding config value must be boolean: " + path);
		return section.getBoolean(lastPathPart(path));
	}

	private static ConfigurationSection requiredSection(ConfigurationSection section, String path) {
		if (section == null) throw new IllegalArgumentException("Missing onboarding config section for " + path);
		ConfigurationSection child = section.getConfigurationSection(lastPathPart(path));
		if (child == null) throw new IllegalArgumentException("Missing onboarding config section: " + path);
		return child;
	}

	private static long requiredLong(ConfigurationSection section, String path) {
		if (section == null) throw new IllegalArgumentException("Missing onboarding config section for " + path);
		if (!section.contains(lastPathPart(path))) throw new IllegalArgumentException("Missing onboarding config value: " + path);
		if (!section.isLong(lastPathPart(path)) && !section.isInt(lastPathPart(path))) {
			throw new IllegalArgumentException("Onboarding config value must be a whole number: " + path);
		}
		return section.getLong(lastPathPart(path));
	}

	private static double requiredDouble(ConfigurationSection section, String path) {
		if (section == null) throw new IllegalArgumentException("Missing onboarding config section for " + path);
		if (!section.contains(lastPathPart(path))) throw new IllegalArgumentException("Missing onboarding config value: " + path);
		if (!section.isDouble(lastPathPart(path)) && !section.isLong(lastPathPart(path)) && !section.isInt(lastPathPart(path))) {
			throw new IllegalArgumentException("Onboarding config value must be numeric: " + path);
		}
		return section.getDouble(lastPathPart(path));
	}

	private static String requiredString(ConfigurationSection section, String path) {
		if (section == null) throw new IllegalArgumentException("Missing onboarding config section for " + path);
		if (!section.contains(lastPathPart(path))) throw new IllegalArgumentException("Missing onboarding config value: " + path);
		if (!section.isString(lastPathPart(path))) throw new IllegalArgumentException("Onboarding config value must be text: " + path);
		String value = section.getString(lastPathPart(path));
		if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing onboarding config value: " + path);
		return value;
	}

	private static List<String> requiredStringList(ConfigurationSection section, String path) {
		if (section == null) throw new IllegalArgumentException("Missing onboarding config section for " + path);
		if (!section.contains(lastPathPart(path))) throw new IllegalArgumentException("Missing onboarding config value: " + path);
		if (!section.isList(lastPathPart(path))) throw new IllegalArgumentException("Onboarding config value must be a list: " + path);
		return section.getStringList(lastPathPart(path));
	}

	private static String lastPathPart(String path) {
		int separator = path.lastIndexOf('.');
		return separator < 0 ? path : path.substring(separator + 1);
	}
}
