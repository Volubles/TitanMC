package com.voluble.titanMC.display.notice;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class PluginMessageService {
	private final MessageConfigurationManager configuration;
	private final MessageRenderer renderer;

	public PluginMessageService(MessageConfigurationManager configuration, MessageRenderer renderer) {
		this.configuration = Objects.requireNonNull(configuration, "configuration");
		this.renderer = Objects.requireNonNull(renderer, "renderer");
	}

	public void send(CommandSender sender, MessageDefinition definition) {
		send(sender, definition, arguments -> {});
	}

	public void send(CommandSender sender, MessageDefinition definition, Consumer<MessageArguments> customize) {
		Objects.requireNonNull(sender, "sender");
		Objects.requireNonNull(definition, "definition");
		MessageArguments arguments = new MessageArguments();
		Objects.requireNonNull(customize, "customize").accept(arguments);
		for (Component line : renderer.render(configuration.current(), definition, arguments)) {
			sender.sendMessage(line);
		}
	}

	public List<Component> render(MessageDefinition definition, Consumer<MessageArguments> customize) {
		MessageArguments arguments = new MessageArguments();
		Objects.requireNonNull(customize, "customize").accept(arguments);
		return renderer.render(configuration.current(), definition, arguments);
	}
}
