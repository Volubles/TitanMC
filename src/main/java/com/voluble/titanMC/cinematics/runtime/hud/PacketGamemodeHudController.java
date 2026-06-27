package com.voluble.titanMC.cinematics.runtime.hud;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChangeGameState;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

final class PacketGamemodeHudController implements CinematicHudController {
	private final Plugin plugin;
	private final Player player;
	private final GameMode originalGameMode;

	PacketGamemodeHudController(Plugin plugin, Player player) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.player = Objects.requireNonNull(player, "player");
		this.originalGameMode = player.getGameMode();
	}

	@Override
	public void apply() {
		send(com.github.retrooper.packetevents.protocol.player.GameMode.SPECTATOR);
	}

	@Override
	public void restore() {
		if (!player.isOnline()) return;
		send(toPacketGameMode(originalGameMode));
	}

	private void send(com.github.retrooper.packetevents.protocol.player.GameMode gameMode) {
		try {
			PacketEvents.getAPI().getPlayerManager().sendPacket(
				player,
				new WrapperPlayServerChangeGameState(
					WrapperPlayServerChangeGameState.Reason.CHANGE_GAME_MODE,
					gameMode.getId()
				)
			);
		} catch (Exception exception) {
			plugin.getLogger().warning("Failed to update cinematic HUD for " + player.getName() + ": " + exception.getMessage());
		}
	}

	private com.github.retrooper.packetevents.protocol.player.GameMode toPacketGameMode(GameMode gameMode) {
		return switch (gameMode) {
			case CREATIVE -> com.github.retrooper.packetevents.protocol.player.GameMode.CREATIVE;
			case ADVENTURE -> com.github.retrooper.packetevents.protocol.player.GameMode.ADVENTURE;
			case SPECTATOR -> com.github.retrooper.packetevents.protocol.player.GameMode.SPECTATOR;
			case SURVIVAL -> com.github.retrooper.packetevents.protocol.player.GameMode.SURVIVAL;
		};
	}
}
