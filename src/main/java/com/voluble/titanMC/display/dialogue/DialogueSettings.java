package com.voluble.titanMC.display.dialogue;

import java.util.Objects;

public record DialogueSettings(
	int range,
	String effect,
	boolean preventExit,
	boolean preventSkip,
	boolean npcFocus,
	boolean saveProgress
) {
	public DialogueSettings {
		if (range < 0) throw new IllegalArgumentException("dialogue range must not be negative");
		effect = clean(effect);
	}

	public static DialogueSettings defaults() {
		return new DialogueSettings(3, "Slowness", false, false, false, false);
	}

	private static String clean(String value) {
		Objects.requireNonNull(value, "effect");
		String cleaned = value.trim();
		if (cleaned.isBlank()) throw new IllegalArgumentException("dialogue effect must not be blank");
		return cleaned;
	}
}
