package com.voluble.titanMC.display.dialogue.titan;

import org.bukkit.map.MinecraftFont;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TitanDialogueTextWidth {
	private static final Pattern WIDTH_ENTRY = Pattern.compile("\"([0-9A-Fa-f]+)\"\\s*:\\s*([0-9.]+)");
	private static final Pattern HEX_COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");
	private static final Pattern MINI_TAG = Pattern.compile("<[^>]+>");

	private final Map<Integer, Float> widths;

	private TitanDialogueTextWidth(Map<Integer, Float> widths) {
		this.widths = Map.copyOf(widths);
	}

	public static TitanDialogueTextWidth load(Plugin plugin) {
		Objects.requireNonNull(plugin, "plugin");
		try (InputStream stream = plugin.getResource("display/dialogue/titan/Pack/Widths/widths.json")) {
			if (stream == null) throw new IllegalStateException("Missing bundled TitanMC dialogue widths.json");
			String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			Matcher matcher = WIDTH_ENTRY.matcher(json);
			Map<Integer, Float> widths = new HashMap<>();
			while (matcher.find()) {
				widths.put(Integer.parseInt(matcher.group(1), 16), Float.parseFloat(matcher.group(2)));
			}
			return new TitanDialogueTextWidth(widths);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not load TitanMC dialogue widths", exception);
		}
	}

	public float width(String text) {
		Objects.requireNonNull(text, "text");
		String stripped = stripFormatting(text);
		float width = 0;
		for (int index = 0; index < stripped.length();) {
			int codePoint = stripped.codePointAt(index);
			index += Character.charCount(codePoint);
			if (codePoint == '*') {
				width += 4;
				continue;
			}
			Float mapped = widths.get(codePoint);
			if (mapped != null) {
				width += mapped + 1;
				continue;
			}
			String glyph = Character.toString(codePoint);
			if (MinecraftFont.Font.isValid(glyph)) {
				width += MinecraftFont.Font.getWidth(glyph) + 1;
			}
		}
		return width;
	}

	public String stripFormatting(String text) {
		String withoutHex = HEX_COLOR.matcher(text).replaceAll("");
		return MINI_TAG.matcher(withoutHex).replaceAll("");
	}
}
