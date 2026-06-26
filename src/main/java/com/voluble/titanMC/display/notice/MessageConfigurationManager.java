package com.voluble.titanMC.display.notice;

import com.voluble.titanMC.managers.ConfigManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class MessageConfigurationManager implements ConfigManager.ComponentConfigManager {
	private static final String RESOURCE = "messages.yml";

	private final Plugin plugin;
	private final Path path;
	private final List<MessageDefinition> defaults;
	private final AtomicReference<MessageCatalog> current = new AtomicReference<>();

	public MessageConfigurationManager(Plugin plugin, List<MessageDefinition> defaults) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.defaults = List.copyOf(Objects.requireNonNull(defaults, "defaults"));
		this.path = plugin.getDataFolder().toPath().resolve(RESOURCE);
	}

	@Override
	public void initialize() {
		try {
			Files.createDirectories(path.getParent());
			if (Files.notExists(path)) {
				try (InputStream source = plugin.getResource(RESOURCE)) {
					if (source == null) throw new IllegalStateException("Missing bundled " + RESOURCE);
					Files.copy(source, path, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			reload();
		} catch (Exception exception) {
			throw new IllegalStateException("Could not initialize " + RESOURCE, exception);
		}
	}

	@Override
	public void reload() {
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
		if (MessageYamlSynchronizer.sync(yaml, defaults)) save(yaml);
		current.set(MessageCatalog.load(yaml));
	}

	public MessageCatalog current() {
		return Objects.requireNonNull(current.get(), "message configuration has not been initialized");
	}

	public PluginMessageService service() {
		return new PluginMessageService(this, new MessageRenderer());
	}

	private void save(YamlConfiguration yaml) {
		try {
			yaml.save(path.toFile());
		} catch (Exception exception) {
			throw new IllegalStateException("Could not save " + RESOURCE, exception);
		}
	}
}
