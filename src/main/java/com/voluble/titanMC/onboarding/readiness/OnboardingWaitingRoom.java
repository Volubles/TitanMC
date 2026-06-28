package com.voluble.titanMC.onboarding.readiness;

import com.voluble.titanMC.onboarding.config.OnboardingWaitingRoomConfiguration;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public final class OnboardingWaitingRoom {
	public CompletableFuture<OnboardingReadinessResult> move(Player player, OnboardingWaitingRoomConfiguration configuration) {
		if (!configuration.enabled()) return CompletableFuture.completedFuture(OnboardingReadinessResult.READY);
		return player.teleportAsync(configuration.location().toLocation())
			.handle((teleported, failure) -> {
				if (failure != null || !Boolean.TRUE.equals(teleported)) {
					return OnboardingReadinessResult.WAITING_ROOM_FAILED;
				}
				return OnboardingReadinessResult.READY;
			});
	}
}
