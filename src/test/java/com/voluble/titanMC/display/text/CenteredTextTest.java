package com.voluble.titanMC.display.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CenteredTextTest {

	@Test
	void blankComponentIsReturnedUnchanged() {
		Component blank = Component.empty();

		assertSame(blank, CenteredText.DEFAULT.center(blank));
	}

	@Test
	void centeredComponentKeepsOriginalTextAfterPadding() {
		Component centered = CenteredText.DEFAULT.center(Component.text("Level Up"));
		String plain = PlainTextComponentSerializer.plainText().serialize(centered);

		assertTrue(plain.startsWith(" "));
		assertTrue(plain.endsWith("Level Up"));
	}

	@Test
	void componentWiderThanChatCenterIsReturnedUnchanged() {
		Component wide = Component.text("W".repeat(80));

		assertEquals(wide, CenteredText.DEFAULT.center(wide));
	}
}
