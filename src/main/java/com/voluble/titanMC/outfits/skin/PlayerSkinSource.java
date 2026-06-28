package com.voluble.titanMC.outfits.skin;

import com.voluble.titanMC.outfits.model.SkinModel;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.util.Objects;
import java.util.Optional;

public final class PlayerSkinSource {
	public Optional<PlayerSkin> skin(Player player) {
		Objects.requireNonNull(player, "player");
		PlayerTextures textures = player.getPlayerProfile().getTextures();
		URL skin = textures.getSkin();
		if (skin == null) return Optional.empty();
		return Optional.of(new PlayerSkin(skin, SkinModel.valueOf(textures.getSkinModel().name())));
	}
}
