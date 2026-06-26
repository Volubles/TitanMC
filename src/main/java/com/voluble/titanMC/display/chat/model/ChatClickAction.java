package com.voluble.titanMC.display.chat.model;

import java.util.Locale;

public enum ChatClickAction {
	RUN_COMMAND,
	SUGGEST_COMMAND,
	OPEN_URL,
	COPY_TO_CLIPBOARD;

	public static ChatClickAction parse(String raw) {
		if (raw == null) throw new IllegalArgumentException("click action must not be null");
		String normalized = raw.trim().toUpperCase(Locale.ROOT);
		try {
			return ChatClickAction.valueOf(normalized);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Unknown click action: '" + raw + "'");
		}
	}
}
