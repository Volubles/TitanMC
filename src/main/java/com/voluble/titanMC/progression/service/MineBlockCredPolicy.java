package com.voluble.titanMC.progression.service;

import com.voluble.titanMC.progression.model.CredAmount;
import org.bukkit.Material;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MineBlockCredPolicy {
	private final Map<Material, CredAmount> values;

	public MineBlockCredPolicy(Map<Material, CredAmount> values) {
		this.values = Map.copyOf(Objects.requireNonNull(values, "values"));
	}

	public Optional<CredAmount> rewardFor(Material material) {
		return rewardFor(material, 1.0D);
	}

	public Optional<CredAmount> rewardFor(Material material, double multiplier) {
		Objects.requireNonNull(material, "material");
		if (!Double.isFinite(multiplier) || multiplier <= 0.0D) return Optional.empty();
		CredAmount amount = values.get(material);
		if (amount == null || amount.isZero()) return Optional.empty();
		long multiplied = Math.round(amount.value() * multiplier);
		return multiplied <= 0L ? Optional.empty() : Optional.of(CredAmount.of(multiplied));
	}
}
