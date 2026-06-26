package com.voluble.titanMC.mines.storage;

import com.voluble.titanMC.mines.Mine;
import com.voluble.titanMC.mines.MineResetDefinition;
import com.voluble.titanMC.mines.breaking.MineBreakProfile;
import com.voluble.titanMC.mines.WeightedPalette;
import com.voluble.titanMC.util.RegionUtils;
import com.voluble.titanMC.util.ComponentFiles;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class MineStorage implements AutoCloseable {

	private final Plugin plugin;
	private final File file;
	private final Object lock = new Object();
	private final Map<String, MineSnapshot> snapshots = new LinkedHashMap<>();
	private final ExecutorService writer;
	private boolean dirty;
	private boolean writeScheduled;
	private boolean closed;

	public MineStorage(Plugin plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.file = ComponentFiles.resolve(plugin.getDataFolder().toPath(), "mines", "mines.yml").toFile();
		this.writer = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "TitanMC-MineStorage");
			thread.setDaemon(true);
			return thread;
		});
	}

	public Map<String, Mine> loadAll() {
		FileConfiguration cfg = loadConfig();
		ConfigurationSection minesSec = cfg.getConfigurationSection("mines");
		Map<String, Mine> result = new LinkedHashMap<>();
		if (minesSec == null) return result;
		for (String name : minesSec.getKeys(false)) {
			ConfigurationSection s = minesSec.getConfigurationSection(name);
			if (s == null) continue;
			String worldStr = s.getString("world", null);
			UUID worldId;
			try {
				worldId = worldStr == null ? null : UUID.fromString(worldStr);
			} catch (IllegalArgumentException ex) {
				continue;
			}
			ConfigurationSection min = s.getConfigurationSection("min");
			ConfigurationSection max = s.getConfigurationSection("max");
			if (worldId == null || min == null || max == null) continue;
			int minX = min.getInt("x");
			int minY = min.getInt("y");
			int minZ = min.getInt("z");
			int maxX = max.getInt("x");
			int maxY = max.getInt("y");
			int maxZ = max.getInt("z");
			RegionUtils.Cuboid cuboid = new RegionUtils.Cuboid(worldId, minX, minY, minZ, maxX, maxY, maxZ);

			int interval = Math.max(1, s.getInt("interval_seconds", 900));
			int batch = Math.max(1, s.getInt("batch_per_tick", 1500));
			boolean enabled = s.getBoolean("enabled", true);
			int autoBelow = s.getInt("auto_reset_below_percent", -1);
			double credMultiplier = s.getDouble("progression.cred_multiplier", 1.0D);

			ConfigurationSection paletteSec = s.getConfigurationSection("palette");
			Map<String, Object> paletteMap = paletteSec != null ? paletteSec.getValues(false) : Collections.emptyMap();
			WeightedPalette palette = WeightedPalette.fromConfigMap(paletteMap);
			if (palette.isEmpty()) {
				// Ensure a reasonable default to avoid AIR-only mines
				palette.addOrUpdate(Material.STONE, 1);
			}

			Mine mine = new Mine(name, cuboid, interval, enabled, batch, palette);
			mine.setCredMultiplier(credMultiplier);
			String breakMode = s.getString("diggable.mode", "UNRESTRICTED");
			if ("ALLOW_LIST".equalsIgnoreCase(breakMode)) {
				Set<Material> materials = new LinkedHashSet<>();
				for (String value : s.getStringList("diggable.materials")) {
					Material material = Material.matchMaterial(value);
					if (material == null) throw new IllegalStateException("Unknown diggable material for mine " + name + ": " + value);
					materials.add(material);
				}
				mine.setBreakProfile(new MineBreakProfile.AllowList(materials));
			} else if (!"UNRESTRICTED".equalsIgnoreCase(breakMode)) {
				throw new IllegalStateException("Unknown diggable mode for mine " + name + ": " + breakMode);
			}
			String resetType = s.getString("reset.type", "PALETTE");
			if ("TEMPLATE".equalsIgnoreCase(resetType)) {
				mine.setResetDefinition(new MineResetDefinition.Template(s.getString("reset.template_id")));
			} else if (!"PALETTE".equalsIgnoreCase(resetType)) {
				throw new IllegalStateException("Unknown reset type for mine " + name + ": " + resetType);
			}
			mine.setAutoResetBelowPercent(autoBelow);
			mine.setBrokenBlocks(s.getInt("broken_blocks", 0));
			long nextReset = s.getLong("next_reset_epoch_ms", mine.getNextResetEpochMs());
			mine.setNextResetEpochMs(nextReset);
			ConfigurationSection safeSpawn = s.getConfigurationSection("safe_spawn");
			if (safeSpawn != null) {
				String safeWorldStr = safeSpawn.getString("world");
				if (safeWorldStr != null) {
					try {
						UUID safeWorldId = UUID.fromString(safeWorldStr);
						World safeWorld = Bukkit.getWorld(safeWorldId);
						if (safeWorld != null) {
							double x = safeSpawn.getDouble("x");
							double y = safeSpawn.getDouble("y");
							double z = safeSpawn.getDouble("z");
							float yaw = (float) safeSpawn.getDouble("yaw", 0);
							float pitch = (float) safeSpawn.getDouble("pitch", 0);
							mine.setSafeSpawn(new Location(safeWorld, x, y, z, yaw, pitch));
						}
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
			result.put(name, mine);
		}
		synchronized (lock) {
			snapshots.clear();
			for (Mine mine : result.values()) {
				snapshots.put(mine.getName(), MineSnapshot.from(mine));
			}
		}
		return result;
	}

	public void saveAll(Collection<Mine> mines) {
		synchronized (lock) {
			ensureOpen();
			snapshots.clear();
			if (mines != null) {
				for (Mine mine : mines) {
					MineSnapshot snapshot = MineSnapshot.from(mine);
					snapshots.put(snapshot.name(), snapshot);
				}
			}
			markDirty();
		}
	}

	public void saveMine(Mine mine) {
		MineSnapshot snapshot = MineSnapshot.from(Objects.requireNonNull(mine, "mine"));
		synchronized (lock) {
			ensureOpen();
			snapshots.put(snapshot.name(), snapshot);
			markDirty();
		}
	}

	public void deleteMine(String name) {
		synchronized (lock) {
			ensureOpen();
			snapshots.remove(name);
			markDirty();
		}
	}

	public void flush() {
		Future<?> barrier = writer.submit(() -> {});
		try {
			barrier.get();
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to flush mine storage", exception);
		}
	}

	@Override
	public void close() {
		synchronized (lock) {
			if (closed) return;
		}
		flush();
		synchronized (lock) {
			closed = true;
		}
		writer.shutdown();
	}

	private void markDirty() {
		dirty = true;
		if (writeScheduled) return;
		writeScheduled = true;
		writer.execute(this::drainWrites);
	}

	private void drainWrites() {
		while (true) {
			Map<String, MineSnapshot> pending;
			synchronized (lock) {
				if (!dirty) {
					writeScheduled = false;
					return;
				}
				dirty = false;
				pending = new LinkedHashMap<>(snapshots);
			}
			writeSnapshots(pending);
		}
	}

	private void writeSnapshots(Map<String, MineSnapshot> pending) {
		FileConfiguration cfg = new YamlConfiguration();
		ConfigurationSection minesSec = cfg.createSection("mines");
		for (MineSnapshot snapshot : pending.values()) {
			saveIntoSection(minesSec, snapshot);
		}
		writeConfig(cfg);
	}

	private void saveIntoSection(ConfigurationSection minesSec, MineSnapshot mine) {
		ConfigurationSection s = minesSec.createSection(mine.name());
		s.set("world", mine.worldId().toString());
		ConfigurationSection min = s.createSection("min");
		min.set("x", mine.minX());
		min.set("y", mine.minY());
		min.set("z", mine.minZ());
		ConfigurationSection max = s.createSection("max");
		max.set("x", mine.maxX());
		max.set("y", mine.maxY());
		max.set("z", mine.maxZ());
		s.set("interval_seconds", mine.intervalSeconds());
		s.set("batch_per_tick", mine.batchPerTick());
		s.set("enabled", mine.enabled());
		s.set("auto_reset_below_percent", mine.autoResetBelowPercent());
		s.set("progression.cred_multiplier", mine.credMultiplier());
		s.set("broken_blocks", mine.brokenBlocks());
		s.set("next_reset_epoch_ms", mine.nextResetEpochMs());
		s.set("reset.type", mine.resetType());
		s.set("reset.template_id", mine.templateId());
		s.set("diggable.mode", mine.breakMode());
		s.set("diggable.materials", mine.diggableMaterials().stream().sorted().toList());
		MineSnapshot.SafeSpawnSnapshot safeSpawn = mine.safeSpawn();
		if (safeSpawn != null) {
			ConfigurationSection safeSec = s.createSection("safe_spawn");
			safeSec.set("world", safeSpawn.worldId().toString());
			safeSec.set("x", safeSpawn.x());
			safeSec.set("y", safeSpawn.y());
			safeSec.set("z", safeSpawn.z());
			safeSec.set("yaw", safeSpawn.yaw());
			safeSec.set("pitch", safeSpawn.pitch());
		}
		ConfigurationSection palette = s.createSection("palette");
		for (Map.Entry<String, Integer> e : mine.palette().entrySet()) {
			palette.set(e.getKey(), e.getValue());
		}
	}

	private FileConfiguration loadConfig() {
		if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
		return YamlConfiguration.loadConfiguration(file);
	}

	private void writeConfig(FileConfiguration cfg) {
		File parent = file.getParentFile();
		if (!parent.exists() && !parent.mkdirs()) {
			plugin.getLogger().severe("Failed to create mine storage directory");
			return;
		}
		File temporary = new File(parent, file.getName() + ".tmp");
		try {
			cfg.save(temporary);
			try {
				Files.move(
					temporary.toPath(),
					file.toPath(),
					StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING
				);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			plugin.getLogger().severe("Failed to save mines.yml: " + e.getMessage());
			try {
				Files.deleteIfExists(temporary.toPath());
			} catch (IOException ignored) {
			}
		}
	}

	private void ensureOpen() {
		if (closed) throw new IllegalStateException("Mine storage is closed");
	}
}


