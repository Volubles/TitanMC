package com.voluble.titanMC.display.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayMessageTest {

	@Test
	void messageCopiesInputLines() {
		List<DisplayLine> lines = new ArrayList<>();
		lines.add(DisplayLine.left(Component.text("one")));

		DisplayMessage message = new DisplayMessage(lines);
		lines.clear();

		assertEquals(1, message.lines().size());
		assertThrows(UnsupportedOperationException.class, () -> message.lines().add(DisplayLine.left(Component.text("two"))));
	}

	@Test
	void rendererCentersOnlyCenteredLines() {
		DisplayMessage message = DisplayMessage.of(
			DisplayLine.left(Component.text("left")),
			DisplayLine.centered(Component.text("center"))
		);

		List<Component> rendered = DisplayMessageRenderer.DEFAULT.render(message);
		String first = PlainTextComponentSerializer.plainText().serialize(rendered.get(0));
		String second = PlainTextComponentSerializer.plainText().serialize(rendered.get(1));

		assertEquals("left", first);
		assertTrue(second.startsWith(" "));
		assertTrue(second.endsWith("center"));
	}

	@Test
	void nullLinesAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> new DisplayMessage(Arrays.asList((DisplayLine) null)));
	}
}
