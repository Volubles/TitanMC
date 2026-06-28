package com.voluble.titanMC.cinematics.runtime;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public record CinematicPlayerState(
	Location location,
	GameMode gameMode,
	boolean allowFlight,
	boolean flying,
	boolean invulnerable,
	ItemStack helmet
) {
	public static CinematicPlayerState capture(Player player) {
		Objects.requireNonNull(player, "player");
		return new CinematicPlayerState(
			player.getLocation().clone(),
			player.getGameMode(),
			player.getAllowFlight(),
			player.isFlying(),
			player.isInvulnerable(),
			clone(player.getInventory().getHelmet())
		);
	}

	public void restore(Player player) {
		Objects.requireNonNull(player, "player");
		player.teleport(location);
		player.setGameMode(gameMode);
		player.setAllowFlight(allowFlight);
		player.setFlying(flying);
		player.setInvulnerable(invulnerable);
		player.getInventory().setHelmet(clone(helmet));
	}

	private static ItemStack clone(ItemStack item) {
		return item == null ? null : item.clone();
	}
}
