package com.voluble.titanMC.outfits.skin;

import java.util.Objects;

public record SkinPropertyData(String value, String signature) {
	public SkinPropertyData {
		value = require(value, "value");
		signature = require(signature, "signature");
	}

	private static String require(String value, String name) {
		String normalized = Objects.requireNonNull(value, name).trim();
		if (normalized.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
		return normalized;
	}
}
