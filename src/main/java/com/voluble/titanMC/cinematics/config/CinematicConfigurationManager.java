package com.voluble.titanMC.cinematics.config;

import com.voluble.titanMC.cinematics.model.CameraPoint;
import com.voluble.titanMC.cinematics.model.CinematicId;
import com.voluble.titanMC.managers.ConfigManager;
import com.voluble.titanMC.util.ComponentFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class CinematicConfigurationManager implements ConfigManager.ComponentConfigManager {
	private final Plugin plugin;
	private final Path path;
	private final AtomicReference<CinematicConfiguration> current = new AtomicReference<>();

	public CinematicConfigurationManager(Plugin plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.path = ComponentFiles.resolve(plugin.getDataFolder().toPath(), "cinematics", "cinematics.yml");
	}

	@Override
	public void initialize() {
		try {
			if (Files.notExists(path)) {
				try (InputStream source = plugin.getResource("cinematics/cinematics.yml")) {
					if (source == null) throw new IllegalStateException("Missing bundled cinematics/cinematics.yml");
					Files.copy(source, path, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			reload();
		} catch (Exception exception) {
			throw new IllegalStateException("Could not initialize cinematics/cinematics.yml", exception);
		}
	}

	@Override
	public void reload() {
		current.set(CinematicConfiguration.load(YamlConfiguration.loadConfiguration(path.toFile())));
	}

	public CinematicConfiguration current() {
		return Objects.requireNonNull(current.get(), "cinematic configuration has not been initialized");
	}

	public void createIfMissing(CinematicId id) {
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
		String base = "cinematics." + id.value();
		if (yaml.isConfigurationSection(base)) return;
		yaml.set(base + ".duration-ticks", 80);
		yaml.set(base + ".camera.restore-player", current().defaultRestorePlayer());
		yaml.set(base + ".camera.points", List.of(
			Map.of("tick", 0, "world", "world", "x", 0.5, "y", 80.0, "z", 0.5, "yaw", 0.0, "pitch", 0.0),
			Map.of("tick", 80, "world", "world", "x", 0.5, "y", 80.0, "z", 0.5, "yaw", 0.0, "pitch", 0.0)
		));
		save(yaml);
		reload();
	}

	public void appendCameraPoint(CinematicId id, CameraPoint point) {
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
		String base = "cinematics." + id.value();
		if (!yaml.isConfigurationSection(base)) createIfMissing(id);
		yaml = YamlConfiguration.loadConfiguration(path.toFile());
		List<Map<?, ?>> points = yaml.getMapList(base + ".camera.points");
		points.add(Map.of(
			"tick", point.tick(),
			"world", point.world(),
			"x", point.x(),
			"y", point.y(),
			"z", point.z(),
			"yaw", point.yaw(),
			"pitch", point.pitch()
		));
		yaml.set(base + ".duration-ticks", Math.max(yaml.getInt(base + ".duration-ticks", 0), point.tick()));
		yaml.set(base + ".camera.points", points);
		save(yaml);
		reload();
	}

	public int nextPointTick(CinematicId id) {
		return current().find(id)
			.map(definition -> definition.camera().points().stream()
				.mapToInt(CameraPoint::tick)
				.max()
				.orElse(0) + current().defaultPointDurationTicks())
			.orElse(0);
	}

	private void save(YamlConfiguration yaml) {
		try {
			yaml.save(path.toFile());
		} catch (Exception exception) {
			throw new IllegalStateException("Could not save cinematics/cinematics.yml", exception);
		}
	}
}
