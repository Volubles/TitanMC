package com.voluble.titanMC.regions.protection.bukkit;

import com.voluble.titanMC.regions.protection.model.ProtectionDecision;
import com.voluble.titanMC.regions.protection.policy.ProtectionBypass;
import com.voluble.titanMC.regions.protection.policy.RegionPolicyRegistry;
import com.voluble.titanMC.regions.protection.service.ProtectionService;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockAutomationProtectionListenerTest extends MockBukkitProtectionTestSupport {

	private World world;

	@BeforeEach
	void createFixtures() {
		world = server.addSimpleWorld("block_automation_protection");
		Plugin plugin = MockBukkit.createMockPlugin();
		ProtectionService protection = new ProtectionService(
			(worldId, x, y, z) -> List.of(),
			RegionPolicyRegistry.builder().build(),
			request -> ProtectionDecision.DENY,
			ProtectionBypass.none()
		);
		server.getPluginManager().registerEvents(
			new BlockAutomationProtectionListener(protection),
			plugin
		);
	}

	@Test
	void directPlayerInputsAreGovernedByInteractionFlagsInsteadOfRedstoneFlag() {
		assertRedstoneChangePreserved(Material.LEVER);
		assertRedstoneChangePreserved(Material.STONE_BUTTON);
		assertRedstoneChangePreserved(Material.STONE_PRESSURE_PLATE);
		assertRedstoneChangePreserved(Material.TRIPWIRE);
	}

	@Test
	void automaticRedstoneChangesRemainProtected() {
		Block wire = world.getBlockAt(8, 64, 8);
		wire.setType(Material.REDSTONE_WIRE);
		BlockRedstoneEvent event = new BlockRedstoneEvent(wire, 0, 15);

		server.getPluginManager().callEvent(event);

		assertEquals(0, event.getNewCurrent());
	}

	private void assertRedstoneChangePreserved(Material material) {
		Block block = world.getBlockAt(material.ordinal(), 64, 0);
		block.setType(material);
		BlockRedstoneEvent event = new BlockRedstoneEvent(block, 0, 15);

		server.getPluginManager().callEvent(event);

		assertEquals(15, event.getNewCurrent(), material::name);
	}
}
