package com.voluble.titanMC.display.notice;

import net.kyori.adventure.text.format.TextColor;

import java.util.Locale;
import java.util.Objects;

public enum MessageType {
	INFO("info", "#30bbf1"),
	SUCCESS("success", "#42d829"),
	ERROR("error", "#d43030");

	private final String configKey;
	private final TextColor color;

	MessageType(String configKey, String color) {
		this.configKey = configKey;
		this.color = TextColor.fromHexString(color);
	}

	public String configKey() {
		return configKey;
	}

	public TextColor color() {
		return color;
	}

	public static MessageType parse(String value) {
		String normalized = Objects.requireNonNull(value, "value").trim().toLowerCase(Locale.ROOT);
		for (MessageType type : values()) {
			if (type.configKey.equals(normalized)) return type;
		}
		throw new IllegalArgumentException("Unknown message type: " + value);
	}
}
