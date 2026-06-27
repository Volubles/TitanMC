package com.voluble.titanMC.outfits.config;

import com.voluble.titanMC.managers.ConfigManager;
import com.voluble.titanMC.outfits.skin.DefaultOutfitTemplates;
import com.voluble.titanMC.util.ComponentFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class OutfitConfigurationManager implements ConfigManager.ComponentConfigManager {
	private final Plugin plugin;
	private final Path folder;
	private final Path path;
	private final AtomicReference<OutfitConfiguration> current = new AtomicReference<>();

	public OutfitConfigurationManager(Plugin plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.folder = ComponentFiles.resolve(plugin.getDataFolder().toPath(), "outfits", "outfits.yml").getParent();
		this.path = folder.resolve("outfits.yml");
	}

	@Override
	public void initialize() {
		try {
			if (Files.notExists(path)) {
				try (InputStream source = plugin.getResource("outfits/outfits.yml")) {
					if (source == null) throw new IllegalStateException("Missing bundled outfits/outfits.yml");
					Files.copy(source, path, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			DefaultOutfitTemplates.ensure(folder);
			reload();
		} catch (Exception exception) {
			throw new IllegalStateException("Could not initialize outfits/outfits.yml", exception);
		}
	}

	@Override
	public void reload() {
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
		current.set(OutfitConfiguration.load(yaml, folder));
	}

	public OutfitConfiguration current() {
		return Objects.requireNonNull(current.get(), "outfit configuration has not been initialized");
	}
}
