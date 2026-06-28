package com.voluble.titanMC.onboarding.readiness;

import com.voluble.titanMC.onboarding.config.OnboardingConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public final class OnboardingReadinessService implements AutoCloseable {
	private final OnboardingWaitingRoom waitingRoom;
	private final OnboardingResourcePackGate resourcePack;
	private final OnboardingOutfitWarmup warmup;
	private final Logger logger;

	public OnboardingReadinessService(
		OnboardingWaitingRoom waitingRoom,
		OnboardingResourcePackGate resourcePack,
		OnboardingOutfitWarmup warmup,
		Logger logger
	) {
		this.waitingRoom = Objects.requireNonNull(waitingRoom, "waitingRoom");
		this.resourcePack = Objects.requireNonNull(resourcePack, "resourcePack");
		this.warmup = Objects.requireNonNull(warmup, "warmup");
		this.logger = Objects.requireNonNull(logger, "logger");
	}

	public CompletableFuture<OnboardingReadinessResult> prepare(Player player, OnboardingConfiguration configuration) {
		if (!configuration.readiness().enabled()) {
			return CompletableFuture.completedFuture(OnboardingReadinessResult.READY);
		}
		return waitingRoom.move(player, configuration.readiness().waitingRoom())
			.thenCompose(result -> next(player, configuration, result, () ->
				resourcePack.await(player, configuration.readiness().resourcePack())
			))
			.thenCompose(result -> next(player, configuration, result, () ->
				warmup.prepare(player, configuration)
			))
			.whenComplete((result, failure) -> {
				if (failure != null) {
					logger.warning("Onboarding readiness failed for " + player.getUniqueId() + ": " + failure.getMessage());
				} else if (result != null && !result.ready()) {
					logger.warning("Onboarding readiness failed for " + player.getUniqueId() + ": " + result);
				}
			});
	}

	public Listener listener() {
		return resourcePack;
	}

	@Override
	public void close() {
		resourcePack.close();
	}

	private CompletableFuture<OnboardingReadinessResult> next(
		Player player,
		OnboardingConfiguration configuration,
		OnboardingReadinessResult result,
		java.util.function.Supplier<CompletableFuture<OnboardingReadinessResult>> next
	) {
		if (!player.isOnline()) return CompletableFuture.completedFuture(OnboardingReadinessResult.WARMUP_FAILED);
		if (!configuration.readiness().enabled() || !result.ready()) return CompletableFuture.completedFuture(result);
		return next.get();
	}
}
