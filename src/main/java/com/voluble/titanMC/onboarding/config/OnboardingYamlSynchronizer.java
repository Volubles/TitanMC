package com.voluble.titanMC.onboarding.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Objects;

final class OnboardingYamlSynchronizer {
	private OnboardingYamlSynchronizer() {
	}

	static boolean sync(YamlConfiguration yaml) {
		Objects.requireNonNull(yaml, "yaml");
		boolean changed = false;
		changed |= setMissing(yaml, "readiness.enabled", true);
		changed |= setMissing(yaml, "readiness.waiting-room.enabled", false);
		changed |= setMissing(yaml, "readiness.resource-pack.enabled", true);
		changed |= setMissing(yaml, "readiness.resource-pack.require-nexo", true);
		changed |= setMissing(yaml, "readiness.resource-pack.timeout-ticks", 600);
		changed |= setMissing(yaml, "readiness.warmup.enabled", true);
		changed |= setMissing(yaml, "readiness.warmup.timeout-ticks", 1200);
		changed |= setMissing(yaml, "presentation.enabled", true);
		changed |= setMissing(yaml, "presentation.steps", List.of(
			Map.of(
				"title", Map.of(
					"text", "Välkommen till",
					"style", "<color:#30bbf1>{{text}}</color>"
				),
				"subtitle", Map.of(
					"text", "Svea Prison",
					"style", "<color:#42d829><bold>{{text}}</bold></color>"
				),
				"typewriter", Map.of(
					"total-ticks", 40,
					"sound", Map.of(
						"enabled", false
					)
				),
				"hold-ticks", 20
			),
			Map.of(
				"title", Map.of(
					"text", "Välj din outfit",
					"style", "<color:#30bbf1>{{text}}</color>"
				),
				"subtitle", Map.of(
					"text", "Använd A och D",
					"style", "<gray>{{text}}</gray>"
				),
				"typewriter", Map.of(
					"total-ticks", 25,
					"sound", Map.of(
						"enabled", false
					)
				),
				"hold-ticks", 10
			)
		));
		changed |= setMissing(yaml, "presentation.complete-sound.enabled", false);
		changed |= setMissing(yaml, "presentation.preview-spawn-sound.enabled", true);
		changed |= setMissing(yaml, "presentation.preview-spawn-sound.key", "entity.player.levelup");
		changed |= setMissing(yaml, "presentation.preview-spawn-sound.category", "master");
		changed |= setMissing(yaml, "presentation.preview-spawn-sound.volume", 0.65D);
		changed |= setMissing(yaml, "presentation.preview-spawn-sound.pitch", 1.35D);
		return changed;
	}

	private static boolean setMissing(YamlConfiguration yaml, String path, Object value) {
		if (yaml.isSet(path)) return false;
		yaml.set(path, value);
		return true;
	}
}
