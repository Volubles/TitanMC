package com.voluble.titanMC.display.message;

import net.kyori.adventure.text.Component;

import java.util.Objects;

public record DisplayLine(Component component, DisplayLineAlignment alignment) {
	public DisplayLine {
		Objects.requireNonNull(component, "component");
		Objects.requireNonNull(alignment, "alignment");
	}

	public static DisplayLine left(Component component) {
		return new DisplayLine(component, DisplayLineAlignment.LEFT);
	}

	public static DisplayLine centered(Component component) {
		return new DisplayLine(component, DisplayLineAlignment.CENTER);
	}
}
