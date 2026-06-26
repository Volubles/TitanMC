package com.voluble.titanMC.progression.config;

import com.voluble.titanMC.progression.model.CredAmount;
import org.bukkit.Material;

import java.util.Map;
import java.util.Objects;

public record ProgressionSourceConfig(String displayName, boolean enabled, Map<Material, CredAmount> blockValues) {
	public ProgressionSourceConfig {
		Objects.requireNonNull(displayName, "displayName");
		if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
		blockValues = Map.copyOf(Objects.requireNonNull(blockValues, "blockValues"));
	}
}
