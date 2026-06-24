package com.voluble.titanMC.display.chat.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatFormatModelTest {

	@Test
	void chatFormatIdNormalizesAndValidates() {
		assertEquals("staff", ChatFormatId.of(" Staff ").value());
		assertThrows(IllegalArgumentException.class, () -> ChatFormatId.of("Staff Lead"));
		assertThrows(IllegalArgumentException.class, () -> ChatFormatId.of("-bad"));
	}

	@Test
	void clickActionParseAcceptsLowercaseAndWhitespace() {
		assertEquals(ChatClickAction.RUN_COMMAND, ChatClickAction.parse(" run_command "));
		assertEquals(ChatClickAction.OPEN_URL, ChatClickAction.parse("open_url"));
		assertThrows(IllegalArgumentException.class, () -> ChatClickAction.parse("teleport"));
	}

	@Test
	void clickBindingRejectsBlankValue() {
		assertThrows(IllegalArgumentException.class,
			() -> new ChatClickBinding(ChatClickAction.RUN_COMMAND, "  "));
	}

	@Test
	void segmentEmptyConstantIsTrulyEmpty() {
		assertTrue(ChatFormatSegment.EMPTY.isEmpty());
		assertEquals("", ChatFormatSegment.EMPTY.text());
		assertTrue(ChatFormatSegment.EMPTY.hover().isEmpty());
		assertTrue(ChatFormatSegment.EMPTY.click().isEmpty());
	}

	@Test
	void segmentRejectsBlankHover() {
		assertThrows(IllegalArgumentException.class,
			() -> new ChatFormatSegment("text", Optional.of(" "), Optional.empty()));
	}

	@Test
	void segmentTextFactoryAllowsBlank() {
		ChatFormatSegment segment = ChatFormatSegment.text("hello");
		assertEquals("hello", segment.text());
		assertFalse(segment.isEmpty());
	}

	@Test
	void formatLooksUpSegmentBySlot() {
		ChatFormatSegment prefix = ChatFormatSegment.text("<gray>[Staff]</gray> ");
		ChatFormatSegment name = ChatFormatSegment.text("<displayname>");
		ChatFormatSegment suffix = ChatFormatSegment.text(" *");
		ChatFormatSegment message = ChatFormatSegment.text("<gray> » </gray><message>");
		ChatFormat format = new ChatFormat(
			ChatFormatId.of("staff"), "titanmc.chat.staff", 100, prefix, name, suffix, message
		);

		assertSame(prefix, format.segment(ChatFormatSlot.PREFIX));
		assertSame(message, format.segment(ChatFormatSlot.MESSAGE));
		assertEquals(4, format.orderedSegments().size());
	}

	@Test
	void formatNormalizesBlankPermission() {
		ChatFormat format = new ChatFormat(
			ChatFormatId.of("default"), "   ", 0,
			ChatFormatSegment.EMPTY, ChatFormatSegment.text("<displayname>"),
			ChatFormatSegment.EMPTY, ChatFormatSegment.text("<gray> » </gray><message>")
		);
		assertEquals("", format.permission());
	}
}
