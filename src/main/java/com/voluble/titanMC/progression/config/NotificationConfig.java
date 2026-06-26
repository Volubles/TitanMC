package com.voluble.titanMC.progression.config;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record NotificationConfig(
		String playerMessage,
		String broadcastMessage,
		int broadcastEvery,
		Optional<String> playerSound,
		Optional<String> broadcastSound,
		Map<Integer, String> soundOverrides
) {
	public NotificationConfig {
		Objects.requireNonNull(playerMessage, "playerMessage");
		Objects.requireNonNull(broadcastMessage, "broadcastMessage");
		Objects.requireNonNull(playerSound, "playerSound");
		Objects.requireNonNull(broadcastSound, "broadcastSound");
		soundOverrides = Map.copyOf(Objects.requireNonNull(soundOverrides, "soundOverrides"));
		if (broadcastEvery < 0) {
			throw new IllegalArgumentException("broadcastEvery must be >= 0 (was " + broadcastEvery + ")");
		}
	}

	public boolean broadcastsEnabled() {
		return broadcastEvery > 0;
	}

	public boolean shouldBroadcast(int level) {
		return broadcastsEnabled() && level % broadcastEvery == 0;
	}

	public Optional<String> soundForLevel(int level) {
		String override = soundOverrides.get(level);
		return override != null ? Optional.of(override) : playerSound;
	}

	public static NotificationConfig defaults() {
		return new NotificationConfig(
			"<green>Level up! You are now level <yellow>{level}</yellow>.",
			"<gold>{player} reached level <yellow>{level}</yellow>!",
			5,
			Optional.of("entity.player.levelup"),
			Optional.of("entity.experience_orb.pickup"),
			Map.of()
		);
	}
}
