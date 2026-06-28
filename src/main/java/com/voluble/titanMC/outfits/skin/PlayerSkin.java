package com.voluble.titanMC.outfits.skin;

import com.voluble.titanMC.outfits.model.SkinModel;

import java.net.URL;
import java.util.Objects;

public record PlayerSkin(URL url, SkinModel model) {
	public PlayerSkin {
		Objects.requireNonNull(url, "url");
		Objects.requireNonNull(model, "model");
	}
}
