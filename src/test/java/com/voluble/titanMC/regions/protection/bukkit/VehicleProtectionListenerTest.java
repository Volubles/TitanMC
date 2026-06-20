package com.voluble.titanMC.regions.protection.bukkit;

import com.voluble.titanMC.regions.protection.model.ProtectionAction;
import com.voluble.titanMC.regions.protection.model.ProtectionDecision;
import com.voluble.titanMC.regions.protection.model.ProtectionRequest;
import com.voluble.titanMC.regions.protection.policy.ProtectionBypass;
import com.voluble.titanMC.regions.protection.policy.RegionPolicyRegistry;
import com.voluble.titanMC.regions.protection.service.ProtectionService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleProtectionListenerTest extends MockBukkitProtectionTestSupport {

	private Player player;
	private Vehicle vehicle;
	private VehicleProtectionListener listener;
	private List<ProtectionRequest> requests;

	@BeforeEach
	void createFixtures() {
		World world = server.addSimpleWorld("vehicle_protection");
		player = server.addPlayer();
		vehicle = (Vehicle) world.spawnEntity(new Location(world, 4.5, 64, 6.5), EntityType.MINECART);
		requests = new ArrayList<>();
		ProtectionService service = new ProtectionService(
			(worldId, x, y, z) -> List.of(),
			RegionPolicyRegistry.builder().build(),
			request -> {
				requests.add(request);
				return ProtectionDecision.DENY;
			},
			ProtectionBypass.none()
		);
		listener = new VehicleProtectionListener(service);
	}

	@Test
	void deniesPlayersPlacingVehicles() {
		EntityPlaceEvent event = new EntityPlaceEvent(
			vehicle, player, vehicle.getLocation().getBlock(), BlockFace.UP
		);

		listener.onVehiclePlace(event);

		assertTrue(event.isCancelled());
		assertRequest(ProtectionAction.VEHICLE_PLACE);
	}

	@Test
	void deniesPlayersEnteringVehicles() {
		VehicleEnterEvent event = new VehicleEnterEvent(vehicle, player);

		listener.onVehicleEnter(event);

		assertTrue(event.isCancelled());
		assertRequest(ProtectionAction.VEHICLE_ENTER);
	}

	@Test
	void deniesPlayersDamagingVehicles() {
		VehicleDamageEvent event = new VehicleDamageEvent(vehicle, player, 1.0);

		listener.onVehicleDamage(event);

		assertTrue(event.isCancelled());
		assertRequest(ProtectionAction.VEHICLE_MODIFY);
	}

	@Test
	void deniesPlayersDestroyingVehicles() {
		VehicleDestroyEvent event = new VehicleDestroyEvent(vehicle, player);

		listener.onVehicleDestroy(event);

		assertTrue(event.isCancelled());
		assertRequest(ProtectionAction.VEHICLE_MODIFY);
	}

	@Test
	void attributesProjectileVehicleDamageToItsPlayerShooter() {
		Projectile arrow = (Projectile) vehicle.getWorld().spawnEntity(
			vehicle.getLocation(),
			EntityType.ARROW
		);
		arrow.setShooter(player);
		VehicleDamageEvent event = new VehicleDamageEvent(vehicle, arrow, 1.0);

		listener.onVehicleDamage(event);

		assertTrue(event.isCancelled());
		assertRequest(ProtectionAction.VEHICLE_MODIFY);
	}

	@Test
	void attributesProjectileVehicleDestructionToItsPlayerShooter() {
		Projectile arrow = (Projectile) vehicle.getWorld().spawnEntity(
			vehicle.getLocation(),
			EntityType.ARROW
		);
		arrow.setShooter(player);
		VehicleDestroyEvent event = new VehicleDestroyEvent(vehicle, arrow);

		listener.onVehicleDestroy(event);

		assertTrue(event.isCancelled());
		assertRequest(ProtectionAction.VEHICLE_MODIFY);
	}

	private void assertRequest(ProtectionAction action) {
		assertEquals(1, requests.size());
		ProtectionRequest request = requests.getFirst();
		assertEquals(action, request.action());
		assertEquals(4, request.target().x());
		assertEquals(64, request.target().y());
		assertEquals(6, request.target().z());
	}
}
