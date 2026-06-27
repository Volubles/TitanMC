package com.voluble.titanMC.cinematics.runtime;

import com.voluble.titanMC.cinematics.config.CinematicConfigurationManager;
import com.voluble.titanMC.cinematics.model.CinematicDefinition;
import com.voluble.titanMC.cinematics.model.CinematicId;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CinematicRuntime implements AutoCloseable {
	private final Plugin plugin;
	private final CinematicConfigurationManager configuration;
	private final Map<UUID, CinematicSession> sessions = new ConcurrentHashMap<>();

	public CinematicRuntime(Plugin plugin, CinematicConfigurationManager configuration) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.configuration = Objects.requireNonNull(configuration, "configuration");
	}

	public StartResult start(Player player, CinematicId id) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(id, "id");
		if (!configuration.current().enabled()) return StartResult.DISABLED;
		Optional<CinematicDefinition> definition = configuration.current().find(id);
		if (definition.isEmpty()) return StartResult.UNKNOWN;
		stop(player.getUniqueId(), true);
		CinematicSession session = new CinematicSession(plugin, player, definition.get(), sessions::remove);
		sessions.put(player.getUniqueId(), session);
		session.start();
		return StartResult.STARTED;
	}

	public boolean stop(UUID playerId, boolean restorePlayer) {
		CinematicSession session = sessions.remove(playerId);
		if (session == null) return false;
		session.stop(restorePlayer);
		return true;
	}

	public boolean active(UUID playerId) {
		return sessions.containsKey(playerId);
	}

	@Override
	public void close() {
		for (UUID playerId : java.util.List.copyOf(sessions.keySet())) {
			stop(playerId, true);
		}
	}

	public enum StartResult {
		STARTED,
		DISABLED,
		UNKNOWN
	}
}
