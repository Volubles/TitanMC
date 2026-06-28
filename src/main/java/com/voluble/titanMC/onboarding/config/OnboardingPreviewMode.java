package com.voluble.titanMC.onboarding.config;

import java.util.Locale;

public enum OnboardingPreviewMode {
	RUNWAY,
	CAROUSEL;

	public static OnboardingPreviewMode parse(String value) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing onboarding preview mode");
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "runway" -> RUNWAY;
			case "carousel" -> CAROUSEL;
			default -> throw new IllegalArgumentException("Unknown onboarding preview mode: " + value);
		};
	}
}
