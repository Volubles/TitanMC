package com.voluble.titanMC.display.dialogue;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class DialogueDefinitionLoader {
	private DialogueDefinitionLoader() {
	}

	public static DialogueDefinition loadBundled(
		Plugin plugin,
		String resource,
		String id,
		DialogueTheme defaultTheme
	) {
		Objects.requireNonNull(plugin, "plugin");
		Objects.requireNonNull(resource, "resource");
		Objects.requireNonNull(defaultTheme, "defaultTheme");
		try (InputStream stream = plugin.getResource(resource)) {
			if (stream == null) throw new IllegalStateException("Missing bundled dialogue: " + resource);
			YamlConfiguration config = YamlConfiguration.loadConfiguration(
				new InputStreamReader(stream, StandardCharsets.UTF_8)
			);
			return load(id, config, defaultTheme);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not load bundled dialogue: " + resource, exception);
		}
	}

	private static DialogueDefinition load(String id, YamlConfiguration config, DialogueTheme defaultTheme) {
		String speaker = string(config, "Character.name", "Dialogue");
		DialogueSettings settings = settings(config);
		DialogueTheme theme = theme(config, defaultTheme);
		ConfigurationSection pages = config.getConfigurationSection("Pages");
		if (pages == null) throw new IllegalStateException("Dialogue is missing Pages section");
		List<DialoguePage> loadedPages = new ArrayList<>();
		for (String pageId : pages.getKeys(false)) {
			ConfigurationSection page = pages.getConfigurationSection(pageId);
			if (page == null) continue;
			List<String> lines = page.getStringList("lines");
			if (lines.isEmpty()) throw new IllegalStateException("Dialogue page " + pageId + " has no lines");
			loadedPages.add(new DialoguePage(
				pageId,
				lines,
				answers(page),
				targetPage(page),
				page.getStringList("pre-actions"),
				page.getStringList("post-actions"),
				page.getStringList("exit-actions")
			));
		}
		return new DialogueDefinition(id, speaker, settings, theme, loadedPages);
	}

	private static DialogueSettings settings(YamlConfiguration config) {
		DialogueSettings defaults = DialogueSettings.defaults();
		return new DialogueSettings(
			config.getInt("Settings.range", defaults.range()),
			string(config, "Settings.effect", defaults.effect()),
			config.getBoolean("Settings.prevent-exit", defaults.preventExit()),
			config.getBoolean("Settings.prevent-skip", defaults.preventSkip()),
			config.getBoolean("Settings.npc-focus", defaults.npcFocus()),
			config.getBoolean("Settings.save-progress", defaults.saveProgress())
		);
	}

	private static DialogueTheme theme(YamlConfiguration config, DialogueTheme base) {
		DialogueVisualStyle visual = visualStyle(config, base.visualStyle());
		return new DialogueTheme(
			base.namespace(),
			base.fontPrefix(),
			base.pack(),
			base.textWidth(),
			color(config, "Colors.name", base.speakerColor()),
			color(config, "Colors.dialogue", base.dialogueColor()),
			color(config, "Colors.answer", base.answerColor()),
			color(config, "Colors.selected", base.selectedAnswerColor()),
			visual,
			base.layout(),
			sound(config.getConfigurationSection("Sounds.typing")),
			sound(config.getConfigurationSection("Sounds.selection")),
			config.getInt("Settings.typing-speed", base.typingSpeedTicks())
		);
	}

	private static DialogueVisualStyle visualStyle(YamlConfiguration config, DialogueVisualStyle base) {
		return new DialogueVisualStyle(
			config.getBoolean("Settings.character-name", base.characterName()),
			config.getBoolean("Settings.character-image", base.characterImage()),
			config.getBoolean("Settings.background-fog", base.backgroundFog()),
			config.getBoolean("Settings.answer-numbers", base.answerNumbers()),
			config.getInt("Offsets.name", base.nameTextOffset()),
			config.getInt("Offsets.name-background", base.nameBackgroundOffset()),
			config.getInt("Offsets.dialogue-background", base.dialogueBackgroundOffset()),
			config.getInt("Offsets.dialogue-line", base.dialogueTextOffset()),
			config.getInt("Offsets.answer-background", base.answerBackgroundOffset()),
			config.getInt("Offsets.answer-line", base.answerTextOffset()),
			config.getInt("Offsets.arrow", base.arrowOffset()),
			config.getInt("Offsets.character", base.characterOffset()),
			string(config, "Images.character-background", base.characterImageKey()),
			string(config, "Images.arrow", base.arrowImageKey()),
			string(config, "Images.dialogue-background", base.dialogueBackgroundImageKey()),
			string(config, "Images.answer-background", base.answerBackgroundImageKey()),
			string(config, "Images.name-start", base.nameStartImageKey()),
			string(config, "Images.name-mid", base.nameMidImageKey()),
			string(config, "Images.name-end", base.nameEndImageKey()),
			string(config, "Images.fog", base.fogImageKey()),
			color(config, "Colors.name-background", base.nameBackgroundColor()),
			color(config, "Colors.dialogue-background", base.dialogueBackgroundColor()),
			color(config, "Colors.answer-background", base.answerBackgroundColor()),
			color(config, "Colors.character-background", base.characterBackgroundColor()),
			color(config, "Colors.arrow", base.arrowColor()),
			color(config, "Colors.fog", base.fogColor())
		);
	}

	private static List<DialogueAnswer> answers(ConfigurationSection page) {
		ConfigurationSection section = page.getConfigurationSection("answers");
		if (section == null) return List.of();
		List<DialogueAnswer> answers = new ArrayList<>();
		for (String id : section.getKeys(false)) {
			ConfigurationSection answer = section.getConfigurationSection(id);
			if (answer == null) continue;
			String text = string(answer, "text", id);
			String target = answer.getString("goto", "");
			answers.add(new DialogueAnswer(
				id,
				text,
				target == null || target.isBlank() ? Optional.empty() : Optional.of(target),
				optional(answer.getString("condition")),
				answer.getStringList("reply"),
				sound(answer.getConfigurationSection("sound")),
				answer.getStringList("actions")
			));
		}
		return answers;
	}

	private static Optional<String> targetPage(ConfigurationSection page) {
		String target = page.getString("goto", "");
		return target == null || target.isBlank() ? Optional.empty() : Optional.of(target);
	}

	private static Optional<DialogueSound> sound(ConfigurationSection section) {
		if (section == null) return Optional.empty();
		String id = section.getString("id");
		if (id == null || id.isBlank()) return Optional.empty();
		return Optional.of(new DialogueSound(
			Key.key(id),
			DialogueSound.source(section.getString("source")),
			(float) section.getDouble("volume", 1.0),
			(float) section.getDouble("pitch", 1.0)
		));
	}

	private static Optional<String> optional(String value) {
		return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
	}

	private static TextColor color(YamlConfiguration config, String path, TextColor fallback) {
		return color(config.getString(path), fallback);
	}

	private static TextColor color(String value, TextColor fallback) {
		if (value == null || value.isBlank()) return fallback;
		TextColor color = TextColor.fromHexString(value);
		return color == null ? fallback : color;
	}

	private static String string(YamlConfiguration config, String path, String fallback) {
		String value = config.getString(path);
		return value == null || value.isBlank() ? fallback : value;
	}

	private static String string(ConfigurationSection section, String path, String fallback) {
		String value = section.getString(path);
		return value == null || value.isBlank() ? fallback : value;
	}
}
