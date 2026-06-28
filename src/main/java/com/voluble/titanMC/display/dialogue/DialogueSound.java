package com.voluble.titanMC.display.dialogue;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import java.util.Locale;
import java.util.Objects;

public record DialogueSound(
	Key key,
	Sound.Source source,
	float volume,
	float pitch
) {
	public DialogueSound {
		key = Objects.requireNonNull(key, "key");
		source = Objects.requireNonNull(source, "source");
		if (volume < 0) throw new IllegalArgumentException("sound volume must not be negative");
		if (pitch < 0) throw new IllegalArgumentException("sound pitch must not be negative");
	}

	public Sound sound() {
		return Sound.sound(key, source, volume, pitch);
	}

	static Sound.Source source(String value) {
		if (value == null || value.isBlank()) return Sound.Source.MASTER;
		return Sound.Source.valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
	}
}
