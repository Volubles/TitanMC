package com.voluble.titanMC.cinematics.runtime;

import com.voluble.titanMC.cinematics.model.CinematicEvent;
import com.voluble.titanMC.cinematics.model.CommandCinematicEvent;
import com.voluble.titanMC.cinematics.model.HeadCinematicEvent;
import com.voluble.titanMC.cinematics.model.ParticleCinematicEvent;
import com.voluble.titanMC.cinematics.model.ScreenCinematicEvent;
import com.voluble.titanMC.cinematics.model.SoundCinematicEvent;
import com.voluble.titanMC.display.screen.ScreenEffectRequest;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Objects;

final class CinematicEventExecutor {
	private final Plugin plugin;
	private final CinematicScreenEffects screenEffects;

	CinematicEventExecutor(Plugin plugin, CinematicScreenEffects screenEffects) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.screenEffects = Objects.requireNonNull(screenEffects, "screenEffects");
	}

	void execute(Player player, CinematicEvent event) {
		try {
			switch (event) {
				case CommandCinematicEvent command -> command(player, command);
				case HeadCinematicEvent head -> head(player, head);
				case ParticleCinematicEvent particle -> particle(player, particle);
				case ScreenCinematicEvent screen -> screen(player, screen);
				case SoundCinematicEvent sound -> sound(player, sound);
			}
		} catch (Exception exception) {
			plugin.getLogger().warning("Failed to execute cinematic event " + event.type() + " at tick " + event.tick() + ": " + exception.getMessage());
		}
	}

	private void command(Player player, CommandCinematicEvent event) {
		String command = event.command().replace("{player}", player.getName());
		if (command.startsWith("/")) command = command.substring(1);
		CommandSender sender = event.console() ? Bukkit.getConsoleSender() : player;
		Bukkit.dispatchCommand(sender, command);
	}

	private void head(Player player, HeadCinematicEvent event) {
		Material material = Material.matchMaterial(event.material());
		if (material == null) throw new IllegalArgumentException("Unknown material: " + event.material());
		player.getInventory().setHelmet(material.isAir() ? null : new org.bukkit.inventory.ItemStack(material));
	}

	private void particle(Player player, ParticleCinematicEvent event) {
		Particle particle = Particle.valueOf(event.particle().trim().toUpperCase(Locale.ROOT));
		player.spawnParticle(
			particle,
			event.position().toLocation(),
			event.count(),
			event.offsetX(),
			event.offsetY(),
			event.offsetZ(),
			event.speed()
		);
	}

	private void sound(Player player, SoundCinematicEvent event) {
		SoundCategory category = SoundCategory.valueOf(event.category().trim().replace('-', '_').toUpperCase(Locale.ROOT));
		player.playSound(player.getLocation(), event.key(), category, event.volume(), event.pitch());
	}

	private void screen(Player player, ScreenCinematicEvent event) {
		ScreenEffectRequest request = new ScreenEffectRequest(event.screenId(), event.title(), event.timing());
		if (!screenEffects.show(player, request)) {
			throw new IllegalStateException("Unknown screen effect: " + event.screenId().value());
		}
	}
}
