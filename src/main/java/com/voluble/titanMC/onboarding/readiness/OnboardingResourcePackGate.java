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

public final class OnboardingResourcePackGate implements Listener, AutoCloseable {
	private final Plugin plugin;
	private final Map<UUID, PendingPack> pending = new ConcurrentHashMap<>();
	private final Map<UUID, OnboardingReadinessResult> latestTerminalStatus = new ConcurrentHashMap<>();

	public OnboardingResourcePackGate(Plugin plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
	}

	public CompletableFuture<OnboardingReadinessResult> await(Player player, OnboardingResourcePackConfiguration configuration) {
		if (!configuration.enabled()) return CompletableFuture.completedFuture(OnboardingReadinessResult.READY);
		if (configuration.requireNexo() && !Bukkit.getPluginManager().isPluginEnabled("Nexo")) {
			return CompletableFuture.completedFuture(OnboardingReadinessResult.RESOURCE_PACK_UNAVAILABLE);
		}
		UUID playerId = player.getUniqueId();
		OnboardingReadinessResult latest = latestTerminalStatus.get(playerId);
		if (latest != null) return CompletableFuture.completedFuture(latest);
		OnboardingReadinessResult current = terminalResult(player.getResourcePackStatus());
		if (current != null) return CompletableFuture.completedFuture(current);
		CompletableFuture<OnboardingReadinessResult> future = new CompletableFuture<>();
		PendingPack previous = pending.remove(playerId);
		if (previous != null) previous.complete(OnboardingReadinessResult.RESOURCE_PACK_FAILED);
		BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () ->
			complete(playerId, OnboardingReadinessResult.RESOURCE_PACK_TIMEOUT), configuration.timeoutTicks()
		);
		pending.put(playerId, new PendingPack(future, timeout));
		return future;
	}

	@EventHandler
	public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
		UUID playerId = event.getPlayer().getUniqueId();
		switch (event.getStatus()) {
			case SUCCESSFULLY_LOADED -> rememberAndComplete(playerId, OnboardingReadinessResult.READY);
			case DECLINED -> rememberAndComplete(playerId, OnboardingReadinessResult.RESOURCE_PACK_DECLINED);
			case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> rememberAndComplete(
				playerId, OnboardingReadinessResult.RESOURCE_PACK_FAILED
			);
			case ACCEPTED, DOWNLOADED -> latestTerminalStatus.remove(playerId);
		}
	}

	private OnboardingReadinessResult terminalResult(PlayerResourcePackStatusEvent.Status status) {
		if (status == null) return null;
		return switch (status) {
			case SUCCESSFULLY_LOADED -> OnboardingReadinessResult.READY;
			case DECLINED -> OnboardingReadinessResult.RESOURCE_PACK_DECLINED;
			case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> OnboardingReadinessResult.RESOURCE_PACK_FAILED;
			case ACCEPTED, DOWNLOADED -> null;
		};
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		latestTerminalStatus.remove(event.getPlayer().getUniqueId());
		complete(event.getPlayer().getUniqueId(), OnboardingReadinessResult.RESOURCE_PACK_FAILED);
	}

	private void rememberAndComplete(UUID playerId, OnboardingReadinessResult result) {
		latestTerminalStatus.put(playerId, result);
		complete(playerId, result);
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
