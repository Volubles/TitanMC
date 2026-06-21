package com.voluble.titanMC.ranks.config;

import com.voluble.titanMC.managers.ConfigManager;
import com.voluble.titanMC.ranks.service.RankCatalog;
import com.voluble.titanMC.util.ComponentFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class RankConfigurationManager implements ConfigManager.ComponentConfigManager {
	private final Plugin plugin;
	private final Path path;
	private final AtomicReference<RankConfiguration> current = new AtomicReference<>();

	public RankConfigurationManager(Plugin plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		path = ComponentFiles.resolve(plugin.getDataFolder().toPath(), "ranks", "ranks.yml");
	}

	@Override
	public void initialize() {
		try {
			if (Files.notExists(path)) {
				try (InputStream source = plugin.getResource("ranks.yml")) {
					if (source == null) throw new IllegalStateException("Missing bundled ranks.yml");
					Files.copy(source, path, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			reload();
		} catch (Exception exception) {
			throw new IllegalStateException("Could not initialize ranks.yml", exception);
		}
	}

	@Override
	public void reload() {
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
		current.set(RankConfiguration.load(yaml));
	}

	public RankConfiguration current() {
		return Objects.requireNonNull(current.get(), "rank configuration has not been initialized");
	}

	public RankCatalog catalog() {
		return current().catalog();
	}
}
