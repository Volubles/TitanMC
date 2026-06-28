package com.voluble.titanMC.display.dialogue.titan;

import java.util.Objects;

record TitanDialogueImage(
	String key,
	String file,
	boolean arrow,
	int reductionRatio,
	int ascent
) {
	TitanDialogueImage {
		key = clean(key, "image key");
		file = clean(file, "image file");
		if (reductionRatio <= 0) throw new IllegalArgumentException("reduction ratio must be positive");
	}

	private static String clean(String value, String label) {
		Objects.requireNonNull(value, label);
		String cleaned = value.trim();
		if (cleaned.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
		return cleaned;
	}
}
