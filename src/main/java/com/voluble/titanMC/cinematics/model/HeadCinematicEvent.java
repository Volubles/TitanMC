package com.voluble.titanMC.cinematics.model;

import java.util.Objects;

public record HeadCinematicEvent(
	int tick,
	int timelineSlot,
	int row,
	String material
) implements CinematicEvent {
	public HeadCinematicEvent {
		CommandCinematicEvent.validatePlacement(tick, timelineSlot, row);
		material = Objects.requireNonNull(material, "material").trim();
		if (material.isBlank()) throw new IllegalArgumentException("head material must not be blank");
	}

	@Override
	public CinematicEventType type() {
		return CinematicEventType.HEAD;
	}
}
