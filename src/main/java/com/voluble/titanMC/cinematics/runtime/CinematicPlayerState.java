package com.voluble.titanMC.cinematics.runtime;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Objects;

public record CinematicPlayerState(
	Location location,
	GameMode gameMode,
	boolean allowFlight,
	boolean flying,
	boolean invulnerable
) {
	public static CinematicPlayerState capture(Player player) {
		Objects.requireNonNull(player, "player");
		return new CinematicPlayerState(
			player.getLocation().clone(),
			player.getGameMode(),
			player.getAllowFlight(),
			player.isFlying(),
			player.isInvulnerable()
		);
	}

	public void restore(Player player) {
		Objects.requireNonNull(player, "player");
		player.teleport(location);
		player.setGameMode(gameMode);
		player.setAllowFlight(allowFlight);
		player.setFlying(flying);
		player.setInvulnerable(invulnerable);
	}
}
