package com.voluble.titanMC.cinematics.runtime.camera;

import org.bukkit.Location;

public interface CinematicCameraDriver {
	void start(Location location);

	void move(int frame, Location location);

	void stop();
}
