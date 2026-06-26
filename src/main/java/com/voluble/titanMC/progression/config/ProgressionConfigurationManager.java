package com.voluble.titanMC.progression.config;

import com.voluble.titanMC.managers.ConfigManager;
import com.voluble.titanMC.util.ComponentFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class ProgressionConfigurationManager implements ConfigManager.ComponentConfigManager {
	private final Plugin plugin;
	private final Path path;
	private final AtomicReference<ProgressionConfiguration> current = new AtomicReference<>();

	public ProgressionConfigurationManager(Plugin plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		path = ComponentFiles.resolve(plugin.getDataFolder().toPath(), "progression", "progression.yml");
	}

	@Override
	public void initialize() {
		try {
			if (Files.notExists(path)) {
				try (InputStream source = plugin.getResource("progression/progression.yml")) {
					if (source == null) throw new IllegalStateException("Missing bundled progression/progression.yml");
					Files.copy(source, path, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			reload();
		} catch (Exception exception) {
			throw new IllegalStateException("Could not initialize progression/progression.yml", exception);
		}
	}

	@Override
	public void reload() {
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
		current.set(ProgressionConfiguration.load(yaml));
	}

	public ProgressionConfiguration current() {
		return Objects.requireNonNull(current.get(), "progression configuration has not been initialized");
	}
}
