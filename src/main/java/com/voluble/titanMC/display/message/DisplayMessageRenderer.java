package com.voluble.titanMC.display.message;

import com.voluble.titanMC.display.text.CenteredText;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Objects;

public final class DisplayMessageRenderer {
	public static final DisplayMessageRenderer DEFAULT = new DisplayMessageRenderer(CenteredText.DEFAULT);

	private final CenteredText centeredText;

	public DisplayMessageRenderer(CenteredText centeredText) {
		this.centeredText = Objects.requireNonNull(centeredText, "centeredText");
	}

	public List<Component> render(DisplayMessage message) {
		Objects.requireNonNull(message, "message");
		return message.lines().stream()
			.map(this::render)
			.toList();
	}

	public Component render(DisplayLine line) {
		Objects.requireNonNull(line, "line");
		return switch (line.alignment()) {
			case LEFT -> line.component();
			case CENTER -> centeredText.center(line.component());
		};
	}
}
