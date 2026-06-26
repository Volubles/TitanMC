package com.voluble.titanMC.display.notice;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MessageCatalog {
	private final Map<MessageKey, String> messages;

	private MessageCatalog(Map<MessageKey, String> messages) {
		this.messages = Map.copyOf(messages);
	}

	public static MessageCatalog load(YamlConfiguration yaml) {
		Objects.requireNonNull(yaml, "yaml");
		Map<MessageKey, String> messages = new LinkedHashMap<>();
		ConfigurationSection section = yaml.getConfigurationSection("messages");
		if (section != null) {
			for (String key : section.getKeys(true)) {
				if (!section.isString(key)) continue;
				messages.put(MessageKey.of(key), Objects.toString(section.getString(key), ""));
			}
		}
		return new MessageCatalog(messages);
	}

	public Optional<MessageEntry> find(MessageDefinition definition) {
		Objects.requireNonNull(definition, "definition");
		String text = messages.get(definition.key());
		if (text == null) return Optional.empty();
		return Optional.of(new MessageEntry(definition.type(), definition.key(), text));
	}
}
