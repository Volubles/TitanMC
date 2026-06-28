package com.voluble.titanMC.cinematics.model;

import java.util.Locale;

public enum CinematicEventType {
	COMMAND,
	HEAD,
	PARTICLE,
	SCREEN,
	SOUND;

	public static CinematicEventType parse(String value) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException("cinematic event type is required");
		return valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
	}
}
