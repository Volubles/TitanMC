package com.voluble.titanMC.regions.protection.bukkit;

import com.voluble.titanMC.regions.protection.model.ProtectionAction;
import com.voluble.titanMC.regions.protection.service.ProtectionService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.Container;
import org.bukkit.block.EnderChest;

import java.util.Objects;

public final class BlockProtectionListener implements Listener {

	private final ProtectionService protection;

	public BlockProtectionListener(ProtectionService protection) {
		this.protection = Objects.requireNonNull(protection, "protection");
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		if (!protection.allowed(BukkitProtectionMapper.request(
			event.getPlayer(), ProtectionAction.BLOCK_BREAK, event.getBlock()
		))) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent event) {
		if (!protection.allowed(BukkitProtectionMapper.request(
			event.getPlayer(), ProtectionAction.BLOCK_PLACE, event.getBlockPlaced()
		))) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockInteract(PlayerInteractEvent event) {
		if (event.getClickedBlock() == null) return;
		if (event.getAction() == Action.PHYSICAL) {
			if (!protection.allowed(BukkitProtectionMapper.request(
				event.getPlayer(), ProtectionAction.PHYSICAL_INTERACT, event.getClickedBlock()
			))) {
				event.setCancelled(true);
			}
			return;
		}
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		if (!event.getClickedBlock().getType().isInteractable()) return;
		ProtectionAction action = isContainer(event.getClickedBlock())
			? ProtectionAction.CONTAINER_OPEN
			: ProtectionAction.BLOCK_INTERACT;
		if (!protection.allowed(BukkitProtectionMapper.request(
			event.getPlayer(), action, event.getClickedBlock()
		))) {
			event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
		}
	}

	private static boolean isContainer(org.bukkit.block.Block block) {
		var state = block.getState();
		return state instanceof Container || state instanceof EnderChest;
	}
}
