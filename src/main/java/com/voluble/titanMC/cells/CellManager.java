package com.voluble.titanMC.cells;

import com.voluble.titanMC.cells.model.CellDefinition;
import com.voluble.titanMC.cells.model.CellLease;
import com.voluble.titanMC.cells.model.TrackedCellBlock;
import com.voluble.titanMC.cells.persistence.CellStorage;
import com.voluble.titanMC.cells.region.CellRegionService;
import com.voluble.titanMC.regions.model.RegionAccessSet;
import com.voluble.titanMC.util.RegionUtils;
import org.bukkit.Location;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CellManager implements AutoCloseable {
	private final CellStorage storage;
	private final CellRegionService regions;
	private final Map<String, CellDefinition> cells = new LinkedHashMap<>();
	private final Map<String, CellLease> leases = new LinkedHashMap<>();
	private final Map<BlockKey, TrackedCellBlock> tracked = new LinkedHashMap<>();
	private final RegionUtils.RegionIndex index = new RegionUtils.RegionIndex();

	public CellManager(CellStorage storage, CellRegionService regions) { this.storage=Objects.requireNonNull(storage); this.regions=Objects.requireNonNull(regions); }
	public void load() throws SQLException {
		cells.clear(); cells.putAll(storage.loadCells()); leases.clear(); leases.putAll(storage.loadLeases()); tracked.clear(); index.clear();
		for (CellDefinition cell : cells.values()) { index.add(cell.cuboid()); regions.reconcile(cell); }
		for (TrackedCellBlock block : storage.loadBlocks()) tracked.put(BlockKey.of(block), block);
		for (CellLease lease : leases.values()) { CellDefinition cell=cells.get(lease.cellId()); if (cell!=null) regions.setAccess(cell, RegionAccessSet.of(java.util.Set.of(lease.ownerId()), java.util.Set.of())); }
	}
	public Collection<CellDefinition> cells() { return List.copyOf(cells.values()); }
	public CellDefinition get(String id) { return cells.get(id.toLowerCase(java.util.Locale.ROOT)); }
	public CellLease lease(String cellId) { return leases.get(cellId); }
	public CellDefinition at(Location location) { RegionUtils.Cuboid c=index.getFirstAt(location); if(c==null)return null; return cells.values().stream().filter(cell->cell.cuboid().equals(c)).findFirst().orElse(null); }
	public void create(CellDefinition cell) { if(cells.containsKey(cell.id()))throw new IllegalArgumentException("Cell already exists: "+cell.id()); for(CellDefinition other:cells.values())if(other.cuboid().intersects(cell.cuboid()))throw new IllegalArgumentException("Cell overlaps: "+other.id()); regions.reconcile(cell); storage.saveCell(cell).join(); cells.put(cell.id(),cell); index.add(cell.cuboid()); }
	public void delete(String id) { CellDefinition cell=Objects.requireNonNull(get(id),"Unknown cell: "+id); if(leases.containsKey(cell.id()))throw new IllegalStateException("Cannot delete a rented cell"); regions.delete(cell); storage.deleteCell(cell.id()).join(); cells.remove(cell.id()); index.remove(cell.cuboid()); }
	public CellLease beginLease(String cellId, UUID ownerId, boolean autoRenew) { CellDefinition cell=Objects.requireNonNull(get(cellId),"Unknown cell: "+cellId); if(!cell.enabled())throw new IllegalStateException("Cell is disabled"); if(leases.containsKey(cell.id()))throw new IllegalStateException("Cell is already rented"); long now=System.currentTimeMillis(); long generation=Math.max(1L, tracked.values().stream().filter(b->b.cellId().equals(cell.id())).mapToLong(TrackedCellBlock::leaseGeneration).max().orElse(0L)+1L); CellLease lease=new CellLease(cell.id(),ownerId,generation,now,now+cell.rentDurationSeconds()*1000L,autoRenew); storage.saveLease(lease).join(); regions.setAccess(cell,RegionAccessSet.of(java.util.Set.of(ownerId),java.util.Set.of())); leases.put(cell.id(),lease); return lease; }
	public void endLease(String cellId) { CellLease lease=Objects.requireNonNull(leases.get(cellId),"Cell is not rented"); CellDefinition cell=Objects.requireNonNull(cells.get(cellId)); regions.setAccess(cell,RegionAccessSet.empty()); storage.deleteLease(cellId).join(); leases.remove(cellId); }
	public void track(Collection<TrackedCellBlock> blocks) { if(blocks.isEmpty())return; for(TrackedCellBlock b:blocks)tracked.put(BlockKey.of(b),b); storage.addBlocks(blocks).exceptionally(error->{ for(TrackedCellBlock b:blocks)tracked.remove(BlockKey.of(b)); return null; }); }
	public boolean isTracked(CellDefinition cell, Location location) { CellLease lease=leases.get(cell.id()); TrackedCellBlock b=tracked.get(BlockKey.of(location)); return lease!=null&&b!=null&&b.cellId().equals(cell.id())&&b.leaseGeneration()==lease.generation(); }
	public List<TrackedCellBlock> tracked(CellLease lease) { return tracked.values().stream().filter(b->b.cellId().equals(lease.cellId())&&b.leaseGeneration()==lease.generation()).toList(); }
	public void untrack(Collection<TrackedCellBlock> blocks) { for(TrackedCellBlock b:blocks)tracked.remove(BlockKey.of(b)); storage.removeBlocks(blocks); }
	public CellStorage storage() { return storage; }
	@Override public void close() throws Exception { storage.close(); }
	private record BlockKey(UUID worldId,int x,int y,int z) { static BlockKey of(Location l){return new BlockKey(l.getWorld().getUID(),l.getBlockX(),l.getBlockY(),l.getBlockZ());} static BlockKey of(TrackedCellBlock b){return new BlockKey(b.worldId(),b.x(),b.y(),b.z());} }
}
