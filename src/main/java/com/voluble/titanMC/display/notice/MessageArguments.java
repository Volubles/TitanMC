package com.voluble.titanMC.display.notice;

import net.kyori.adventure.text.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MessageArguments {
	private final Map<String, Component> values = new LinkedHashMap<>();

	public MessageArguments plain(String key, Object value) {
		values.put(normalizeKey(key), Component.text(Objects.toString(value, "")));
		return this;
	}

	public MessageArguments component(String key, Component value) {
		values.put(normalizeKey(key), Objects.requireNonNull(value, "value"));
		return this;
	}

	Optional<Component> find(String key) {
		return Optional.ofNullable(values.get(key));
	}

	private static String normalizeKey(String key) {
		String normalized = Objects.requireNonNull(key, "key").trim();
		if (normalized.isEmpty()) throw new IllegalArgumentException("argument key must not be blank");
		return normalized;
	}
}
