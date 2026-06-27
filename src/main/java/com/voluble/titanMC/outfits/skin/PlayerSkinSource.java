package com.voluble.titanMC.outfits.skin;

import org.bukkit.entity.Player;

import java.net.URL;
import java.util.Objects;
import java.util.Optional;

public final class PlayerSkinSource {
	public Optional<URL> skinUrl(Player player) {
		Objects.requireNonNull(player, "player");
		return Optional.ofNullable(player.getPlayerProfile().getTextures().getSkin());
	}
}
