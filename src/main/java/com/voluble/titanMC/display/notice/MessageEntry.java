package com.voluble.titanMC.display.notice;

import java.util.Objects;

public record MessageEntry(MessageType type, MessageKey key, String text) {
	public MessageEntry {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(text, "text");
	}
}
