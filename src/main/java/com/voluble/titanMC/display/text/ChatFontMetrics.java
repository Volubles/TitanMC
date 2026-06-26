package com.voluble.titanMC.display.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ChatFontMetrics {
	public static final int DEFAULT_CHAT_CENTER_PX = 154;
	public static final ChatFontMetrics VANILLA = new ChatFontMetrics(defaultWidths(), new GlyphWidth(4));

	private static final char LEGACY_COLOR = '\u00A7';

	private final Map<Character, GlyphWidth> widths;
	private final GlyphWidth defaultWidth;

	private ChatFontMetrics(Map<Character, GlyphWidth> widths, GlyphWidth defaultWidth) {
		this.widths = Map.copyOf(widths);
		this.defaultWidth = Objects.requireNonNull(defaultWidth, "defaultWidth");
	}

	public int width(Component component) {
		Objects.requireNonNull(component, "component");
		return widthLegacy(LegacyComponentSerializer.legacySection().serialize(component));
	}

	public int widthLegacy(String legacyText) {
		Objects.requireNonNull(legacyText, "legacyText");
		int width = 0;
		boolean bold = false;
		boolean formattingCode = false;
		for (int index = 0; index < legacyText.length(); index++) {
			char character = legacyText.charAt(index);
			if (character == LEGACY_COLOR) {
				formattingCode = true;
				continue;
			}
			if (formattingCode) {
				formattingCode = false;
				bold = nextBoldState(character, bold);
				continue;
			}
			width += glyphWidth(character, bold);
		}
		return width;
	}

	public int spaceWidth() {
		return glyphWidth(' ', false);
	}

	private int glyphWidth(char character, boolean bold) {
		GlyphWidth width = widths.getOrDefault(character, defaultWidth);
		return (bold ? width.boldWidth() : width.normalWidth()) + 1;
	}

	private static boolean nextBoldState(char code, boolean current) {
		char normalized = Character.toLowerCase(code);
		if (normalized == 'l') return true;
		if (normalized == 'r' || normalized == 'x' || isLegacyColor(normalized)) return false;
		return current;
	}

	private static boolean isLegacyColor(char code) {
		return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
	}

	private static Map<Character, GlyphWidth> defaultWidths() {
		Map<Character, GlyphWidth> widths = new HashMap<>();
		add(widths, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdeghjmnopqrsuvwxyz0123456789#$%^&*_-=+?/~", 5);
		add(widths, "fkt{}()<>", 4);
		add(widths, "I[]\"", 3);
		add(widths, "`", 2);
		add(widths, "il!|:;'. ,", 1);
		widths.put(' ', new GlyphWidth(3, 3));
		widths.put('@', new GlyphWidth(6));
		widths.put('\\', new GlyphWidth(5));
		widths.put('\u00E5', new GlyphWidth(5));
		widths.put('\u00C5', new GlyphWidth(5));
		widths.put('\u00E4', new GlyphWidth(5));
		widths.put('\u00C4', new GlyphWidth(5));
		widths.put('\u00F6', new GlyphWidth(5));
		widths.put('\u00D6', new GlyphWidth(5));
		widths.put('\u2500', new GlyphWidth(5));
		return widths;
	}

	private static void add(Map<Character, GlyphWidth> widths, String characters, int width) {
		GlyphWidth glyphWidth = new GlyphWidth(width);
		for (int index = 0; index < characters.length(); index++) {
			widths.put(characters.charAt(index), glyphWidth);
		}
	}

	private record GlyphWidth(int normalWidth, int boldWidth) {
		private GlyphWidth(int normalWidth) {
			this(normalWidth, normalWidth + 1);
		}
	}
}
