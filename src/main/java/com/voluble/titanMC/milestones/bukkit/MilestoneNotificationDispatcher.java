package com.voluble.titanMC.milestones.bukkit;

import com.voluble.titanMC.display.message.DisplayBroadcastService;
import com.voluble.titanMC.display.message.DisplayLine;
import com.voluble.titanMC.display.message.DisplayMessage;
import com.voluble.titanMC.milestones.config.MilestoneConfigurationManager;
import com.voluble.titanMC.milestones.config.MilestoneNotificationConfig;
import com.voluble.titanMC.milestones.model.MilestoneTier;
import com.voluble.titanMC.milestones.model.MilestoneTrack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Server;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.text.NumberFormat;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;

public final class MilestoneNotificationDispatcher {
	private static final long INITIAL_DELAY_TICKS = 50L;
	private static final long SPACING_TICKS = 20L;

	private final Plugin plugin;
	private final Server server;
	private final MilestoneConfigurationManager configuration;
	private final DisplayBroadcastService broadcasts;
	private final MiniMessage miniMessage = MiniMessage.miniMessage();
	private final Queue<NotificationContext> queue = new ArrayDeque<>();
	private BukkitTask task;

	public MilestoneNotificationDispatcher(
		Plugin plugin,
		Server server,
		MilestoneConfigurationManager configuration,
		DisplayBroadcastService broadcasts
	) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.server = Objects.requireNonNull(server, "server");
		this.configuration = Objects.requireNonNull(configuration, "configuration");
		this.broadcasts = Objects.requireNonNull(broadcasts, "broadcasts");
	}

	public void enqueue(Player player, MilestoneTrack track, MilestoneTier tier, String rewardText) {
		queue.add(NotificationContext.of(player, track, tier, rewardText));
		if (task == null) schedule(INITIAL_DELAY_TICKS);
	}

	private void schedule(long delayTicks) {
		task = server.getScheduler().runTaskLater(plugin, this::dispatchNext, delayTicks);
	}

	private void dispatchNext() {
		NotificationContext context = queue.poll();
		if (context != null) notify(context);
		if (queue.isEmpty()) {
			task = null;
			return;
		}
		schedule(SPACING_TICKS);
	}

	private void notify(NotificationContext context) {
		MilestoneNotificationConfig.Completion notification = configuration.current().notifications().completion();
		if (!notification.enabled()) return;

		Map<String, String> placeholders = context.placeholders();
		Player player = server.getPlayer(context.playerId());
		if (player != null && notification.playerMessageEnabled()) {
			broadcasts.send(player, message(notification.playerLines(), placeholders, notification.playerMessageCentered()));
		}
		if (player != null) notification.sound().ifPresent(sound -> playSound(player, sound));

		MilestoneNotificationConfig.Broadcast broadcast = notification.broadcast();
		if (!broadcast.shouldBroadcast(context.target())) return;
		broadcasts.broadcast(message(broadcast.lines(), placeholders, broadcast.centered()));
		broadcast.sound().ifPresent(sound -> {
			for (Player online : server.getOnlinePlayers()) playSound(online, sound);
		});
	}

	private DisplayMessage message(java.util.List<String> templates, Map<String, String> placeholders, boolean centered) {
		return new DisplayMessage(templates.stream()
			.map(template -> centered
				? DisplayLine.centered(render(template, placeholders))
				: DisplayLine.left(render(template, placeholders)))
			.toList());
	}

	private Component render(String template, Map<String, String> placeholders) {
		String rendered = template;
		for (var entry : placeholders.entrySet()) {
			rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return miniMessage.deserialize(rendered);
	}

	private void playSound(Player player, String sound) {
		player.playSound(player.getLocation(), sound, SoundCategory.MASTER, 1.0f, 1.0f);
	}

	private record NotificationContext(
		UUID playerId,
		String playerName,
		String trackName,
		String milestoneName,
		long target,
		String targetText,
		String rewardText
	) {
		private static NotificationContext of(Player player, MilestoneTrack track, MilestoneTier tier, String rewardText) {
			return new NotificationContext(
				player.getUniqueId(),
				player.getName(),
				track.name(),
				tier.name(),
				tier.target(),
				NumberFormat.getIntegerInstance(Locale.US).format(tier.target()),
				rewardText
			);
		}

		private Map<String, String> placeholders() {
			Map<String, String> values = new LinkedHashMap<>();
			values.put("player", playerName);
			values.put("track", trackName);
			values.put("milestone", milestoneName);
			values.put("target", targetText);
			values.put("rewards", rewardText);
			return values;
		}
	}
}
