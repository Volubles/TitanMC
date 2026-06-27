package com.voluble.titanMC.cinematics.runtime.camera;

import com.github.retrooper.packetevents.PacketEvents;
import me.tofaa.entitylib.APIConfig;
import me.tofaa.entitylib.EntityLib;
import me.tofaa.entitylib.spigot.SpigotEntityLibPlatform;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class PacketDisplayCameraRuntime {
	private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

	private PacketDisplayCameraRuntime() {
	}

	static void initialize(JavaPlugin plugin) {
		Objects.requireNonNull(plugin, "plugin");
		if (!INITIALIZED.compareAndSet(false, true)) return;
		SpigotEntityLibPlatform platform = new SpigotEntityLibPlatform(plugin);
		APIConfig settings = new APIConfig(PacketEvents.getAPI())
			.usePlatformLogger();
		EntityLib.init(platform, settings);
		plugin.getLogger().info("Packet cinematic camera runtime initialized");
	}
}
