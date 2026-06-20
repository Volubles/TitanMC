package com.voluble.titanMC.regions.protection.bukkit;

import com.voluble.titanMC.regions.protection.model.ProtectionAction;
import com.voluble.titanMC.regions.protection.model.ProtectionActor;
import com.voluble.titanMC.regions.protection.model.TransitionRule;
import com.voluble.titanMC.regions.protection.service.ProtectionEvaluation;
import com.voluble.titanMC.regions.protection.service.ProtectionService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.TNTPrimeEvent;

import java.time.Instant;
import java.util.Objects;

public final class BlockAutomationProtectionListener implements Listener {

	private static final ProtectionActor AUTOMATION = ProtectionActor.environment("block-automation");
	private static final ProtectionActor REDSTONE = ProtectionActor.environment("redstone");
	private static final ProtectionActor TNT = ProtectionActor.environment("tnt-prime");

	private final ProtectionService protection;

	public BlockAutomationProtectionListener(ProtectionService protection) {
		this.protection = Objects.requireNonNull(protection, "protection");
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockDispense(BlockDispenseEvent event) {
		Block source = event.getBlock();
		Block target = source.getBlockData() instanceof Directional directional
			? source.getRelative(directional.getFacing())
			: source;
		ProtectionEvaluation evaluation = protection.beginEvaluation(AUTOMATION, Instant.now());
		var movement = BukkitProtectionMapper.movement(
			AUTOMATION, ProtectionAction.BLOCK_AUTOMATION, source, target
		);
		if (!evaluation.resolveTransition(movement, TransitionRule.BOTH).allowed()) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onRedstoneChange(BlockRedstoneEvent event) {
		if (isDirectInteractionInput(event.getBlock())) return;
		if (!protection.allowed(BukkitProtectionMapper.request(
			REDSTONE, ProtectionAction.REDSTONE_CHANGE, event.getBlock()
		))) {
			event.setNewCurrent(event.getOldCurrent());
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onTntPrime(TNTPrimeEvent event) {
		Player player = event.getPrimingEntity() == null
			? null
			: EntityInteractionProtectionListener.responsiblePlayer(event.getPrimingEntity());
		boolean allowed = player == null
			? protection.allowed(BukkitProtectionMapper.request(
				TNT, ProtectionAction.TNT_PRIME, event.getBlock()
			))
			: protection.allowed(BukkitProtectionMapper.request(
				player, ProtectionAction.TNT_PRIME, event.getBlock()
			));
		if (!allowed) event.setCancelled(true);
	}

	private static boolean isDirectInteractionInput(Block block) {
		Material type = block.getType();
		String name = type.name();
		return type == Material.LEVER
			|| type == Material.TRIPWIRE
			|| type == Material.DAYLIGHT_DETECTOR
			|| type == Material.LECTERN
			|| type == Material.COMPARATOR
			|| type == Material.REPEATER
			|| name.endsWith("_BUTTON")
			|| name.endsWith("_PRESSURE_PLATE");
	}
}
