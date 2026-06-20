package com.voluble.titanMC.regions.model;

import java.time.Instant;
import java.util.Objects;

public record RegionDefinition(
	RegionId id,
	RegionKey key,
	WorldId worldId,
	int priority,
	RegionGeometry geometry,
	Instant createdAt,
	Instant updatedAt,
	long revision
) {

	public RegionDefinition {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(worldId, "worldId");
		Objects.requireNonNull(geometry, "geometry");
		Objects.requireNonNull(createdAt, "createdAt");
		Objects.requireNonNull(updatedAt, "updatedAt");
		if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt must not precede createdAt");
		if (revision < 1L) throw new IllegalArgumentException("revision must be positive");
	}

	public RegionDefinition(
		RegionId id,
		RegionKey key,
		WorldId worldId,
		int priority,
		RegionGeometry geometry,
		Instant createdAt,
		Instant updatedAt
	) {
		this(id, key, worldId, priority, geometry, createdAt, updatedAt, 1L);
	}

	public static RegionDefinition create(RegionKey key, WorldId worldId, int priority, RegionGeometry geometry) {
		Instant now = Instant.now();
		return new RegionDefinition(RegionId.random(), key, worldId, priority, geometry, now, now, 1L);
	}

	public boolean contains(int x, int y, int z) {
		return geometry.contains(x, y, z);
	}

	public boolean intersects(BlockBox box) {
		return geometry.intersects(box);
	}
}
