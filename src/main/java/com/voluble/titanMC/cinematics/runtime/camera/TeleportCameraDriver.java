package com.voluble.titanMC.cinematics.runtime.camera;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Objects;

final class TeleportCameraDriver implements CinematicCameraDriver {
	private final Player player;

	TeleportCameraDriver(Player player) {
		this.player = Objects.requireNonNull(player, "player");
	}

	@Override
	public void start(Location location) {
		move(0, location);
	}

	@Override
	public void move(int frame, Location location) {
		player.teleport(location);
	}

	@Override
	public void stop() {
	}
}
