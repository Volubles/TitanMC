package com.voluble.titanMC.regions.model;

import java.util.Objects;

public record CuboidGeometry(BlockBox bounds) implements RegionGeometry {

	public CuboidGeometry {
		Objects.requireNonNull(bounds, "bounds");
	}

	@Override
	public boolean contains(int x, int y, int z) {
		return bounds.contains(x, y, z);
	}

	@Override
	public boolean intersects(BlockBox box) {
		return bounds.intersects(Objects.requireNonNull(box, "box"));
	}

	@Override
	public int complexity() {
		return 1;
	}
}
