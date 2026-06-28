package com.voluble.titanMC.onboarding.config;

import com.voluble.titanMC.managers.ConfigManager;
import com.voluble.titanMC.util.ComponentFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class OnboardingConfigurationManager implements ConfigManager.ComponentConfigManager {
	private final Plugin plugin;
	private final Path path;
	private final AtomicReference<OnboardingConfiguration> current = new AtomicReference<>();

	public OnboardingConfigurationManager(Plugin plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.path = ComponentFiles.resolve(plugin.getDataFolder().toPath(), "onboarding", "onboarding.yml");
	}

	@Override
	public void initialize() {
		try {
			if (Files.notExists(path)) {
				try (InputStream source = plugin.getResource("onboarding/onboarding.yml")) {
					if (source == null) throw new IllegalStateException("Missing bundled onboarding/onboarding.yml");
					Files.copy(source, path, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			reload();
		} catch (Exception exception) {
			throw new IllegalStateException("Could not initialize onboarding/onboarding.yml", exception);
		}
	}

	@Override
	public void reload() {
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
		if (OnboardingYamlSynchronizer.sync(yaml)) save(yaml);
		current.set(OnboardingConfiguration.load(yaml));
	}

	public OnboardingConfiguration current() {
		return Objects.requireNonNull(current.get(), "onboarding configuration has not been initialized");
	}

	public void savePreviewPoint(OnboardingPreviewPoint point, Location location) {
		Objects.requireNonNull(point, "point");
		Objects.requireNonNull(location, "location");
		OnboardingConfiguration.LocationSpec spec = OnboardingConfiguration.LocationSpec.from(location);
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
		String base = point.configPath();
		yaml.set(base + ".world", spec.world());
		yaml.set(base + ".x", spec.x());
		yaml.set(base + ".y", spec.y());
		yaml.set(base + ".z", spec.z());
		yaml.set(base + ".yaw", spec.yaw());
		yaml.set(base + ".pitch", spec.pitch());
		save(yaml);
		reload();
	}

	private void save(YamlConfiguration yaml) {
		try {
			yaml.save(path.toFile());
		} catch (Exception exception) {
			throw new IllegalStateException("Could not save onboarding/onboarding.yml", exception);
		}
	}
}
