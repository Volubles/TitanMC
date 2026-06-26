package com.voluble.titanMC.milestones.config;

import com.voluble.titanMC.managers.ConfigManager;
import com.voluble.titanMC.util.ComponentFiles;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class MilestoneConfigurationManager implements ConfigManager.ComponentConfigManager {
	private final Plugin plugin;
	private final Path path;
	private final AtomicReference<MilestoneConfiguration> current = new AtomicReference<>();

	public MilestoneConfigurationManager(Plugin plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.path = ComponentFiles.resolve(plugin.getDataFolder().toPath(), "milestones", "milestones.yml");
	}

	@Override
	public void initialize() {
		try {
			Files.createDirectories(path.getParent());
			if (Files.notExists(path)) {
				try (InputStream source = plugin.getResource("milestones/milestones.yml")) {
					if (source == null) throw new IllegalStateException("Missing bundled milestones/milestones.yml");
					Files.copy(source, path, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Could not initialize milestones.yml", exception);
		}
		reload();
	}

	@Override
	public void reload() {
		YamlConfiguration yaml = new YamlConfiguration();
		try {
			yaml.load(path.toFile());
		} catch (IOException | InvalidConfigurationException exception) {
			throw new IllegalStateException("Could not read milestones.yml", exception);
		}
		current.set(MilestoneConfiguration.load(yaml));
	}

	public MilestoneConfiguration current() {
		return Objects.requireNonNull(current.get(), "milestone configuration has not been initialized");
	}
}
