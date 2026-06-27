package com.voluble.titanMC.cinematics.model;

import java.util.Objects;

public record CinematicDefinition(
	CinematicId id,
	int durationTicks,
	CameraPathDefinition camera
) {
	public CinematicDefinition {
		Objects.requireNonNull(id, "id");
		if (durationTicks <= 0) throw new IllegalArgumentException("cinematic duration must be positive");
		Objects.requireNonNull(camera, "camera");
	}
}
