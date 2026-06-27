package com.voluble.titanMC.outfits.config;

import com.voluble.titanMC.outfits.model.OutfitDefinition;
import com.voluble.titanMC.outfits.model.OutfitId;
import com.voluble.titanMC.outfits.model.SkinModel;
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
	Map<OutfitId, OutfitDefinition> outfits
) {
	public OutfitConfiguration {
		Objects.requireNonNull(mineSkinApiKey, "mineSkinApiKey");
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
		Map<OutfitId, OutfitDefinition> outfits = outfits(requireSection(yaml, "outfits"), folder);
		return new OutfitConfiguration(enabled, prompt, promptDelay, optionalText(token), outfits);
	}

	public Optional<OutfitDefinition> find(OutfitId id) {
		return Optional.ofNullable(outfits.get(Objects.requireNonNull(id, "id")));
	}

	private static Map<OutfitId, OutfitDefinition> outfits(ConfigurationSection section, Path folder) {
		Map<OutfitId, OutfitDefinition> definitions = new LinkedHashMap<>();
		for (String key : section.getKeys(false)) {
			ConfigurationSection entry = requireSection(section, key);
			OutfitId id = OutfitId.of(key);
			String template = requiredString(entry, "template");
			OutfitDefinition definition = new OutfitDefinition(
				id,
				entry.getString("display-name", id.value()),
				entry.getStringList("description"),
				folder.resolve(template).normalize(),
				SkinModel.parse(entry.getString("model", "CLASSIC"))
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
		if (value == null || value.isBlank()) throw new IllegalArgumentException(section.getCurrentPath() + "." + key + " must be set");
		return value.trim();
	}
}
