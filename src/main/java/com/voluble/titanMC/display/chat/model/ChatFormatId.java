package com.voluble.titanMC.display.chat.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record ChatFormatId(String value) {
	private static final Pattern PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

	public ChatFormatId {
		Objects.requireNonNull(value, "value");
	}

	public static ChatFormatId of(String raw) {
		String normalized = Objects.requireNonNull(raw, "raw").trim().toLowerCase(Locale.ROOT);
		if (!PATTERN.matcher(normalized).matches()) {
			throw new IllegalArgumentException("Invalid chat format id: '" + raw + "'");
		}
		return new ChatFormatId(normalized);
	}

	@Override
	public String toString() {
		return value;
	}
}
