package com.voluble.titanMC.display.chat.model;

import java.util.Objects;
import java.util.Optional;

public record ChatFormatSegment(String text, Optional<String> hover, Optional<ChatClickBinding> click) {
	public static final ChatFormatSegment EMPTY = new ChatFormatSegment("", Optional.empty(), Optional.empty());

	public ChatFormatSegment {
		Objects.requireNonNull(text, "text");
		Objects.requireNonNull(hover, "hover");
		Objects.requireNonNull(click, "click");
		hover.ifPresent(value -> {
			if (value.isBlank()) throw new IllegalArgumentException("hover must not be blank if present");
		});
	}

	public static ChatFormatSegment text(String text) {
		return new ChatFormatSegment(Objects.requireNonNull(text, "text"), Optional.empty(), Optional.empty());
	}

	public boolean isEmpty() {
		return text.isEmpty() && hover.isEmpty() && click.isEmpty();
	}
}
