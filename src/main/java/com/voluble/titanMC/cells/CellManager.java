package com.voluble.titanMC.cells;

import com.voluble.titanMC.cells.model.CellDefinition;
import com.voluble.titanMC.cells.model.CellLease;
import com.voluble.titanMC.cells.model.CellResetJob;
import com.voluble.titanMC.cells.model.TrackedCellBlock;
import com.voluble.titanMC.cells.persistence.CellStorage;
import com.voluble.titanMC.cells.region.CellRegionService;
import com.voluble.titanMC.regions.model.RegionAccessSet;
import com.voluble.titanMC.util.RegionUtils;
import org.bukkit.Location;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class CellManager implements AutoCloseable {
	private final CellStorage storage;
	private final CellRegionService regions;
	private final Map<String, CellDefinition> cells = new LinkedHashMap<>();
	private final Map<String, CellLease> leases = new LinkedHashMap<>();
	private final Map<RegionUtils.Cuboid, CellDefinition> byCuboid = new LinkedHashMap<>();
	private final Map<BlockKey, TrackedCellBlock> tracked = new LinkedHashMap<>();
	private final Map<String, CellResetJob> resetJobs = new LinkedHashMap<>();
	private final Map<String, java.util.Set<UUID>> members = new LinkedHashMap<>();
	private final Map<BlockKey, com.voluble.titanMC.cells.model.CellSign> signs = new LinkedHashMap<>();
	private final RegionUtils.RegionIndex index = new RegionUtils.RegionIndex();

	public CellManager(CellStorage storage, CellRegionService regions) {
		this.storage = Objects.requireNonNull(storage);
		this.regions = Objects.requireNonNull(regions);
	}

	public void load() throws SQLException {
		cells.clear();
		cells.putAll(storage.loadCells());
		leases.clear();
		leases.putAll(storage.loadLeases());
		members.clear();
		members.putAll(storage.loadMembers(leases));
		resetJobs.clear();
		resetJobs.putAll(storage.loadResetJobs());
		tracked.clear();
		signs.clear();
		for (var sign : storage.loadSigns())
			signs.put(new BlockKey(sign.worldId(), sign.x(), sign.y(), sign.z()), sign);
		index.clear();
		byCuboid.clear();
		for (CellDefinition cell : cells.values()) {
			index.add(cell.cuboid());
			byCuboid.put(cell.cuboid(), cell);
			regions.reconcile(cell);
		}
		for (TrackedCellBlock block : storage.loadBlocks()) tracked.put(BlockKey.of(block), block);
		for (CellLease lease : leases.values()) {
			CellDefinition cell = cells.get(lease.cellId());
			if (cell != null)
				regions.setAccess(cell, resetJobs.containsKey(cell.id()) ? RegionAccessSet.empty() : RegionAccessSet.of(java.util.Set.of(lease.ownerId()), members.getOrDefault(cell.id(), java.util.Set.of())));
		}
	}

	public Collection<CellDefinition> cells() {
		return List.copyOf(cells.values());
	}

	public CellDefinition get(String id) {
		return cells.get(id.toLowerCase(java.util.Locale.ROOT));
	}

	public CellLease lease(String cellId) {
		return leases.get(cellId);
	}

	public Collection<CellResetJob> resetJobs() {
		return List.copyOf(resetJobs.values());
	}

	public java.util.Set<UUID> members(String cellId) {
		return java.util.Set.copyOf(members.getOrDefault(cellId, java.util.Set.of()));
	}

	public Collection<com.voluble.titanMC.cells.model.CellSign> signs() {
		return List.copyOf(signs.values());
	}

	public void registerSign(com.voluble.titanMC.cells.model.CellSign sign) {
		Objects.requireNonNull(get(sign.cellId()), "Unknown cell: " + sign.cellId());
		storage.saveSign(sign).join();
		signs.put(new BlockKey(sign.worldId(), sign.x(), sign.y(), sign.z()), sign);
	}

	public void unregisterSign(com.voluble.titanMC.cells.model.CellSign sign) {
		storage.deleteSign(sign).join();
		signs.remove(new BlockKey(sign.worldId(), sign.x(), sign.y(), sign.z()));
	}

	public void setDisplayName(String cellId, String displayName) {
		CellDefinition old = Objects.requireNonNull(get(cellId), "Unknown cell: " + cellId);
		CellDefinition updated = new CellDefinition(old.id(), displayName, old.wardId(), old.cuboid(), old.rentPrice(), old.rentDurationSeconds(), old.maxRentDurationSeconds(), old.enabled());
		storage.saveCell(updated).join();
		cells.put(old.id(), updated);
		byCuboid.put(old.cuboid(), updated);
	}

	public void addMember(String cellId, UUID playerId) {
		CellLease lease = Objects.requireNonNull(leases.get(cellId), "Cell is not rented");
		if (lease.ownerId().equals(playerId)) return;
		java.util.Set<UUID> updated = new java.util.LinkedHashSet<>(members.getOrDefault(cellId, java.util.Set.of()));
		updated.add(playerId);
		storage.addMember(cellId, lease.generation(), playerId).join();
		members.put(cellId, updated);
		regions.setAccess(cells.get(cellId), RegionAccessSet.of(java.util.Set.of(lease.ownerId()), updated));
	}

	public void removeMember(String cellId, UUID playerId) {
		CellLease lease = Objects.requireNonNull(leases.get(cellId), "Cell is not rented");
		java.util.Set<UUID> updated = new java.util.LinkedHashSet<>(members.getOrDefault(cellId, java.util.Set.of()));
		updated.remove(playerId);
		storage.removeMember(cellId, lease.generation(), playerId).join();
		members.put(cellId, updated);
		regions.setAccess(cells.get(cellId), RegionAccessSet.of(java.util.Set.of(lease.ownerId()), updated));
	}

	public CellDefinition at(Location location) {
		RegionUtils.Cuboid c = index.getFirstAt(location);
		return c == null ? null : byCuboid.get(c);
	}

	public void create(CellDefinition cell) {
		if (cells.containsKey(cell.id())) throw new IllegalArgumentException("Cell already exists: " + cell.id());
		for (CellDefinition other : cells.values())
			if (other.cuboid().intersects(cell.cuboid()))
				throw new IllegalArgumentException("Cell overlaps: " + other.id());
		regions.reconcile(cell);
		storage.saveCell(cell).join();
		cells.put(cell.id(), cell);
		index.add(cell.cuboid());
		byCuboid.put(cell.cuboid(), cell);
	}

	public void delete(String id) {
		CellDefinition cell = Objects.requireNonNull(get(id), "Unknown cell: " + id);
		if (leases.containsKey(cell.id())) throw new IllegalStateException("Cannot delete a rented cell");
		regions.delete(cell);
		storage.deleteCell(cell.id()).join();
		cells.remove(cell.id());
		index.remove(cell.cuboid());
		byCuboid.remove(cell.cuboid());
	}

	public CellLease planLease(String cellId, UUID ownerId) {
		CellDefinition cell = Objects.requireNonNull(get(cellId), "Unknown cell: " + cellId);
		if (!cell.enabled()) throw new IllegalStateException("Cell is disabled");
		if (leases.containsKey(cell.id()) || resetJobs.containsKey(cell.id()))
			throw new IllegalStateException("Cell is not available");
		long now = System.currentTimeMillis();
		long generation = Math.max(1L, tracked.values().stream().filter(b -> b.cellId().equals(cell.id())).mapToLong(TrackedCellBlock::leaseGeneration).max().orElse(0L) + 1L);
		return new CellLease(cell.id(), ownerId, generation, now, now + cell.rentDurationSeconds() * 1000L);
	}

	public CompletableFuture<Void> persistLease(CellLease lease) {
		return storage.saveLease(lease);
	}

	public void activateLease(CellLease lease) {
		CellDefinition cell = Objects.requireNonNull(cells.get(lease.cellId()));
		if (leases.containsKey(cell.id())) throw new IllegalStateException("Cell became unavailable");
		regions.setAccess(cell, RegionAccessSet.of(java.util.Set.of(lease.ownerId()), java.util.Set.of()));
		members.remove(cell.id());
		leases.put(cell.id(), lease);
	}

	public void replaceLease(CellLease lease) {
		CellLease current = Objects.requireNonNull(leases.get(lease.cellId()), "Cell is not rented");
		if (current.generation() != lease.generation()) throw new IllegalStateException("Lease generation changed");
		leases.put(lease.cellId(), lease);
	}

	public CompletableFuture<Void> discardLease(CellLease lease) {
		return storage.deleteLease(lease.cellId());
	}

	public void endLease(String cellId) {
		CellLease lease = Objects.requireNonNull(leases.get(cellId), "Cell is not rented");
		CellDefinition cell = Objects.requireNonNull(cells.get(cellId));
		regions.setAccess(cell, RegionAccessSet.empty());
		storage.deleteLease(cellId).join();
		leases.remove(cellId);
	}

	public CellResetJob beginReset(String cellId) {
		if (resetJobs.containsKey(cellId)) throw new IllegalStateException("Cell reset is already running");
		CellLease lease = Objects.requireNonNull(leases.get(cellId), "Cell is not rented");
		CellDefinition cell = Objects.requireNonNull(cells.get(cellId));
		regions.setAccess(cell, RegionAccessSet.empty());
		storage.beginReset(lease).join();
		CellResetJob job = new CellResetJob(cellId, lease.generation(), lease.ownerId(), CellResetJob.Phase.COLLECTING, null);
		resetJobs.put(cellId, job);
		return job;
	}

	public void markPrepared(CellResetJob job, long lotId) {
		resetJobs.put(job.cellId(), new CellResetJob(job.cellId(), job.leaseGeneration(), job.ownerId(), CellResetJob.Phase.PREPARED, lotId));
	}

	public void completeReset(CellResetJob job, long lotId) {
		storage.completeReset(job.cellId(), job.leaseGeneration(), lotId).join();
		tracked.values().removeIf(b -> b.cellId().equals(job.cellId()) && b.leaseGeneration() == job.leaseGeneration());
		leases.remove(job.cellId());
		members.remove(job.cellId());
		resetJobs.remove(job.cellId());
	}

	public void track(Collection<TrackedCellBlock> blocks) {
		if (blocks.isEmpty()) return;
		for (TrackedCellBlock b : blocks) tracked.put(BlockKey.of(b), b);
		storage.addBlocks(blocks).exceptionally(error -> {
			for (TrackedCellBlock b : blocks) tracked.remove(BlockKey.of(b), b);
			return null;
		});
	}

	public boolean isTracked(CellDefinition cell, Location location) {
		CellLease lease = leases.get(cell.id());
		TrackedCellBlock b = tracked.get(BlockKey.of(location));
		return lease != null && b != null && b.cellId().equals(cell.id()) && b.leaseGeneration() == lease.generation();
	}

	public List<TrackedCellBlock> tracked(CellLease lease) {
		return tracked.values().stream().filter(b -> b.cellId().equals(lease.cellId()) && b.leaseGeneration() == lease.generation()).toList();
	}

	public void untrack(Collection<TrackedCellBlock> blocks) {
		for (TrackedCellBlock b : blocks) tracked.remove(BlockKey.of(b));
		storage.removeBlocks(blocks);
	}

	public CellStorage storage() {
		return storage;
	}

	@Override
	public void close() throws Exception {
		storage.close();
	}

	private record BlockKey(UUID worldId, int x, int y, int z) {
		static BlockKey of(Location l) {
			return new BlockKey(l.getWorld().getUID(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
		}

		static BlockKey of(TrackedCellBlock b) {
			return new BlockKey(b.worldId(), b.x(), b.y(), b.z());
		}
	}
}
