package com.voluble.titanMC.cinematics.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

public record CameraPoint(
	int tick,
	String world,
	double x,
	double y,
	double z,
	float yaw,
	float pitch
) {
	public CameraPoint {
		if (tick < 0) throw new IllegalArgumentException("camera point tick must not be negative");
		world = Objects.requireNonNull(world, "world").trim();
		if (world.isBlank()) throw new IllegalArgumentException("camera point world must not be blank");
	}

	public static CameraPoint at(int tick, Location location) {
		Objects.requireNonNull(location, "location");
		World world = Objects.requireNonNull(location.getWorld(), "location world");
		return new CameraPoint(
			tick,
			world.getName(),
			location.getX(),
			location.getY(),
			location.getZ(),
			location.getYaw(),
			location.getPitch()
		);
	}

	public Location toLocation() {
		World bukkitWorld = Bukkit.getWorld(world);
		if (bukkitWorld == null) throw new IllegalStateException("Unknown cinematic world: " + world);
		return new Location(bukkitWorld, x, y, z, yaw, pitch);
	}
}
