package com.voluble.titanMC.milestones.config;

import java.util.Objects;

public record MilestoneMenuConfig(int rows, String title) {
	public MilestoneMenuConfig {
		if (rows < 1 || rows > 6) throw new IllegalArgumentException("menu rows must be between 1 and 6");
		title = Objects.requireNonNull(title, "title").trim();
		if (title.isBlank()) throw new IllegalArgumentException("menu title must not be blank");
	}
}
