package com.voluble.titanMC.display.text;

import net.kyori.adventure.text.Component;

import java.util.Objects;

public final class CenteredText {
	public static final CenteredText DEFAULT = new CenteredText(
		ChatFontMetrics.VANILLA,
		ChatFontMetrics.DEFAULT_CHAT_CENTER_PX
	);

	private final ChatFontMetrics metrics;
	private final int centerPx;

	public CenteredText(ChatFontMetrics metrics, int centerPx) {
		this.metrics = Objects.requireNonNull(metrics, "metrics");
		if (centerPx <= 0) throw new IllegalArgumentException("centerPx must be positive");
		this.centerPx = centerPx;
	}

	public Component center(Component component) {
		Objects.requireNonNull(component, "component");
		int componentWidth = metrics.width(component);
		if (componentWidth <= 0) return component;
		int paddingPx = centerPx - (componentWidth / 2);
		int spaceWidth = metrics.spaceWidth();
		if (paddingPx <= 0 || spaceWidth <= 0) return component;
		int spaces = Math.max(0, paddingPx / spaceWidth);
		if (spaces == 0) return component;
		return Component.text(" ".repeat(spaces)).append(component);
	}
}
