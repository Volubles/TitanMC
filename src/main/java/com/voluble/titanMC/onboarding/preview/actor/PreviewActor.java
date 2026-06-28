package com.voluble.titanMC.onboarding.preview.actor;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.voluble.titanMC.outfits.skin.SkinPropertyData;
import me.tofaa.entitylib.EntityLib;
import me.tofaa.entitylib.meta.types.PlayerMeta;
import me.tofaa.entitylib.wrapper.WrapperPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

public final class PreviewActor {
	private final Plugin plugin;
	private final Player viewer;
	private final UUID viewerId;
	private final UUID npcId;
	private final String npcName;
	private final String teamName;
	private final PreviewMotion motion;
	private final WrapperPlayer npc;
	private final CompletableFuture<Void> removed = new CompletableFuture<>();
	private CompletableFuture<Void> focused;
	private Location current;
	private PreviewActorState state = PreviewActorState.ENTERING;
	private BukkitTask task;

	public PreviewActor(
		Plugin plugin,
		Player viewer,
		SkinPropertyData skin,
		PreviewMotion motion
	) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.viewer = Objects.requireNonNull(viewer, "viewer");
		this.viewerId = viewer.getUniqueId();
		this.npcId = UUID.randomUUID();
		this.npcName = username(npcId);
		this.teamName = teamName(npcId);
		this.motion = Objects.requireNonNull(motion, "motion");
		this.npc = createNpc(Objects.requireNonNull(skin, "skin"));
	}

	public CompletableFuture<Void> enter(Location entrance, Location focus) {
		Objects.requireNonNull(entrance, "entrance");
		Objects.requireNonNull(focus, "focus");
		CompletableFuture<Void> result = new CompletableFuture<>();
		focused = result;
		state = PreviewActorState.ENTERING;
		spawnAt(entrance);
		moveTo(focus, true).whenComplete((ignored, failure) -> {
			if (failure != null) {
				result.completeExceptionally(failure);
				return;
			}
			if (state == PreviewActorState.REMOVED) return;
			current = focus.clone();
			state = PreviewActorState.FOCUSED;
			npc.teleport(toPacketLocation(current));
			rotateHead(current);
			result.complete(null);
		});
		return result;
	}

	public void stageAt(Location location) {
		state = PreviewActorState.STAGED;
		spawnAt(location);
		rotateHead(location);
	}

	public CompletableFuture<Void> stageAtLater(Location location, long delayTicks) {
		CompletableFuture<Void> result = new CompletableFuture<>();
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (state == PreviewActorState.REMOVED) {
				result.complete(null);
				return;
			}
			stageAt(location);
			result.complete(null);
		}, Math.max(0L, delayTicks));
		return result;
	}

	public CompletableFuture<Void> moveToFocus(Location focus) {
		if (state == PreviewActorState.REMOVED) return removed;
		state = PreviewActorState.ENTERING;
		return moveTo(focus, true).thenRun(() -> {
			if (state == PreviewActorState.REMOVED) return;
			current = focus.clone();
			state = PreviewActorState.FOCUSED;
			npc.teleport(toPacketLocation(current));
			rotateHead(current);
		});
	}

	public CompletableFuture<Void> moveToStage(Location stage) {
		if (state == PreviewActorState.REMOVED) return removed;
		state = PreviewActorState.EXITING;
		return moveTo(stage, true).thenRun(() -> {
			if (state == PreviewActorState.REMOVED) return;
			current = stage.clone();
			state = PreviewActorState.STAGED;
			npc.teleport(toPacketLocation(current));
			rotateHead(current);
		});
	}

	public CompletableFuture<Void> exitTo(Location target) {
		if (state == PreviewActorState.REMOVED || state == PreviewActorState.EXITING) return removed;
		cancelFocus("Preview actor started exiting before focus");
		cancelTask();
		state = PreviewActorState.EXITING;
		moveTo(target, true).thenRun(this::remove);
		return removed;
	}

	public void remove() {
		if (state == PreviewActorState.REMOVED) return;
		state = PreviewActorState.REMOVED;
		cancelFocus("Preview actor was removed before focus");
		cancelTask();
		try {
			npc.remove();
		} catch (Exception ignored) {
			// Preview cleanup should never interrupt onboarding teardown.
		} finally {
			removeHiddenNameTeam();
		}
		removed.complete(null);
	}

	private void cancelFocus(String message) {
		if (focused == null || focused.isDone()) return;
		focused.completeExceptionally(new CancellationException(message));
	}

	private WrapperPlayer createNpc(SkinPropertyData skin) {
		UserProfile profile = new UserProfile(
			npcId,
			npcName,
			List.of(new TextureProperty("textures", skin.value(), skin.signature()))
		);
		int entityId = EntityLib.getPlatform().getEntityIdProvider().provide(npcId, EntityTypes.PLAYER);
		WrapperPlayer player = new WrapperPlayer(profile, entityId);
		player.setGameMode(GameMode.SURVIVAL);
		player.setLatency(0);
		player.getEntityMeta().setCustomNameVisible(false);
		if (player.getEntityMeta() instanceof PlayerMeta meta) {
			meta.setCapeEnabled(true);
			meta.setJacketEnabled(true);
			meta.setLeftSleeveEnabled(true);
			meta.setRightSleeveEnabled(true);
			meta.setLeftLegEnabled(true);
			meta.setRightLegEnabled(true);
			meta.setHatEnabled(true);
		}
		return player;
	}

	private void spawnAt(Location location) {
		if (current != null && npc.isSpawned()) {
			current = location.clone();
			npc.teleport(toPacketLocation(current));
			return;
		}
		current = location.clone();
		npc.spawn(toPacketLocation(current));
		createHiddenNameTeam();
		npc.addViewer(viewerId);
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (state != PreviewActorState.REMOVED) npc.setInTablist(false);
		}, 10L);
	}

	private CompletableFuture<Void> moveTo(Location target, boolean rotateDuringMove) {
		CompletableFuture<Void> result = new CompletableFuture<>();
		Location start = current == null ? target.clone() : current.clone();
		playSegment(start, target, rotateDuringMove, () -> result.complete(null));
		return result;
	}

	private void createHiddenNameTeam() {
		Component empty = Component.empty();
		WrapperPlayServerTeams.ScoreBoardTeamInfo info = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
			empty,
			empty,
			empty,
			WrapperPlayServerTeams.NameTagVisibility.NEVER,
			WrapperPlayServerTeams.CollisionRule.NEVER,
			NamedTextColor.WHITE,
			WrapperPlayServerTeams.OptionData.NONE
		);
		PacketEvents.getAPI().getPlayerManager().sendPacket(
			viewer,
			new WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.CREATE, info, npcName)
		);
	}

	private void removeHiddenNameTeam() {
		PacketEvents.getAPI().getPlayerManager().sendPacket(
			viewer,
			new WrapperPlayServerTeams(teamName, WrapperPlayServerTeams.TeamMode.REMOVE, Optional.empty(), List.of())
		);
	}

	private void playSegment(Location start, Location target, boolean rotateDuringMove, Runnable complete) {
		cancelTask();
		int duration = motion.ticksBetween(start, target);
		if (duration == 0) {
			current = target.clone();
			complete.run();
			return;
		}
		task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
			private int tick;

			@Override
			public void run() {
				if (!viewer.isOnline() || state == PreviewActorState.REMOVED) {
					remove();
					return;
				}
				tick++;
				Location next = interpolate(start, target, Math.min(1.0, tick / (double) duration));
				sendMovement(next, rotateDuringMove);
				current = next;
				if (tick >= duration) {
					cancelTask();
					complete.run();
				}
			}
		}, 1L, 1L);
	}

	private void sendMovement(Location next, boolean rotate) {
		Location previous = current == null ? next : current;
		double deltaX = next.getX() - previous.getX();
		double deltaY = next.getY() - previous.getY();
		double deltaZ = next.getZ() - previous.getZ();
		npc.setLocation(toPacketLocation(next));
		if (rotate) {
			npc.sendPacketToViewersIfSpawned(new WrapperPlayServerEntityRelativeMoveAndRotation(
				npc.getEntityId(), deltaX, deltaY, deltaZ, next.getYaw(), next.getPitch(), true
			));
			rotateHead(next);
			return;
		}
		npc.sendPacketToViewersIfSpawned(new WrapperPlayServerEntityRelativeMove(
			npc.getEntityId(), deltaX, deltaY, deltaZ, true
		));
	}

	private Location interpolate(Location start, Location target, double progress) {
		double eased = easeInOut(progress);
		double rotation = rotationProgress(progress);
		Location next = start.clone();
		next.setX(lerp(start.getX(), target.getX(), eased));
		next.setY(lerp(start.getY(), target.getY(), eased));
		next.setZ(lerp(start.getZ(), target.getZ(), eased));
		next.setYaw(lerpYaw(start.getYaw(), target.getYaw(), rotation));
		next.setPitch((float) lerp(start.getPitch(), target.getPitch(), rotation));
		return next;
	}

	private void rotateHead(Location location) {
		npc.rotateHead(toPacketLocation(location));
	}

	private void cancelTask() {
		if (task == null) return;
		task.cancel();
		task = null;
	}

	private static double lerp(double start, double end, double progress) {
		return start + (end - start) * progress;
	}

	private double rotationProgress(double progress) {
		if (state != PreviewActorState.ENTERING) return easeInOut(progress);
		double delayed = (progress - 0.55) / 0.45;
		return easeInOut(Math.max(0.0, Math.min(1.0, delayed)));
	}

	private static float lerpYaw(float start, float end, double progress) {
		float delta = ((end - start + 540.0F) % 360.0F) - 180.0F;
		return (float) (start + delta * progress);
	}

	private static double easeInOut(double progress) {
		if (progress <= 0.0) return 0.0;
		if (progress >= 1.0) return 1.0;
		return progress * progress * (3.0 - 2.0 * progress);
	}

	private static String username(UUID uuid) {
		return "TMC" + uuid.toString().replace("-", "").substring(0, 10);
	}

	private static String teamName(UUID uuid) {
		return "tmc_ob_" + uuid.toString().replace("-", "").substring(0, 8);
	}

	private static com.github.retrooper.packetevents.protocol.world.Location toPacketLocation(Location location) {
		return new com.github.retrooper.packetevents.protocol.world.Location(
			location.getX(),
			location.getY(),
			location.getZ(),
			location.getYaw(),
			location.getPitch()
		);
	}
}
