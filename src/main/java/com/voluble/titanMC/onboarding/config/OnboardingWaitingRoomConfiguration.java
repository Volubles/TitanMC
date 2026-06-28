package com.voluble.titanMC.onboarding.config;

import org.bukkit.configuration.ConfigurationSection;

public record OnboardingWaitingRoomConfiguration(
	boolean enabled,
	OnboardingConfiguration.LocationSpec location
) {
	public OnboardingWaitingRoomConfiguration {
		if (enabled && location == null) {
			throw new IllegalArgumentException("Missing onboarding config section: readiness.waiting-room.location");
		}
	}

	public static OnboardingWaitingRoomConfiguration load(ConfigurationSection section) {
		boolean enabled = OnboardingConfiguration.requiredBoolean(section, "readiness.waiting-room.enabled");
		OnboardingConfiguration.LocationSpec location = null;
		if (enabled) {
			location = OnboardingConfiguration.LocationSpec.load(
				OnboardingConfiguration.requiredSection(section, "readiness.waiting-room.location")
			);
		}
		return new OnboardingWaitingRoomConfiguration(enabled, location);
	}
}
