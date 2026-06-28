package com.voluble.titanMC.onboarding.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Objects;

final class OnboardingYamlSynchronizer {
	private OnboardingYamlSynchronizer() {
	}

	static boolean sync(YamlConfiguration yaml) {
		Objects.requireNonNull(yaml, "yaml");
		boolean changed = false;
		if (!yaml.isSet("presentation.enabled")) {
			yaml.set("presentation.enabled", false);
			changed = true;
		}
		return changed;
	}
}
