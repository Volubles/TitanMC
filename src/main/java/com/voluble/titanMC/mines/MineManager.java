package com.voluble.titanMC.mines;

import com.voluble.titanMC.TitanMC;
import com.voluble.titanMC.mines.regions.MineRegionService;
import com.voluble.titanMC.mines.storage.MineStorage;
import com.voluble.titanMC.mines.template.MineTemplateService;
import com.voluble.titanMC.mines.breaking.MineBreakProfile;
import com.voluble.titanMC.util.RegionUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.*;

public final class MineManager implements MineLookup {

	private final Plugin plugin;
	private final MineStorage storage;
	private final MineRegionService regions;
	private final MineTemplateService templates;
	private final Map<String, Mine> minesByName = new LinkedHashMap<>();
	private final Map<RegionUtils.Cuboid, Mine> cuboidToMine = new HashMap<>();
	private final RegionUtils.RegionIndex regionIndex = new RegionUtils.RegionIndex();

	public MineManager(Plugin plugin, MineRegionService regions) {
		this.plugin = Objects.requireNonNull(plugin);
		this.storage = new MineStorage(plugin);
		this.regions = Objects.requireNonNull(regions, "regions");
		this.templates = new MineTemplateService(plugin, plugin.getDataFolder().toPath().resolve("mines").resolve("templates"));
	}

	public void load() {
		minesByName.clear();
		cuboidToMine.clear();
		regionIndex.clear();
		// Storage
		Map<String, Mine> loaded = storage.loadAll();
		for (Mine mine : loaded.values()) {
			if (mine.getResetDefinition() instanceof MineResetDefinition.Template template
				&& !templates.storage().exists(template.templateId())) {
				throw new IllegalStateException(
					"Mine " + mine.getName() + " references missing template " + template.templateId()
				);
			}
		}
		regions.reconcile(loaded.values());
		for (Mine mine : loaded.values()) {
			registerInternal(mine);
		}
	}

	public void close() {
		templates.close();
		storage.saveAll(minesByName.values());
		storage.close();
	}

	public MineTemplateService templates() { return templates; }

	public Collection<Mine> getAll() { return Collections.unmodifiableCollection(minesByName.values()); }

	public Mine get(String name) { return minesByName.get(name); }

	public boolean exists(String name) { return minesByName.containsKey(name); }

	public void add(Mine mine) {
		if (mine == null) return;
		if (minesByName.containsKey(mine.getName())) throw new IllegalArgumentException("Mine already exists: " + mine.getName());
		Mine overlap = findOverlap(mine.getCuboid(), null);
		if (overlap != null) throw new IllegalArgumentException("Mine overlaps: " + overlap.getName());
		regions.create(mine);
		registerInternal(mine);
		storage.saveMine(mine);
	}

	public boolean delete(String name) {
		Mine mine = minesByName.get(name);
		if (mine == null) return false;
		regions.delete(mine);
		minesByName.remove(name);
		unindex(mine);
		storage.deleteMine(name);
		TitanMC.getInstance().getMineScheduler().cancelReset(name);
		templates.cancel(name);
		return true;
	}

	public void setCuboid(String name, RegionUtils.Cuboid newCuboid) {
		Mine mine = requireMine(name);
		if (mine.getResetDefinition() instanceof MineResetDefinition.Template) {
			throw new IllegalStateException("Switch this mine to palette reset before redefining its region");
		}
		Mine overlap = findOverlap(newCuboid, name);
		if (overlap != null) throw new IllegalArgumentException("Mine overlaps: " + overlap.getName());
		regions.redefine(mine, newCuboid);
		TitanMC.getInstance().getMineScheduler().cancelReset(name);
		unindex(mine);
		mine.setCuboid(newCuboid);
		mine.resetDepletionCounters();
		index(mine);
		storage.saveMine(mine);
	}

	public void setInterval(String name, int seconds) {
		Mine mine = requireMine(name);
		mine.setResetIntervalSeconds(seconds);
		// Reschedule the next reset to use the new interval
		mine.scheduleNextAfterInterval();
		storage.saveMine(mine);
	}

	public void setBatchPerTick(String name, int batch) {
		Mine mine = requireMine(name);
		mine.setBatchSizePerTick(batch);
		storage.saveMine(mine);
	}

	public void setCredMultiplier(String name, double multiplier) {
		Mine mine = requireMine(name);
		mine.setCredMultiplier(multiplier);
		storage.saveMine(mine);
	}

	public void setEnabled(String name, boolean enabled) {
		Mine mine = requireMine(name);
		mine.setEnabled(enabled);
		storage.saveMine(mine);
		if (!enabled) {
			TitanMC.getInstance().getMineScheduler().cancelReset(name);
		} else {
			// If re-enabling, reschedule the next reset time
			mine.scheduleNextAfterInterval();
			storage.saveMine(mine);
		}
	}

	public void paletteAddOrUpdate(String name, Material material, int weight) {
		Mine mine = requireMine(name);
		mine.getPalette().addOrUpdate(material, weight);
		disableInvalidDepletion(mine);
		storage.saveMine(mine);
	}

	public void paletteRemove(String name, Material material) {
		Mine mine = requireMine(name);
		mine.getPalette().remove(material);
		disableInvalidDepletion(mine);
		storage.saveMine(mine);
	}

	public void setSafeSpawn(String name, Location location) {
		Mine mine = requireMine(name);
		mine.setSafeSpawn(location);
		storage.saveMine(mine);
	}

	public void setDepletionThreshold(String name, int percent) {
		Mine mine = requireMine(name);
		if (percent >= 0 && !supportsDepletion(mine)) {
			throw new IllegalStateException("Depletion reset requires a palette mine where every generated material is diggable");
		}
		mine.setAutoResetBelowPercent(percent);
		storage.saveMine(mine);
	}

	public void useUnrestrictedBreaking(String name) {
		Mine mine = requireMine(name);
		mine.setBreakProfile(new MineBreakProfile.Unrestricted());
		storage.saveMine(mine);
	}

	public void useDiggableAllowList(String name) {
		Mine mine = requireMine(name);
		mine.setBreakProfile(new MineBreakProfile.AllowList(Set.of()));
		disableInvalidDepletion(mine);
		storage.saveMine(mine);
	}

	public void addDiggableMaterial(String name, Material material) {
		Mine mine = requireMine(name);
		MineBreakProfile.AllowList allowList = requireAllowList(mine);
		mine.setBreakProfile(allowList.with(material));
		disableInvalidDepletion(mine);
		storage.saveMine(mine);
	}

	public void removeDiggableMaterial(String name, Material material) {
		Mine mine = requireMine(name);
		MineBreakProfile.AllowList allowList = requireAllowList(mine);
		mine.setBreakProfile(allowList.without(material));
		disableInvalidDepletion(mine);
		storage.saveMine(mine);
	}

	public boolean supportsDepletion(Mine mine) {
		if (!(mine.getResetDefinition() instanceof MineResetDefinition.Palette)) return false;
		if (mine.getBreakProfile() instanceof MineBreakProfile.Unrestricted) return true;
		MineBreakProfile.AllowList allowList = (MineBreakProfile.AllowList) mine.getBreakProfile();
		return allowList.materials().containsAll(mine.getPalette().getEntriesView().keySet());
	}

	public List<Mine> getAt(Location location) {
		List<Mine> list = new ArrayList<>();
		for (RegionUtils.Cuboid c : regionIndex.getAllAt(location)) {
			Mine mine = cuboidToMine.get(c);
			if (mine != null) list.add(mine);
		}
		return list;
	}

	public Mine getFirstAt(Location location) {
		RegionUtils.Cuboid cuboid = regionIndex.getFirstAt(location);
		return cuboid == null ? null : cuboidToMine.get(cuboid);
	}

	public void setTemplateReset(String name, String templateId) {
		Mine mine = requireMine(name);
		String normalized = MineResetDefinition.normalizeTemplateId(templateId);
		if (!templates.storage().exists(normalized)) throw new IllegalArgumentException("Unknown mine template: " + normalized);
		TitanMC.getInstance().getMineScheduler().cancelReset(name);
		mine.setResetDefinition(new MineResetDefinition.Template(normalized));
		mine.setAutoResetBelowPercent(-1);
		mine.resetDepletionCounters();
		storage.saveMine(mine);
	}

	private void disableInvalidDepletion(Mine mine) {
		if (!supportsDepletion(mine)) mine.setAutoResetBelowPercent(-1);
	}

	private static MineBreakProfile.AllowList requireAllowList(Mine mine) {
		if (mine.getBreakProfile() instanceof MineBreakProfile.AllowList allowList) return allowList;
		throw new IllegalStateException("Enable the diggable allowlist before editing materials");
	}

	public void setPaletteReset(String name) {
		Mine mine = requireMine(name);
		TitanMC.getInstance().getMineScheduler().cancelReset(name);
		mine.setResetDefinition(new MineResetDefinition.Palette());
		mine.resetDepletionCounters();
		storage.saveMine(mine);
	}

	public void completeReset(String name) {
		Mine mine = minesByName.get(name);
		if (mine == null) return;
		mine.scheduleNextAfterInterval();
		mine.resetDepletionCounters();
		storage.saveMine(mine);
	}

	public Mine findOverlap(RegionUtils.Cuboid cuboid, String excludedName) {
		for (Mine mine : minesByName.values()) {
			if (excludedName != null && mine.getName().equals(excludedName)) continue;
			if (mine.getCuboid().intersects(cuboid)) return mine;
		}
		return null;
	}

	private Mine requireMine(String name) {
		Mine mine = minesByName.get(name);
		if (mine == null) throw new IllegalArgumentException("Unknown mine: " + name);
		return mine;
	}

	private void registerInternal(Mine mine) {
		minesByName.put(mine.getName(), mine);
		index(mine);
	}

	private void index(Mine mine) {
		cuboidToMine.put(mine.getCuboid(), mine);
		regionIndex.add(mine.getCuboid());
	}

	private void unindex(Mine mine) {
		regionIndex.remove(mine.getCuboid());
		cuboidToMine.remove(mine.getCuboid());
	}
}


