package com.voluble.titanMC.display.notice;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageRendererTest {
	private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

	@Test
	void rendersPlainMessageWithArguments() {
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.set("messages.cells.created", "Created {{cell}}.");
		MessageCatalog catalog = MessageCatalog.load(yaml);
		MessageRenderer renderer = new MessageRenderer();

		String plain = PLAIN.serialize(renderer.render(
			catalog,
			MessageDefinition.of("cells.created", MessageType.SUCCESS, "Created cell."),
			new MessageArguments().plain("cell", "A1")
		).getFirst());

		assertEquals("Created A1.", plain);
	}

	@Test
	void fallsBackToDefinitionWhenKeyIsMissing() {
		YamlConfiguration yaml = new YamlConfiguration();
		MessageCatalog catalog = MessageCatalog.load(yaml);
		MessageRenderer renderer = new MessageRenderer();

		String plain = PLAIN.serialize(renderer.render(
			catalog,
			MessageDefinition.of("cells.unknown", MessageType.ERROR, "Unknown cell."),
			new MessageArguments()
		).getFirst());

		assertEquals("Unknown cell.", plain);
	}

	@Test
	void syncedCatalogLoadsNestedMessageKeys() {
		YamlConfiguration yaml = new YamlConfiguration();
		MessageDefinition definition = MessageDefinition.of("donator-tools.reload.success", MessageType.SUCCESS, "Reloaded.");
		MessageYamlSynchronizer.sync(yaml, List.of(definition));

		MessageCatalog catalog = MessageCatalog.load(yaml);

		assertEquals("Reloaded.", catalog.find(definition).orElseThrow().text());
	}

	@Test
	void usageTextRendersLiteralAngleBrackets() {
		YamlConfiguration yaml = new YamlConfiguration();
		MessageYamlSynchronizer.sync(yaml, List.of(MessageDefaults.DONATOR_TOOLS_HELP_USAGE));
		MessageCatalog catalog = MessageCatalog.load(yaml);
		MessageRenderer renderer = new MessageRenderer();

		String plain = PLAIN.serialize(renderer.render(catalog, MessageDefaults.DONATOR_TOOLS_HELP_USAGE).getFirst());

		assertEquals("/dtools <tool> [player]", plain);
	}

	@Test
	void multilineMessagesRenderOneComponentPerLineIncludingEmptyLines() {
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.set("messages.help", "Line one\n\nLine three");
		MessageCatalog catalog = MessageCatalog.load(yaml);
		MessageRenderer renderer = new MessageRenderer();

		List<Component> lines = renderer.render(
			catalog,
			MessageDefinition.of("help", MessageType.INFO, "fallback"),
			new MessageArguments()
		);

		assertEquals(3, lines.size());
		assertEquals("Line one", PLAIN.serialize(lines.get(0)));
		assertEquals("", PLAIN.serialize(lines.get(1)));
		assertEquals("Line three", PLAIN.serialize(lines.get(2)));
	}
}
