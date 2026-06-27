package com.voluble.titanMC.cinematics.runtime.camera;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public final class CinematicCameraDrivers {
	private CinematicCameraDrivers() {
	}

	public static CinematicCameraDriver create(Plugin plugin, Player player) {
		Objects.requireNonNull(plugin, "plugin");
		Objects.requireNonNull(player, "player");
		if (!packetCameraAvailable(plugin)) {
			return new TeleportCameraDriver(player);
		}
		if (!(plugin instanceof JavaPlugin javaPlugin)) {
			return new TeleportCameraDriver(player);
		}
		try {
			PacketDisplayCameraRuntime.initialize(javaPlugin);
			return new PacketDisplayCameraDriver(plugin, player);
		} catch (Throwable exception) {
			plugin.getLogger().log(Level.WARNING, "Packet cinematic camera unavailable; falling back to player teleports", exception);
			return new TeleportCameraDriver(player);
		}
	}

	private static boolean packetCameraAvailable(Plugin plugin) {
		if (!plugin.getServer().getPluginManager().isPluginEnabled("packetevents")
			&& !plugin.getServer().getPluginManager().isPluginEnabled("PacketEvents")) {
			return false;
		}
		return present("com.github.retrooper.packetevents.PacketEvents")
			&& present("me.tofaa.entitylib.EntityLib");
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
