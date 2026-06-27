package com.voluble.titanMC.cinematics.runtime;

import com.voluble.titanMC.cinematics.model.CinematicDefinition;
import com.voluble.titanMC.cinematics.runtime.camera.CinematicCameraDriver;
import com.voluble.titanMC.cinematics.runtime.camera.CinematicCameraDrivers;
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
	private final CinematicEventExecutor events;
	private final CinematicCameraDriver camera;
	private CinematicPlayerState playerState;
	private CinematicPlayerPresentation presentation;
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
		this.events = new CinematicEventExecutor(plugin);
		this.camera = CinematicCameraDrivers.create(plugin, player);
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
		camera.stop();
		if (presentation != null) {
			presentation.restore();
			presentation = null;
		}
		if (restorePlayer && player.isOnline() && playerState != null && definition.camera().restorePlayer()) {
			playerState.restore(player);
		}
		completion.accept(player.getUniqueId());
	}

	private void setup() {
		playerState = CinematicPlayerState.capture(player);
		presentation = CinematicPlayerPresentation.apply(plugin, player);
		player.setAllowFlight(true);
		player.setFlying(true);
		player.setInvulnerable(true);
		camera.start(CameraPathInterpolator.locationAt(definition.camera().points(), 0));
	}

	private void tick() {
		try {
			if (!player.isOnline()) {
				stop(false);
				return;
			}
			if (frame > definition.durationTicks()) {
				stop(true);
				return;
			}
			camera.move(frame, CameraPathInterpolator.locationAt(definition.camera().points(), frame));
			for (var event : definition.timeline().atTick(frame)) {
				events.execute(player, event);
			}
			frame++;
		} catch (Exception exception) {
			plugin.getLogger().warning(
				"Stopped cinematic " + definition.id().value() + " for " + player.getName() + ": " + exception.getMessage()
			);
			stop(true);
		}
	}
}
