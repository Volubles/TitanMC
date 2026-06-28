package com.voluble.titanMC.display.dialogue;

import com.voluble.titanMC.display.dialogue.titan.TitanDialoguePack;
import com.voluble.titanMC.display.dialogue.titan.TitanDialogueTextWidth;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

public final class DialogueThemeLoader {
	private static final String LINES_RESOURCE = "display/dialogue/titan/Pack/Lines/lines.yml";
	private static final String CONFIG_RESOURCE = "display/dialogue/titan/config.yml";

	private DialogueThemeLoader() {
	}

	public static DialogueTheme titanDefault(Plugin plugin, TitanDialoguePack pack) {
		Objects.requireNonNull(plugin, "plugin");
		Objects.requireNonNull(pack, "pack");
		YamlConfiguration lines = load(plugin, LINES_RESOURCE);
		YamlConfiguration config = load(plugin, CONFIG_RESOURCE);
		DialogueLayout layout = new DialogueLayout(
			lines.getInt("Character-Name.ascent", 40),
			lines.getInt("Dialogue-Lines.ascent", 25),
			lines.getInt("Dialogue-Lines.space", 9),
			lines.getInt("Answer-Lines.ascent", 75),
			lines.getInt("Answer-Lines.space", 9),
			lines.getInt("Dialogue-Lines.count", 5),
			lines.getInt("Answer-Lines.count", 3)
		);
		String namespace = config.getString("namespace", "titanmc_dialogue");
		DialogueVisualStyle visualStyle = new DialogueVisualStyle(
			true,
			true,
			true,
			true,
			0,
			20,
			0,
			10,
			140,
			13,
			-7,
			-16,
			"character-background",
			"hand",
			"dialogue-background",
			"answer-background",
			"name-start",
			"name-mid",
			"name-end",
			"fog",
			color("#f8ffe0"),
			color("#f8ffe0"),
			color("#f8ffe0"),
			color("#ffffff"),
			color("#cdff29"),
			color("#000000")
		);
		return new DialogueTheme(
			namespace,
			namespace,
			pack,
			TitanDialogueTextWidth.load(plugin),
			color(config.getString("colors.speaker", "#4f4a3e")),
			color(config.getString("colors.dialogue", "#4f4a3e")),
			color(config.getString("colors.answer", "#4f4a3e")),
			color(config.getString("colors.selected-answer", "#4f4a3e")),
			visualStyle,
			layout,
			Optional.empty(),
			Optional.empty(),
			config.getInt("typing-speed-ticks", 1)
		);
	}

	private static YamlConfiguration load(Plugin plugin, String resource) {
		try (InputStream stream = plugin.getResource(resource)) {
			if (stream == null) throw new IllegalStateException("Missing bundled resource: " + resource);
			return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
		} catch (Exception exception) {
			throw new IllegalStateException("Could not load bundled resource: " + resource, exception);
		}
	}

	private static TextColor color(String value) {
		TextColor color = TextColor.fromHexString(value == null ? "#ffffff" : value);
		return color == null ? TextColor.color(0xffffff) : color;
	}
}
