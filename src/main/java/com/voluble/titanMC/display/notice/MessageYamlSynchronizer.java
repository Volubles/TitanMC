package com.voluble.titanMC.display.notice;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.Objects;

final class MessageYamlSynchronizer {
	private MessageYamlSynchronizer() {
	}

	static boolean sync(YamlConfiguration yaml, List<MessageDefinition> defaults) {
		Objects.requireNonNull(yaml, "yaml");
		Objects.requireNonNull(defaults, "defaults");
		boolean changed = false;
		for (MessageDefinition definition : defaults) {
			String path = "messages." + definition.key().value();
			if (!yaml.isSet(path)) {
				yaml.set(path, definition.defaultText());
				changed = true;
			}
		}
		return changed;
	}
}
