package com.voluble.titanMC.cells.region;

import com.voluble.titanMC.cells.model.CellDefinition;
import com.voluble.titanMC.regions.model.BlockBox;
import com.voluble.titanMC.regions.model.CuboidGeometry;
import com.voluble.titanMC.regions.model.RegionAccessSet;
import com.voluble.titanMC.regions.model.RegionDefinition;
import com.voluble.titanMC.regions.model.RegionKey;
import com.voluble.titanMC.regions.model.WorldId;
import com.voluble.titanMC.regions.service.RegionEngine;
import com.voluble.titanMC.regions.service.RegionMutationResult;
import com.voluble.titanMC.util.RegionUtils;

import java.util.Objects;

public final class CellRegionService {
	public static final int PRIORITY = 200;
	private final RegionEngine regions;

	public CellRegionService(RegionEngine regions) { this.regions = Objects.requireNonNull(regions, "regions"); }

	public void reconcile(CellDefinition cell) {
		RegionDefinition existing = find(cell);
		RegionMutationResult result;
		if (existing == null) {
			result = regions.create(key(cell.id()), world(cell), PRIORITY, geometry(cell.cuboid())).join();
		} else if (!existing.worldId().equals(world(cell)) || existing.priority() != PRIORITY || !existing.geometry().equals(geometry(cell.cuboid()))) {
			result = regions.update(existing.id(), existing.revision(), existing.key(), world(cell), PRIORITY, geometry(cell.cuboid())).join();
		} else return;
		requireSuccess(result);
	}

	public void delete(CellDefinition cell) {
		RegionDefinition existing = find(cell);
		if (existing != null) requireSuccess(regions.delete(existing.id(), existing.revision()).join());
	}

	public void setAccess(CellDefinition cell, RegionAccessSet access) {
		RegionDefinition existing = Objects.requireNonNull(find(cell), "Missing cell region: " + cell.id());
		requireSuccess(regions.setAccess(existing.id(), existing.revision(), access).join());
	}

	public RegionDefinition find(CellDefinition cell) { return regions.find(world(cell), key(cell.id())); }
	private static RegionKey key(String id) { return RegionKey.of(CellProtectionPolicy.NAMESPACE, id); }
	private static WorldId world(CellDefinition cell) { return new WorldId(cell.cuboid().worldId); }
	private static CuboidGeometry geometry(RegionUtils.Cuboid c) { return new CuboidGeometry(BlockBox.inclusive(c.minX,c.minY,c.minZ,c.maxX,c.maxY,c.maxZ)); }
	private static void requireSuccess(RegionMutationResult result) { if (result instanceof RegionMutationResult.Failure f) throw new IllegalStateException("Cell region operation failed: " + f.message()); }
}
