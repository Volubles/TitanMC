package com.voluble.titanMC.cells;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class CellSignService implements Listener {
	private final CellManager cells;private final CellRentalService rentals;private final CellSignRenderer renderer;private final NamespacedKey cellKey;
	public CellSignService(Plugin plugin,CellManager cells,CellRentalService rentals,CellSignRenderer renderer){this.cells=cells;this.rentals=rentals;this.renderer=renderer;cellKey=new NamespacedKey(plugin,"cell_rental");}
	public void bind(Sign sign,String cellId){var cell=java.util.Objects.requireNonNull(cells.get(cellId),"Unknown cell: "+cellId);var location=sign.getLocation();var binding=new com.voluble.titanMC.cells.model.CellSign(cell.id(),location.getWorld().getUID(),location.getBlockX(),location.getBlockY(),location.getBlockZ());cells.registerSign(binding);sign.getPersistentDataContainer().set(cellKey,PersistentDataType.STRING,cell.id());renderer.render(sign,cell);}
	@EventHandler(ignoreCancelled=true)public void onInteract(PlayerInteractEvent event){if(event.getAction()!=Action.RIGHT_CLICK_BLOCK||event.getClickedBlock()==null||!(event.getClickedBlock().getState() instanceof Sign sign))return;String cellId=sign.getPersistentDataContainer().get(cellKey,PersistentDataType.STRING);if(cellId==null)return;event.setCancelled(true);rentals.rent(event.getPlayer(),cellId);}
}
