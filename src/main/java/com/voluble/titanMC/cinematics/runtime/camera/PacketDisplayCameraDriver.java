package com.voluble.titanMC.cinematics.runtime.camera;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.UUID;

final class PacketDisplayCameraDriver implements CinematicCameraDriver {
	private static final int DISPLAY_INTERPOLATION_TICKS = 3;
	private static final int CHUNK_SYNC_INTERVAL_TICKS = 10;
	private static final double MAX_PLAYER_DISTANCE_SQUARED = 25.0 * 25.0;

	private final Plugin plugin;
	private final Player player;
	private final UUID viewerId;
	private WrapperEntity camera;
	private boolean active;

	PacketDisplayCameraDriver(Plugin plugin, Player player) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.player = Objects.requireNonNull(player, "player");
		this.viewerId = player.getUniqueId();
	}

	@Override
	public void start(Location location) {
		if (active) return;
		active = true;
		Location cameraLocation = cameraLocation(location);
		player.teleport(location);
		camera = createCamera();
		camera.addViewer(viewerId);
		camera.spawn(toPacketLocation(cameraLocation));
		setCamera(camera.getEntityId());
	}

	@Override
	public void move(int frame, Location location) {
		if (!active || camera == null) {
			start(location);
			return;
		}
		Location cameraLocation = cameraLocation(location);
		camera.teleport(toPacketLocation(cameraLocation));
		syncPlayerForChunks(frame, location);
	}

	@Override
	public void stop() {
		if (!active) return;
		active = false;
		try {
			setCamera(player.getEntityId());
		} catch (Exception exception) {
			plugin.getLogger().warning("Failed to reset cinematic camera for " + player.getName() + ": " + exception.getMessage());
		}
		if (camera != null) {
			camera.remove();
			camera = null;
		}
	}

	private WrapperEntity createCamera() {
		WrapperEntity entity = new WrapperEntity(EntityTypes.TEXT_DISPLAY);
		if (entity.getEntityMeta() instanceof TextDisplayMeta meta) {
			meta.setNotifyAboutChanges(false);
			meta.setPositionRotationInterpolationDuration(DISPLAY_INTERPOLATION_TICKS);
			meta.setNotifyAboutChanges(true);
		}
		return entity;
	}

	private Location cameraLocation(Location location) {
		Location copy = location.clone();
		copy.add(0.0, player.getEyeHeight(), 0.0);
		return copy;
	}

	private void syncPlayerForChunks(int frame, Location location) {
		if (frame % CHUNK_SYNC_INTERVAL_TICKS != 0 && sameWorld(location) && player.getLocation().distanceSquared(location) <= MAX_PLAYER_DISTANCE_SQUARED) {
			return;
		}
		player.teleport(location);
	}

	private boolean sameWorld(Location location) {
		return player.getWorld().equals(location.getWorld());
	}

	private void setCamera(int entityId) {
		PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerCamera(entityId));
	}

	private com.github.retrooper.packetevents.protocol.world.Location toPacketLocation(Location location) {
		return new com.github.retrooper.packetevents.protocol.world.Location(
			location.getX(),
			location.getY(),
			location.getZ(),
			location.getYaw(),
			location.getPitch()
		);
	}
}
