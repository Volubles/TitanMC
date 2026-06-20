package com.voluble.titanMC.regions.protection.bukkit;

import com.voluble.titanMC.regions.protection.model.ProtectionAction;
import com.voluble.titanMC.regions.protection.model.ProtectionDecision;
import com.voluble.titanMC.regions.protection.policy.ProtectionBypass;
import com.voluble.titanMC.regions.protection.policy.RegionPolicyRegistry;
import com.voluble.titanMC.regions.protection.service.ProtectionService;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockProtectionListenerTest extends MockBukkitProtectionTestSupport {

	private World world;
	private Player player;
	private Plugin plugin;

	@BeforeEach
	void createFixtures() {
		world = server.addSimpleWorld("block_protection");
		player = server.addPlayer();
		plugin = MockBukkit.createMockPlugin();
	}

	@Test
	void cancelsBreakAndPlaceWhenProtectionDenies() {
		register(request -> ProtectionDecision.DENY, ProtectionBypass.none());

		BlockBreakEvent breakEvent = breakEvent(world.getBlockAt(1, 64, 1));
		BlockPlaceEvent placeEvent = placeEvent(world.getBlockAt(2, 64, 2));
		server.getPluginManager().callEvent(breakEvent);
		server.getPluginManager().callEvent(placeEvent);

		assertTrue(breakEvent.isCancelled());
		assertTrue(placeEvent.isCancelled());
	}

	@Test
	void mapsBreakAndPlaceToIndependentActions() {
		register(
			request -> request.action() == ProtectionAction.BLOCK_BREAK
				? ProtectionDecision.ALLOW
				: ProtectionDecision.DENY,
			ProtectionBypass.none()
		);

		BlockBreakEvent breakEvent = breakEvent(world.getBlockAt(1, 64, 1));
		BlockPlaceEvent placeEvent = placeEvent(world.getBlockAt(2, 64, 2));
		server.getPluginManager().callEvent(breakEvent);
		server.getPluginManager().callEvent(placeEvent);

		assertFalse(breakEvent.isCancelled());
		assertTrue(placeEvent.isCancelled());
	}

	@Test
	void bypassPermissionAllowsDeniedAction() {
		player.addAttachment(plugin, "titanmc.protection.bypass", true);
		register(
			request -> ProtectionDecision.DENY,
			ProtectionBypass.permission("titanmc.protection.bypass")
		);

		BlockBreakEvent event = breakEvent(world.getBlockAt(1, 64, 1));
		server.getPluginManager().callEvent(event);

		assertFalse(event.isCancelled());
	}

	@Test
	void deniesRightClickAndPhysicalBlockInteractions() {
		register(request -> ProtectionDecision.DENY, ProtectionBypass.none());
		Block clicked = world.getBlockAt(3, 64, 3);
		clicked.setType(Material.LEVER);
		PlayerInteractEvent blockInteraction = interactEvent(Action.RIGHT_CLICK_BLOCK, clicked);
		PlayerInteractEvent physicalInteraction = interactEvent(Action.PHYSICAL, clicked);
		PlayerInteractEvent airInteraction = interactEvent(Action.RIGHT_CLICK_AIR, null);
		Event.Result airBefore = airInteraction.useInteractedBlock();

		server.getPluginManager().callEvent(blockInteraction);
		server.getPluginManager().callEvent(physicalInteraction);
		server.getPluginManager().callEvent(airInteraction);

		assertEquals(Event.Result.DENY, blockInteraction.useInteractedBlock());
		assertTrue(physicalInteraction.isCancelled());
		assertEquals(airBefore, airInteraction.useInteractedBlock());
	}

	@Test
	void doesNotTreatPlainPlacementSupportAsBlockInteraction() {
		register(
			request -> request.action() == ProtectionAction.BLOCK_PLACE
				? ProtectionDecision.ALLOW
				: ProtectionDecision.DENY,
			ProtectionBypass.none()
		);
		Block stone = world.getBlockAt(3, 64, 3);
		stone.setType(Material.STONE);
		PlayerInteractEvent interaction = new PlayerInteractEvent(
			player,
			Action.RIGHT_CLICK_BLOCK,
			new ItemStack(Material.STONE),
			stone,
			BlockFace.UP,
			EquipmentSlot.HAND
		);
		Event.Result before = interaction.useInteractedBlock();
		BlockPlaceEvent placement = placeEvent(world.getBlockAt(3, 65, 3));

		server.getPluginManager().callEvent(interaction);
		server.getPluginManager().callEvent(placement);

		assertEquals(before, interaction.useInteractedBlock());
		assertFalse(placement.isCancelled());
	}

	@Test
	void allowsRightClickWhenProtectionAllows() {
		register(request -> ProtectionDecision.ALLOW, ProtectionBypass.none());
		PlayerInteractEvent event = interactEvent(Action.RIGHT_CLICK_BLOCK, world.getBlockAt(3, 64, 3));
		Event.Result before = event.useInteractedBlock();

		server.getPluginManager().callEvent(event);

		assertEquals(before, event.useInteractedBlock());
	}

	@Test
	void mapsContainersSeparatelyFromOrdinaryBlockInteractions() {
		register(
			request -> request.action() == ProtectionAction.CONTAINER_OPEN
				? ProtectionDecision.DENY
				: ProtectionDecision.ALLOW,
			ProtectionBypass.none()
		);
		Block chest = world.getBlockAt(4, 64, 4);
		chest.setType(Material.CHEST);
		Block stone = world.getBlockAt(5, 64, 5);
		stone.setType(Material.STONE);
		PlayerInteractEvent containerEvent = interactEvent(Action.RIGHT_CLICK_BLOCK, chest);
		PlayerInteractEvent blockEvent = interactEvent(Action.RIGHT_CLICK_BLOCK, stone);

		server.getPluginManager().callEvent(containerEvent);
		server.getPluginManager().callEvent(blockEvent);

		assertEquals(Event.Result.DENY, containerEvent.useInteractedBlock());
		assertFalse(blockEvent.useInteractedBlock() == Event.Result.DENY);
	}

	private void register(
		com.voluble.titanMC.regions.protection.policy.ProtectionDefaults defaults,
		ProtectionBypass bypass
	) {
		ProtectionService service = new ProtectionService(
			(worldId, x, y, z) -> List.of(),
			RegionPolicyRegistry.builder().build(),
			defaults,
			bypass
		);
		server.getPluginManager().registerEvents(new BlockProtectionListener(service), plugin);
	}

	private BlockBreakEvent breakEvent(Block block) {
		block.setType(Material.STONE);
		return new BlockBreakEvent(block, player);
	}

	private BlockPlaceEvent placeEvent(Block block) {
		block.setType(Material.STONE);
		return new BlockPlaceEvent(
			block,
			block.getState(),
			world.getBlockAt(block.getX(), block.getY() - 1, block.getZ()),
			new ItemStack(Material.STONE),
			player,
			true,
			EquipmentSlot.HAND
		);
	}

	private PlayerInteractEvent interactEvent(Action action, Block clicked) {
		return new PlayerInteractEvent(
			player, action, new ItemStack(Material.AIR), clicked, BlockFace.UP, EquipmentSlot.HAND
		);
	}
}
