package com.voluble.titanMC.display.message;

import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

public final class DisplayBroadcastService {
	private final Server server;
	private final DisplayMessageRenderer renderer;

	public DisplayBroadcastService(Server server, DisplayMessageRenderer renderer) {
		this.server = Objects.requireNonNull(server, "server");
		this.renderer = Objects.requireNonNull(renderer, "renderer");
	}

	public static DisplayBroadcastService create(Server server) {
		return new DisplayBroadcastService(server, DisplayMessageRenderer.DEFAULT);
	}

	public void broadcast(DisplayMessage message) {
		List<Component> rendered = renderer.render(message);
		for (Player player : server.getOnlinePlayers()) {
			sendRendered(player, rendered);
		}
	}

	public void send(Player player, DisplayMessage message) {
		Objects.requireNonNull(player, "player");
		sendRendered(player, renderer.render(message));
	}

	private static void sendRendered(Player player, List<Component> rendered) {
		for (Component line : rendered) {
			player.sendMessage(line);
		}
	}
}
