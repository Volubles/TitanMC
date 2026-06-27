package com.voluble.titanMC.cinematics.runtime;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Objects;

final class CinematicPlayerPresentation {
	private final Plugin plugin;
	private final Player player;
	private final List<Player> hiddenPlayers;
	private final PotionEffect previousInvisibility;

	private CinematicPlayerPresentation(Plugin plugin, Player player, List<Player> hiddenPlayers, PotionEffect previousInvisibility) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.player = Objects.requireNonNull(player, "player");
		this.hiddenPlayers = List.copyOf(hiddenPlayers);
		this.previousInvisibility = previousInvisibility;
	}

	static CinematicPlayerPresentation apply(Plugin plugin, Player player) {
		Objects.requireNonNull(plugin, "plugin");
		Objects.requireNonNull(player, "player");
		PotionEffect previous = player.getPotionEffect(PotionEffectType.INVISIBILITY);
		List<Player> others = plugin.getServer().getOnlinePlayers().stream()
			.map(Player.class::cast)
			.filter(other -> !other.getUniqueId().equals(player.getUniqueId()))
			.toList();
		player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
		for (Player other : others) {
			other.hidePlayer(plugin, player);
			player.hidePlayer(plugin, other);
		}
		return new CinematicPlayerPresentation(plugin, player, others, previous);
	}

	void restore() {
		for (Player other : hiddenPlayers) {
			if (!other.isOnline()) continue;
			other.showPlayer(plugin, player);
			if (player.isOnline()) player.showPlayer(plugin, other);
		}
		if (!player.isOnline()) return;
		player.removePotionEffect(PotionEffectType.INVISIBILITY);
		if (previousInvisibility != null) {
			player.addPotionEffect(previousInvisibility);
		}
	}
}
