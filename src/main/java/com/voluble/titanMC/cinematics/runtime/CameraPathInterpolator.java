package com.voluble.titanMC.cinematics.runtime;

import com.voluble.titanMC.cinematics.model.CameraPoint;
import org.bukkit.Location;

import java.util.List;

public final class CameraPathInterpolator {
	private CameraPathInterpolator() {
	}

	public static Location locationAt(List<CameraPoint> points, int frame) {
		if (points.isEmpty()) throw new IllegalArgumentException("points must not be empty");
		if (frame <= points.getFirst().tick()) return points.getFirst().toLocation();
		for (int index = 1; index < points.size(); index++) {
			CameraPoint previous = points.get(index - 1);
			CameraPoint next = points.get(index);
			if (frame <= next.tick()) return interpolate(previous, next, frame);
		}
		return points.getLast().toLocation();
	}

	private static Location interpolate(CameraPoint from, CameraPoint to, int frame) {
		int duration = Math.max(1, to.tick() - from.tick());
		double progress = Math.max(0.0, Math.min(1.0, (frame - from.tick()) / (double) duration));
		Location start = from.toLocation();
		Location end = to.toLocation();
		if (!start.getWorld().equals(end.getWorld())) return progress < 1.0 ? start : end;
		return new Location(
			start.getWorld(),
			lerp(start.getX(), end.getX(), progress),
			lerp(start.getY(), end.getY(), progress),
			lerp(start.getZ(), end.getZ(), progress),
			lerpYaw(start.getYaw(), end.getYaw(), progress),
			(float) lerp(start.getPitch(), end.getPitch(), progress)
		);
	}

	private static double lerp(double from, double to, double progress) {
		return from + (to - from) * progress;
	}

	private static float lerpYaw(float from, float to, double progress) {
		float delta = ((((to - from) % 360) + 540) % 360) - 180;
		return (float) (from + delta * progress);
	}
}
