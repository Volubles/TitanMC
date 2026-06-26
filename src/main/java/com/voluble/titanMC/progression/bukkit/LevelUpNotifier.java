package com.voluble.titanMC.progression.bukkit;

import com.voluble.titanMC.display.message.DisplayBroadcastService;
import com.voluble.titanMC.display.message.DisplayLine;
import com.voluble.titanMC.display.message.DisplayMessage;
import com.voluble.titanMC.progression.config.NotificationConfig;
import com.voluble.titanMC.progression.event.PlayerLeveledUpEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Server;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Objects;
import java.util.function.Supplier;

public final class LevelUpNotifier implements Listener {

	private final Server server;
	private final DisplayBroadcastService broadcasts;
	private final Supplier<NotificationConfig> notifications;
	private final MiniMessage serializer = MiniMessage.miniMessage();

	public LevelUpNotifier(Server server, DisplayBroadcastService broadcasts, Supplier<NotificationConfig> notifications) {
		this.server = Objects.requireNonNull(server, "server");
		this.broadcasts = Objects.requireNonNull(broadcasts, "broadcasts");
		this.notifications = Objects.requireNonNull(notifications, "notifications");
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onLevelUp(PlayerLeveledUpEvent event) {
		Player player = server.getPlayer(event.playerId());
		if (player == null) return;

		NotificationConfig config = notifications.get();
		int level = event.currentLevel();
		String name = player.getName();
		boolean broadcast = config.shouldBroadcast(level);

		if (!broadcast) {
			broadcasts.send(player, message(config.playerMessages(), name, level));
		}
		config.soundForLevel(level).ifPresent(sound -> playSound(player, sound));

		if (broadcast) {
			broadcasts.broadcast(message(config.broadcastMessages(), name, level));
			config.broadcastSound().ifPresent(sound -> {
				for (Player online : server.getOnlinePlayers()) {
					playSound(online, sound);
				}
			});
		}
	}

	private void playSound(Player player, String sound) {
		player.playSound(player.getLocation(), sound, SoundCategory.MASTER, 1.0f, 1.0f);
	}

	private Component render(String template, String playerName, int level) {
		// Minecraft usernames can't contain MiniMessage delimiters so direct
		// substitution is safe; this keeps the YAML using the familiar
		// {placeholder} syntax that admins already know from other plugins.
		String substituted = template
			.replace("{player}", playerName)
			.replace("{level}", Integer.toString(level));
		return serializer.deserialize(substituted);
	}

	private DisplayMessage message(java.util.List<String> templates, String playerName, int level) {
		return new DisplayMessage(templates.stream()
			.map(template -> DisplayLine.left(render(template, playerName, level)))
			.toList());
	}
}
