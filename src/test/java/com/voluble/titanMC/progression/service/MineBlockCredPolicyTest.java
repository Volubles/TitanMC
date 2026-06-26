package com.voluble.titanMC.progression.service;

import com.voluble.titanMC.progression.model.CredAmount;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineBlockCredPolicyTest {
	@Test
	void returnsConfiguredRewardForMaterial() {
		MineBlockCredPolicy policy = new MineBlockCredPolicy(Map.of(Material.DIAMOND_ORE, CredAmount.of(50L)));

		assertEquals(50L, policy.rewardFor(Material.DIAMOND_ORE).orElseThrow().value());
	}

	@Test
	void appliesMultiplierAndRoundsToWholeCred() {
		MineBlockCredPolicy policy = new MineBlockCredPolicy(Map.of(Material.REDSTONE_ORE, CredAmount.of(6L)));

		assertEquals(9L, policy.rewardFor(Material.REDSTONE_ORE, 1.5D).orElseThrow().value());
	}

	@Test
	void zeroMultiplierDisablesReward() {
		MineBlockCredPolicy policy = new MineBlockCredPolicy(Map.of(Material.STONE, CredAmount.of(1L)));

		assertTrue(policy.rewardFor(Material.STONE, 0.0D).isEmpty());
	}

	@Test
	void returnsEmptyForUnconfiguredMaterial() {
		MineBlockCredPolicy policy = new MineBlockCredPolicy(Map.of(Material.STONE, CredAmount.of(1L)));

		assertTrue(policy.rewardFor(Material.DIRT).isEmpty());
	}
}
