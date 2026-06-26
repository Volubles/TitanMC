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
		Objects.requireNonNull(material, "material");
		CredAmount amount = values.get(material);
		return amount == null || amount.isZero() ? Optional.empty() : Optional.of(amount);
	}
}
