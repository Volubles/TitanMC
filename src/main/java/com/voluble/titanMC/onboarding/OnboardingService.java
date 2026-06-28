package com.voluble.titanMC.onboarding;

import com.voluble.titanMC.cinematics.runtime.CinematicRuntime;
import com.voluble.titanMC.display.notice.MessageDefaults;
import com.voluble.titanMC.display.notice.PluginMessageService;
import com.voluble.titanMC.onboarding.config.OnboardingConfiguration;
import com.voluble.titanMC.onboarding.config.OnboardingConfigurationManager;
import com.voluble.titanMC.onboarding.config.OnboardingPreviewPoint;
import com.voluble.titanMC.onboarding.persistence.OnboardingStorage;
import com.voluble.titanMC.onboarding.presentation.OnboardingPresentationRunner;
import com.voluble.titanMC.onboarding.preview.OutfitPreview;
import com.voluble.titanMC.onboarding.readiness.OnboardingReadinessService;
import com.voluble.titanMC.outfits.OutfitService;
import com.voluble.titanMC.outfits.config.OutfitConfigurationManager;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OnboardingService implements AutoCloseable {
	private final Plugin plugin;
	private final OnboardingConfigurationManager configuration;
	private final OnboardingStorage storage;
	private final CinematicRuntime cinematics;
	private final OutfitService outfits;
	private final OutfitConfigurationManager outfitConfiguration;
	private final OutfitPreview preview;
	private final OnboardingReadinessService readiness;
	private final OnboardingPresentationRunner presentation;
	private final PluginMessageService messages;
	private final Logger logger;
	private final Map<UUID, OnboardingSession> sessions = new ConcurrentHashMap<>();

	public OnboardingService(
		Plugin plugin,
		OnboardingConfigurationManager configuration,
		OnboardingStorage storage,
		CinematicRuntime cinematics,
		OutfitService outfits,
		OutfitConfigurationManager outfitConfiguration,
		OutfitPreview preview,
		OnboardingReadinessService readiness,
		PluginMessageService messages,
		Logger logger
	) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.configuration = Objects.requireNonNull(configuration, "configuration");
		this.storage = Objects.requireNonNull(storage, "storage");
		this.cinematics = Objects.requireNonNull(cinematics, "cinematics");
		this.outfits = Objects.requireNonNull(outfits, "outfits");
		this.outfitConfiguration = Objects.requireNonNull(outfitConfiguration, "outfitConfiguration");
		this.preview = Objects.requireNonNull(preview, "preview");
		this.readiness = Objects.requireNonNull(readiness, "readiness");
		this.presentation = new OnboardingPresentationRunner(this.plugin);
		this.messages = Objects.requireNonNull(messages, "messages");
		this.logger = Objects.requireNonNull(logger, "logger");
	}

	public void start(Player player) {
		if (!configuration.current().enabled()) {
			messages.send(player, MessageDefaults.ONBOARDING_DISABLED);
			return;
		}
		if (sessions.containsKey(player.getUniqueId())) {
			messages.send(player, MessageDefaults.ONBOARDING_ALREADY_ACTIVE);
			return;
		}
		OnboardingConfiguration snapshot = configuration.current();
		OnboardingSession session = new OnboardingSession(
			plugin,
			player,
			snapshot,
			cinematics,
			outfits,
			outfitConfiguration,
			preview,
			presentation,
			storage,
			messages,
			logger,
			sessions::remove
		);
		sessions.put(player.getUniqueId(), session);
		session.start();
	}

	public void startFirstJoin(Player player) {
		if (!configuration.current().enabled() || !configuration.current().firstJoinEnabled()) return;
		UUID playerId = player.getUniqueId();
		CompletableFuture.supplyAsync(() -> {
			try {
				return storage.completed(playerId);
			} catch (SQLException exception) {
				throw new IllegalStateException(exception);
			}
		}).whenComplete((completed, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
			if (failure != null) {
				logger.log(Level.WARNING, "Failed to load onboarding state for " + playerId, failure);
				return;
			}
			Player online = Bukkit.getPlayer(playerId);
			if (online != null && !completed) prepareFirstJoin(online);
		}));
	}

	private void prepareFirstJoin(Player player) {
		OnboardingConfiguration snapshot = configuration.current();
		readiness.prepare(player, snapshot).whenComplete((result, failure) ->
			Bukkit.getScheduler().runTask(plugin, () -> {
				Player online = Bukkit.getPlayer(player.getUniqueId());
				if (online == null) return;
				if (failure != null || result == null || !result.ready()) {
					messages.send(online, MessageDefaults.ONBOARDING_READINESS_FAILED);
					return;
				}
				start(online);
			})
		);
	}

	public void handleInput(Player player, Input input) {
		OnboardingSession session = sessions.get(player.getUniqueId());
		if (session != null) session.handle(input);
	}

	public boolean active(UUID playerId) {
		return sessions.containsKey(playerId);
	}

	public void stop(UUID playerId, boolean restorePlayer) {
		OnboardingSession session = sessions.get(playerId);
		if (session != null) session.stop(restorePlayer);
	}

	public void reset(UUID playerId) throws SQLException {
		storage.reset(playerId);
	}

	public void reload() {
		configuration.reload();
	}

	public void capturePreviewPoint(Player player, OnboardingPreviewPoint point) {
		configuration.savePreviewPoint(point, player.getLocation());
	}

	@Override
	public void close() throws SQLException {
		for (UUID playerId : java.util.List.copyOf(sessions.keySet())) {
			stop(playerId, true);
		}
		readiness.close();
		storage.close();
	}
}
