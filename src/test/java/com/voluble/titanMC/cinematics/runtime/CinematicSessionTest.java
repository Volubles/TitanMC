package com.voluble.titanMC.cinematics.runtime;

import com.voluble.titanMC.cinematics.model.CameraPathDefinition;
import com.voluble.titanMC.cinematics.model.CameraPoint;
import com.voluble.titanMC.cinematics.model.CinematicDefinition;
import com.voluble.titanMC.cinematics.model.CinematicId;
import com.voluble.titanMC.cinematics.model.CinematicTimeline;
import com.voluble.titanMC.cinematics.model.HeadCinematicEvent;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CinematicSessionTest {
	private ServerMock server;
	private Plugin plugin;
	private World world;
	private Player player;
	private CinematicScreenEffects screenEffects;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		plugin = MockBukkit.createMockPlugin();
		world = server.addSimpleWorld("cinematic_session");
		player = server.addPlayer();
		screenEffects = (viewer, request) -> true;
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void defaultPlaybackStopsWhenTimelineFinishes() {
		AtomicInteger completions = new AtomicInteger();
		CinematicSession session = new CinematicSession(plugin, player, definition(), ignored -> completions.incrementAndGet(), screenEffects);

		session.start();
		server.getScheduler().performTicks(5L);

		assertEquals(1, completions.get());
	}

	@Test
	void holdLastFrameKeepsSessionActiveUntilStopped() {
		AtomicInteger completions = new AtomicInteger();
		AtomicInteger holds = new AtomicInteger();
		CinematicSession session = new CinematicSession(
			plugin,
			player,
			definition(),
			CinematicPlaybackOptions.holdLastFrame().withHoldCallback(holds::incrementAndGet),
			ignored -> completions.incrementAndGet(),
			screenEffects
		);

		session.start();
		server.getScheduler().performTicks(5L);

		assertEquals(1, holds.get());
		assertEquals(0, completions.get());
		assertEquals(10.0, player.getLocation().getX(), 0.001);

		session.stop(true);

		assertEquals(1, completions.get());
	}

	@Test
	void cameraStartsAtFirstCameraTick() {
		CinematicSession session = new CinematicSession(plugin, player, delayedDefinition(), ignored -> { }, screenEffects);

		session.start();
		server.getScheduler().performTicks(2L);

		assertEquals(0.0, player.getLocation().getX(), 0.001);

		server.getScheduler().performTicks(3L);

		assertEquals(10.0, player.getLocation().getX(), 0.001);
	}

	@Test
	void restoresOriginalHelmetAfterHeadEvent() {
		player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
		CinematicSession session = new CinematicSession(plugin, player, headDefinition(), ignored -> { }, screenEffects);

		session.start();
		server.getScheduler().performTicks(1L);

		assertEquals(Material.CARVED_PUMPKIN, player.getInventory().getHelmet().getType());

		session.stop(true);

		assertEquals(Material.DIAMOND_HELMET, player.getInventory().getHelmet().getType());
	}

	private CinematicDefinition definition() {
		return new CinematicDefinition(
			CinematicId.of("test"),
			2,
			new CameraPathDefinition(true, List.of(
				point(0, 0, 0.0),
				point(2, 1, 10.0)
			)),
			new CinematicTimeline(List.of())
		);
	}

	private CinematicDefinition delayedDefinition() {
		return new CinematicDefinition(
			CinematicId.of("delayed"),
			4,
			new CameraPathDefinition(true, List.of(
				point(3, 0, 10.0),
				point(4, 1, 10.0)
			)),
			new CinematicTimeline(List.of())
		);
	}

	private CinematicDefinition headDefinition() {
		return new CinematicDefinition(
			CinematicId.of("head"),
			20,
			new CameraPathDefinition(true, List.of(
				point(0, 0, 0.0),
				point(20, 1, 10.0)
			)),
			new CinematicTimeline(List.of(new HeadCinematicEvent(0, 0, 1, "carved_pumpkin")))
		);
	}

	private CameraPoint point(int tick, int slot, double x) {
		return new CameraPoint(tick, slot, world.getName(), x, 64.0, 0.0, 0.0f, 0.0f);
	}
}
