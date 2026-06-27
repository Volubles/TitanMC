package com.voluble.titanMC.cinematics.runtime.hud;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.logging.Level;

public final class CinematicHudControllers {
	private CinematicHudControllers() {
	}

	public static CinematicHudController create(Plugin plugin, Player player) {
		Objects.requireNonNull(plugin, "plugin");
		Objects.requireNonNull(player, "player");
		if (!packetEventsAvailable(plugin)) {
			return new NoOpCinematicHudController();
		}
		try {
			return new PacketGamemodeHudController(plugin, player);
		} catch (Throwable exception) {
			plugin.getLogger().log(Level.WARNING, "Packet cinematic HUD unavailable; keeping vanilla HUD", exception);
			return new NoOpCinematicHudController();
		}
	}

	private static boolean packetEventsAvailable(Plugin plugin) {
		if (!plugin.getServer().getPluginManager().isPluginEnabled("packetevents")
			&& !plugin.getServer().getPluginManager().isPluginEnabled("PacketEvents")) {
			return false;
		}
		return present("com.github.retrooper.packetevents.PacketEvents");
	}

	private static boolean present(String className) {
		try {
			Class.forName(className);
			return true;
		} catch (ClassNotFoundException exception) {
			return false;
		}
	}
}
