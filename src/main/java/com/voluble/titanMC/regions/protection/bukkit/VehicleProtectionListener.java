package com.voluble.titanMC.regions.protection.bukkit;

import com.voluble.titanMC.regions.protection.model.ProtectionAction;
import com.voluble.titanMC.regions.protection.service.ProtectionService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.entity.EntityPlaceEvent;

import java.util.Objects;

public final class VehicleProtectionListener implements Listener {

	private final ProtectionService protection;

	public VehicleProtectionListener(ProtectionService protection) {
		this.protection = Objects.requireNonNull(protection, "protection");
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onVehiclePlace(EntityPlaceEvent event) {
		if (!(event.getEntity() instanceof org.bukkit.entity.Vehicle) || event.getPlayer() == null) return;
		if (!allowed(event.getPlayer(), ProtectionAction.VEHICLE_PLACE, event.getEntity().getLocation())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onVehicleEnter(VehicleEnterEvent event) {
		if (!(event.getEntered() instanceof Player player)) return;
		if (!allowed(player, ProtectionAction.VEHICLE_ENTER, event.getVehicle().getLocation())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onVehiclePush(VehicleEntityCollisionEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;
		if (!allowed(player, ProtectionAction.VEHICLE_MODIFY, event.getVehicle().getLocation())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onVehicleDamage(VehicleDamageEvent event) {
		Player player = event.getAttacker() == null
			? null
			: EntityInteractionProtectionListener.responsiblePlayer(event.getAttacker());
		if (player == null) return;
		if (!allowed(player, ProtectionAction.VEHICLE_MODIFY, event.getVehicle().getLocation())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onVehicleDestroy(VehicleDestroyEvent event) {
		Player player = event.getAttacker() == null
			? null
			: EntityInteractionProtectionListener.responsiblePlayer(event.getAttacker());
		if (player == null) return;
		if (!allowed(player, ProtectionAction.VEHICLE_MODIFY, event.getVehicle().getLocation())) {
			event.setCancelled(true);
		}
	}

	private boolean allowed(Player player, ProtectionAction action, org.bukkit.Location location) {
		return protection.allowed(BukkitProtectionMapper.request(player, action, location));
	}
}
