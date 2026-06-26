package com.voluble.titanMC.display.chat.model;

import java.util.Objects;

public record ChatClickBinding(ChatClickAction action, String value) {
	public ChatClickBinding {
		Objects.requireNonNull(action, "action");
		Objects.requireNonNull(value, "value");
		if (value.isBlank()) throw new IllegalArgumentException("click value must not be blank");
	}
}
