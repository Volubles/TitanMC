package com.voluble.titanMC.outfits.model;

import java.util.Locale;
import java.util.Objects;

public enum OutfitRenderMode {
	COMPOSITE,
	FULL_SKIN;

	public static OutfitRenderMode parse(String value) {
		String normalized = Objects.requireNonNull(value, "value")
			.trim()
			.replace('-', '_')
			.toUpperCase(Locale.ROOT);
		for (OutfitRenderMode mode : values()) {
			if (mode.name().equals(normalized)) return mode;
		}
		throw new IllegalArgumentException("Unknown outfit render mode: " + value);
	}
}
