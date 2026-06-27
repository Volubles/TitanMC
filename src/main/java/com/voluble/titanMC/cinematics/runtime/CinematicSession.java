package com.voluble.titanMC.cinematics.runtime;

import com.voluble.titanMC.cinematics.model.CinematicDefinition;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class CinematicSession {
	private final Plugin plugin;
	private final Player player;
	private final CinematicDefinition definition;
	private final Consumer<UUID> completion;
	private CinematicPlayerState playerState;
	private BukkitTask task;
	private int frame;
	private boolean stopped;

	public CinematicSession(
		Plugin plugin,
		Player player,
		CinematicDefinition definition,
		Consumer<UUID> completion
	) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.player = Objects.requireNonNull(player, "player");
		this.definition = Objects.requireNonNull(definition, "definition");
		this.completion = Objects.requireNonNull(completion, "completion");
	}

	public UUID playerId() {
		return player.getUniqueId();
	}

	public CinematicDefinition definition() {
		return definition;
	}

	public void start() {
		if (task != null) return;
		setup();
		task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
	}

	public void stop(boolean restorePlayer) {
		if (stopped) return;
		stopped = true;
		if (task != null) {
			task.cancel();
			task = null;
		}
		if (restorePlayer && player.isOnline() && playerState != null && definition.camera().restorePlayer()) {
			playerState.restore(player);
		}
		completion.accept(player.getUniqueId());
	}

	private void setup() {
		playerState = CinematicPlayerState.capture(player);
		player.setAllowFlight(true);
		player.setFlying(true);
		player.setInvulnerable(true);
	}

	private void tick() {
		if (!player.isOnline()) {
			stop(false);
			return;
		}
		if (frame > definition.durationTicks()) {
			stop(true);
			return;
		}
		player.teleport(CameraPathInterpolator.locationAt(definition.camera().points(), frame));
		frame++;
	}
}
