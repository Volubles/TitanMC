package com.voluble.titanMC.onboarding.config;

import org.bukkit.configuration.ConfigurationSection;

public record OnboardingWarmupConfiguration(
	boolean enabled,
	long timeoutTicks
) {
	public OnboardingWarmupConfiguration {
		if (timeoutTicks <= 0L) throw new IllegalArgumentException("onboarding warmup timeout must be positive");
	}

	public static OnboardingWarmupConfiguration load(ConfigurationSection section) {
		return new OnboardingWarmupConfiguration(
			OnboardingConfiguration.requiredBoolean(section, "readiness.warmup.enabled"),
			OnboardingConfiguration.requiredLong(section, "readiness.warmup.timeout-ticks")
		);
	}
}
