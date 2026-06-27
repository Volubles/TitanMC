package com.voluble.titanMC.cinematics.model;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record CameraPathDefinition(boolean restorePlayer, List<CameraPoint> points) {
	public CameraPathDefinition {
		points = Objects.requireNonNull(points, "points").stream()
			.sorted(Comparator.comparingInt(CameraPoint::tick))
			.toList();
		if (points.isEmpty()) throw new IllegalArgumentException("camera path must contain at least one point");
	}
}
