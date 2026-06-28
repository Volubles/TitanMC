package com.voluble.titanMC.onboarding.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

public record OnboardingReadinessConfiguration(
	boolean enabled,
	OnboardingWaitingRoomConfiguration waitingRoom,
	OnboardingResourcePackConfiguration resourcePack,
	OnboardingWarmupConfiguration warmup
) {
	public OnboardingReadinessConfiguration {
		Objects.requireNonNull(waitingRoom, "waitingRoom");
		Objects.requireNonNull(resourcePack, "resourcePack");
		Objects.requireNonNull(warmup, "warmup");
	}

	public static OnboardingReadinessConfiguration load(ConfigurationSection section) {
		return new OnboardingReadinessConfiguration(
			OnboardingConfiguration.requiredBoolean(section, "readiness.enabled"),
			OnboardingWaitingRoomConfiguration.load(
				OnboardingConfiguration.requiredSection(section, "readiness.waiting-room")
			),
			OnboardingResourcePackConfiguration.load(
				OnboardingConfiguration.requiredSection(section, "readiness.resource-pack")
			),
			OnboardingWarmupConfiguration.load(
				OnboardingConfiguration.requiredSection(section, "readiness.warmup")
			)
		);
	}
}
