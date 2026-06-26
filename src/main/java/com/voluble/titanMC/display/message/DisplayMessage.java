package com.voluble.titanMC.display.message;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record DisplayMessage(List<DisplayLine> lines) {
	public DisplayMessage {
		Objects.requireNonNull(lines, "lines");
		if (lines.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("lines must not contain null");
		}
		lines = List.copyOf(lines);
	}

	public static DisplayMessage of(DisplayLine... lines) {
		Objects.requireNonNull(lines, "lines");
		return new DisplayMessage(Arrays.asList(lines));
	}
}
