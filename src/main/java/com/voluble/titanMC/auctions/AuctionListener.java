package com.voluble.titanMC.auctions;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.EquipmentSlot;

import java.util.logging.Level;

public final class AuctionListener implements Listener {
	private final Plugin plugin;
	private final AuctionService auctions;

	public AuctionListener(Plugin plugin, AuctionService auctions) {
		this.plugin = plugin;
		this.auctions = auctions;
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) return;
		Block block = event.getClickedBlock();
		if (block == null) return;
		AuctionLot signLot = auctions.atSign(block);
		if (signLot != null) {
			event.setCancelled(true);
			auctions.purchase(event.getPlayer(), signLot);
			return;
		}
		AuctionLot chestLot = auctions.atChest(block);
		if (chestLot == null) return;
		if (!auctions.canOpen(event.getPlayer(), chestLot)) {
			event.setCancelled(true);
			event.getPlayer().sendMessage(chestLot.state() == AuctionState.FOR_SALE
				? "Buy this mystery chest using its sign."
				: "This chest is temporarily reserved for its buyer.");
		}
	}

	@EventHandler
	public void onBreak(BlockBreakEvent event) {
		if (!protectedBlock(event.getBlock())) return;
		event.setCancelled(true);
		if (!mayDiscard(event.getPlayer())) {
			event.getPlayer().sendMessage("Auction blocks can only be removed by an auction administrator in creative mode.");
			return;
		}
		try {
			if (auctions.discardAt(event.getBlock())) {
				event.getPlayer().sendMessage("Auction permanently discarded.");
			}
		} catch (IllegalStateException exception) {
			plugin.getLogger().log(Level.SEVERE, "Could not discard auction", exception);
			event.getPlayer().sendMessage("The auction could not be removed. Check the server log.");
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onClick(InventoryClickEvent event) {
		Inventory top = event.getView().getTopInventory();
		AuctionLot lot = lot(top);
		if (lot == null) return;
		if (event.getWhoClicked() instanceof org.bukkit.entity.Player player && !auctions.canOpen(player, lot)) {
			event.setCancelled(true);
			return;
		}
		int topSize = top.getSize();
		InventoryAction action = event.getAction();
		boolean clickedTop = event.getRawSlot() >= 0 && event.getRawSlot() < topSize;
		boolean insertsFromBottom = !clickedTop && action == InventoryAction.MOVE_TO_OTHER_INVENTORY;
		boolean insertsIntoTop = clickedTop && switch (action) {
			case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD, CLONE_STACK -> true;
			default -> false;
		};
		if (insertsFromBottom || insertsIntoTop) {
			event.setCancelled(true);
			return;
		}
		synchronizeNextTick(top);
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onDrag(InventoryDragEvent event) {
		Inventory top = event.getView().getTopInventory();
		AuctionLot lot = lot(top);
		if (lot == null) return;
		if (event.getWhoClicked() instanceof org.bukkit.entity.Player player && !auctions.canOpen(player, lot)) {
			event.setCancelled(true);
			return;
		}
		if (event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) {
			event.setCancelled(true);
			return;
		}
		synchronizeNextTick(top);
	}

	@EventHandler
	public void onClose(InventoryCloseEvent event) {
		synchronize(event.getInventory());
	}

	@EventHandler(ignoreCancelled = true)
	public void onMove(InventoryMoveItemEvent event) {
		if (lot(event.getSource()) != null || lot(event.getDestination()) != null) event.setCancelled(true);
	}

	@EventHandler
	public void onEntityExplode(EntityExplodeEvent event) {
		event.blockList().removeIf(block -> auctions.atChest(block) != null || auctions.atSign(block) != null);
	}

	@EventHandler
	public void onBlockExplode(BlockExplodeEvent event) {
		event.blockList().removeIf(block -> auctions.atChest(block) != null || auctions.atSign(block) != null);
	}

	@EventHandler
	public void onPistonExtend(BlockPistonExtendEvent event) {
		if (event.getBlocks().stream().anyMatch(block -> protectedBlock(block)
			|| protectedBlock(block.getRelative(event.getDirection())))) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onPistonRetract(BlockPistonRetractEvent event) {
		if (event.getBlocks().stream().anyMatch(this::protectedBlock)) event.setCancelled(true);
	}

	private void synchronizeNextTick(Inventory inventory) {
		AuctionLot lot = lot(inventory);
		if (lot != null) Bukkit.getScheduler().runTask(plugin, () -> auctions.synchronizeInventory(lot));
	}

	private void synchronize(Inventory inventory) {
		AuctionLot lot = lot(inventory);
		if (lot != null) auctions.synchronizeInventory(lot);
	}

	private AuctionLot lot(Inventory inventory) {
		return inventory.getHolder() instanceof Chest chest ? auctions.atChest(chest.getBlock()) : null;
	}

	private boolean protectedBlock(Block block) {
		return auctions.atChest(block) != null || auctions.atSign(block) != null;
	}

	static boolean mayDiscard(Player player) {
		return player.getGameMode() == GameMode.CREATIVE && player.hasPermission("titanmc.auction.admin");
	}
}
