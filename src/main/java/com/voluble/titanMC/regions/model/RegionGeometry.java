package com.voluble.titanMC.regions.model;

public sealed interface RegionGeometry permits CuboidGeometry {

	BlockBox bounds();

	boolean contains(int x, int y, int z);

	boolean intersects(BlockBox box);

	int complexity();
}
