package com.voluble.titanMC.onboarding.config;

import org.bukkit.configuration.ConfigurationSection;

public record OnboardingResourcePackConfiguration(
	boolean enabled,
	boolean requireNexo,
	long timeoutTicks
) {
	public OnboardingResourcePackConfiguration {
		if (timeoutTicks <= 0L) throw new IllegalArgumentException("resource pack timeout must be positive");
	}

	public static OnboardingResourcePackConfiguration load(ConfigurationSection section) {
		return new OnboardingResourcePackConfiguration(
			OnboardingConfiguration.requiredBoolean(section, "readiness.resource-pack.enabled"),
			OnboardingConfiguration.requiredBoolean(section, "readiness.resource-pack.require-nexo"),
			OnboardingConfiguration.requiredLong(section, "readiness.resource-pack.timeout-ticks")
		);
	}
}
