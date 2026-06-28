package com.voluble.titanMC.outfits.config;

import com.voluble.titanMC.outfits.model.OutfitDefinition;
import com.voluble.titanMC.outfits.model.OutfitId;
import com.voluble.titanMC.outfits.model.OutfitRenderMode;
import com.voluble.titanMC.outfits.model.SkinModel;
import com.voluble.titanMC.outfits.skin.MineSkinVisibility;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record OutfitConfiguration(
	boolean enabled,
	boolean firstJoinPrompt,
	long firstJoinPromptDelayTicks,
	Optional<String> mineSkinApiKey,
	MineSkinVisibility mineSkinVisibility,
	Map<OutfitId, OutfitDefinition> outfits
) {
	public OutfitConfiguration {
		Objects.requireNonNull(mineSkinApiKey, "mineSkinApiKey");
		Objects.requireNonNull(mineSkinVisibility, "mineSkinVisibility");
		outfits = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(outfits, "outfits")));
		if (firstJoinPromptDelayTicks < 0L) throw new IllegalArgumentException("first join prompt delay must not be negative");
	}

	public static OutfitConfiguration load(FileConfiguration yaml, Path folder) {
		Objects.requireNonNull(yaml, "yaml");
		Objects.requireNonNull(folder, "folder");
		boolean enabled = yaml.getBoolean("enabled", true);
		ConfigurationSection firstJoin = yaml.getConfigurationSection("first-join");
		boolean prompt = firstJoin == null || firstJoin.getBoolean("prompt", true);
		long promptDelay = firstJoin == null ? 60L : firstJoin.getLong("delay-ticks", 60L);
		String token = apiKey(yaml);
		MineSkinVisibility visibility = MineSkinVisibility.parse(yaml.getString("integrations.mineskin.visibility", "unlisted"));
		Map<OutfitId, OutfitDefinition> outfits = outfits(requireSection(yaml, "outfits"), folder);
		return new OutfitConfiguration(enabled, prompt, promptDelay, optionalText(token), visibility, outfits);
	}

	public Optional<OutfitDefinition> find(OutfitId id) {
		return Optional.ofNullable(outfits.get(Objects.requireNonNull(id, "id")));
	}

	private static Map<OutfitId, OutfitDefinition> outfits(ConfigurationSection section, Path folder) {
		Map<OutfitId, OutfitDefinition> definitions = new LinkedHashMap<>();
		for (String key : section.getKeys(false)) {
			ConfigurationSection entry = requireSection(section, key);
			OutfitId id = OutfitId.of(key);
			String legacyTemplate = optionalString(entry, "template").orElse(null);
			String classicTemplate = optionalString(entry, "classic-template").orElse(legacyTemplate);
			if (classicTemplate == null) throw new IllegalArgumentException(entry.getCurrentPath() + ".classic-template must be set");
			String slimTemplate = optionalString(entry, "slim-template").orElse(classicTemplate);
			OutfitDefinition definition = new OutfitDefinition(
				id,
				entry.getString("display-name", id.value()),
				entry.getStringList("description"),
				OutfitRenderMode.parse(entry.getString("render-mode", "composite")),
				SkinModel.parse(entry.getString("skin-model", entry.getString("model", "classic"))),
				folder.resolve(classicTemplate).normalize(),
				folder.resolve(slimTemplate).normalize()
			);
			definitions.put(id, definition);
		}
		if (definitions.isEmpty()) throw new IllegalArgumentException("outfits.yml must define at least one outfit");
		return definitions;
	}

	private static String apiKey(FileConfiguration yaml) {
		String configured = yaml.getString("integrations.mineskin.api-key", "");
		if (configured != null && !configured.isBlank()) return configured.trim();
		String envName = yaml.getString("integrations.mineskin.api-key-env", "");
		if (envName == null || envName.isBlank()) return "";
		String env = System.getenv(envName.trim());
		return env == null ? "" : env.trim();
	}

	private static Optional<String> optionalText(String value) {
		return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
	}

	private static Optional<String> optionalString(ConfigurationSection section, String key) {
		String value = section.getString(key);
		return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
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

}
