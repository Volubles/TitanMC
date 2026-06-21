package com.voluble.titanMC.cells.config;

import com.voluble.titanMC.managers.ConfigManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class CellsConfigurationManager implements ConfigManager.ComponentConfigManager {
	private final Plugin plugin;
	private final Path path;
	private final AtomicReference<CellsConfiguration> current = new AtomicReference<>();

	public CellsConfigurationManager(Plugin plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.path = plugin.getDataFolder().toPath().resolve("cells.yml");
	}

	@Override public void initialize() {
		try {
			Files.createDirectories(path.getParent());
			if (Files.notExists(path)) {
				try (InputStream source = plugin.getResource("cells.yml")) {
					if (source == null) throw new IllegalStateException("Missing bundled cells.yml");
					Files.copy(source, path, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		} catch (Exception exception) { throw new IllegalStateException("Could not initialize cells.yml", exception); }
		reload();
	}

	@Override public void reload() {
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
		current.set(CellsConfiguration.load(yaml));
	}

	public CellsConfiguration current() { return current.get(); }
}
