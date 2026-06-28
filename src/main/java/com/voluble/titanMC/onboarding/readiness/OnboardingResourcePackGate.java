package com.voluble.titanMC.onboarding.readiness;

import com.voluble.titanMC.onboarding.config.OnboardingResourcePackConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class OnboardingResourcePackGate implements Listener, AutoCloseable {
	private final Plugin plugin;
	private final OnboardingResourcePackSender sender;
	private final Logger logger;
	private final Map<UUID, PendingPack> pending = new ConcurrentHashMap<>();

	public OnboardingResourcePackGate(Plugin plugin, OnboardingResourcePackSender sender, Logger logger) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.sender = Objects.requireNonNull(sender, "sender");
		this.logger = Objects.requireNonNull(logger, "logger");
	}

	public CompletableFuture<OnboardingReadinessResult> await(Player player, OnboardingResourcePackConfiguration configuration) {
		if (!configuration.enabled()) return CompletableFuture.completedFuture(OnboardingReadinessResult.READY);
		if (!sender.available()) {
			if (configuration.requireNexo()) return CompletableFuture.completedFuture(OnboardingReadinessResult.RESOURCE_PACK_UNAVAILABLE);
			return CompletableFuture.completedFuture(OnboardingReadinessResult.READY);
		}
		UUID playerId = player.getUniqueId();
		CompletableFuture<OnboardingReadinessResult> future = new CompletableFuture<>();
		PendingPack previous = pending.remove(playerId);
		if (previous != null) previous.complete(OnboardingReadinessResult.RESOURCE_PACK_FAILED);
		BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () ->
			complete(playerId, OnboardingReadinessResult.RESOURCE_PACK_TIMEOUT), configuration.timeoutTicks()
		);
		pending.put(playerId, new PendingPack(future, timeout));
		try {
			sender.send(player);
		} catch (RuntimeException exception) {
			logger.warning("Failed to send onboarding resource pack to " + playerId + ": " + exception.getMessage());
			complete(playerId, OnboardingReadinessResult.RESOURCE_PACK_FAILED);
		}
		return future;
	}

	@EventHandler
	public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
		UUID playerId = event.getPlayer().getUniqueId();
		if (!pending.containsKey(playerId)) return;
		switch (event.getStatus()) {
			case SUCCESSFULLY_LOADED -> complete(playerId, OnboardingReadinessResult.READY);
			case DECLINED -> complete(playerId, OnboardingReadinessResult.RESOURCE_PACK_DECLINED);
			case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED ->
				complete(playerId, OnboardingReadinessResult.RESOURCE_PACK_FAILED);
			case ACCEPTED, DOWNLOADED -> {
			}
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		complete(event.getPlayer().getUniqueId(), OnboardingReadinessResult.RESOURCE_PACK_FAILED);
	}

	private void complete(UUID playerId, OnboardingReadinessResult result) {
		PendingPack pack = pending.remove(playerId);
		if (pack != null) pack.complete(result);
	}

	@Override
	public void close() {
		for (UUID playerId : java.util.List.copyOf(pending.keySet())) {
			complete(playerId, OnboardingReadinessResult.RESOURCE_PACK_FAILED);
		}
	}

	private record PendingPack(CompletableFuture<OnboardingReadinessResult> future, BukkitTask timeout) {
		private PendingPack {
			Objects.requireNonNull(future, "future");
			Objects.requireNonNull(timeout, "timeout");
		}

		void complete(OnboardingReadinessResult result) {
			timeout.cancel();
			future.complete(result);
		}
	}
}
