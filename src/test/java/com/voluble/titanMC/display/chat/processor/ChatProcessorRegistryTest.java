package com.voluble.titanMC.display.chat.processor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatProcessorRegistryTest {

	private ServerMock server;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void emptyRegistryPassesMessageThrough() {
		ChatProcessorRegistry registry = new ChatProcessorRegistry();
		Component input = Component.text("hello");

		Component output = registry.process(ChatProcessorContext.defaultRender(server.addPlayer()), input);

		assertSame(input, output);
	}

	@Test
	void processorsChainInRegistrationOrder() {
		ChatProcessorRegistry registry = new ChatProcessorRegistry();
		registry.register(new AppendProcessor("first", " + first"));
		registry.register(new AppendProcessor("second", " + second"));

		Component output = registry.process(
			ChatProcessorContext.defaultRender(server.addPlayer()),
			Component.text("base")
		);

		assertEquals("base + first + second", PlainTextComponentSerializer.plainText().serialize(output));
	}

	@Test
	void nullReturnFromProcessorIsTreatedAsUnchanged() {
		ChatProcessorRegistry registry = new ChatProcessorRegistry();
		registry.register(new NullingProcessor("noop"));
		registry.register(new AppendProcessor("append", " + append"));

		Component output = registry.process(
			ChatProcessorContext.defaultRender(server.addPlayer()),
			Component.text("hi")
		);

		assertEquals("hi + append", PlainTextComponentSerializer.plainText().serialize(output));
	}

	@Test
	void rejectsDuplicateIds() {
		ChatProcessorRegistry registry = new ChatProcessorRegistry();
		registry.register(new AppendProcessor("dup", "a"));
		assertThrows(IllegalArgumentException.class,
			() -> registry.register(new AppendProcessor("dup", "b")));
	}

	@Test
	void rejectsBlankIds() {
		ChatProcessorRegistry registry = new ChatProcessorRegistry();
		assertThrows(IllegalArgumentException.class,
			() -> registry.register(new AppendProcessor("   ", "a")));
	}

	@Test
	void allExposesRegisteredProcessorsInOrder() {
		ChatProcessor first = new AppendProcessor("first", "a");
		ChatProcessor second = new AppendProcessor("second", "b");
		ChatProcessorRegistry registry = new ChatProcessorRegistry();
		registry.register(first);
		registry.register(second);

		assertEquals(List.of(first, second), registry.all());
	}

	@Test
	void contextCarriesViewerWhenSet() {
		Player sender = server.addPlayer("sender");
		Player viewer = server.addPlayer("viewer");
		ChatProcessorContext ctx = new ChatProcessorContext(sender, viewer);

		List<Player> seenViewers = new ArrayList<>();
		ChatProcessorRegistry registry = new ChatProcessorRegistry();
		registry.register(new ChatProcessor() {
			@Override public String id() { return "viewerSink"; }
			@Override public Component process(ChatProcessorContext context, Component message) {
				seenViewers.add(context.viewer());
				return message;
			}
		});

		registry.process(ctx, Component.text("x"));
		assertEquals(viewer, seenViewers.getFirst());
	}

	private record AppendProcessor(String id, String suffix) implements ChatProcessor {
		@Override
		public Component process(ChatProcessorContext context, Component message) {
			return message.append(Component.text(suffix));
		}
	}

	private record NullingProcessor(String id) implements ChatProcessor {
		@Override
		public Component process(ChatProcessorContext context, Component message) {
			return null;
		}
	}
}
