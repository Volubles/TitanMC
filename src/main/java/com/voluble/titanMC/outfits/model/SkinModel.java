package com.voluble.titanMC.outfits.model;

import java.util.Locale;
import java.util.Objects;

public enum SkinModel {
	CLASSIC,
	SLIM;

	public static SkinModel parse(String value) {
		String normalized = Objects.requireNonNull(value, "value").trim().toUpperCase(Locale.ROOT);
		for (SkinModel model : values()) {
			if (model.name().equals(normalized)) return model;
		}
		throw new IllegalArgumentException("Unknown skin model: " + value);
	}
}
