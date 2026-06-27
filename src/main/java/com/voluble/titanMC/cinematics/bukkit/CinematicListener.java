package com.voluble.titanMC.cinematics.bukkit;

import com.voluble.titanMC.cinematics.runtime.CinematicRuntime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

public final class CinematicListener implements Listener {
	private final CinematicRuntime runtime;

	public CinematicListener(CinematicRuntime runtime) {
		this.runtime = Objects.requireNonNull(runtime, "runtime");
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		runtime.stop(event.getPlayer().getUniqueId(), false);
	}
}
