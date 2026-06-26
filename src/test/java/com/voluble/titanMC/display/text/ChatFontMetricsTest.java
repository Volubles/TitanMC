package com.voluble.titanMC.display.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatFontMetricsTest {

	@Test
	void componentWidthUsesVanillaGlyphSizes() {
		assertEquals(10, ChatFontMetrics.VANILLA.width(Component.text("Hi!")));
	}

	@Test
	void boldFormattingAddsWidthUntilReset() {
		String section = "\u00A7";
		assertEquals(12, ChatFontMetrics.VANILLA.widthLegacy(section + "lHi" + section + "r!"));
	}

	@Test
	void colorFormattingClearsBoldState() {
		String section = "\u00A7";
		assertEquals(11, ChatFontMetrics.VANILLA.widthLegacy(section + "lH" + section + "ai!"));
	}

	@Test
	void adventureColorsDoNotCountAsVisibleGlyphs() {
		Component component = Component.text("Hi", NamedTextColor.GOLD, TextDecoration.BOLD);
		assertTrue(ChatFontMetrics.VANILLA.width(component) > ChatFontMetrics.VANILLA.width(Component.text("Hi")));
	}
}
