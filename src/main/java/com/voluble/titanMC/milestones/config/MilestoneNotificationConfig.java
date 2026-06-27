package com.voluble.titanMC.milestones.config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record MilestoneNotificationConfig(Completion completion) {
	public MilestoneNotificationConfig {
		Objects.requireNonNull(completion, "completion");
	}

	public record Completion(
		boolean enabled,
		long initialDelayTicks,
		long spacingTicks,
		boolean playerMessageEnabled,
		boolean playerMessageCentered,
		List<String> playerLines,
		Optional<String> sound,
		Broadcast broadcast
	) {
		public Completion {
			if (initialDelayTicks < 0) throw new IllegalArgumentException("completion initial delay must not be negative");
			if (spacingTicks < 0) throw new IllegalArgumentException("completion spacing must not be negative");
			playerLines = List.copyOf(Objects.requireNonNull(playerLines, "playerLines"));
			Objects.requireNonNull(sound, "sound");
			Objects.requireNonNull(broadcast, "broadcast");
		}
	}

	public record Broadcast(boolean enabled, boolean centered, long minimumTarget, List<String> lines, Optional<String> sound) {
		public Broadcast {
			if (minimumTarget < 0) throw new IllegalArgumentException("broadcast minimum target must not be negative");
			lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
			Objects.requireNonNull(sound, "sound");
		}

		public boolean shouldBroadcast(long target) {
			return enabled && target >= minimumTarget;
		}
	}
}
