package com.voluble.titanMC.mines.event;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MineBlockMinedEventTest {
	private ServerMock server;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void locationIsDefensivelyCopied() {
		World world = server.addSimpleWorld("mine");
		Player player = server.addPlayer();
		Location original = new Location(world, 1, 2, 3);

		MineBlockMinedEvent event = new MineBlockMinedEvent(player, "mine_a", Material.STONE, original, 1.25D);
		original.setX(99);
		Location returned = event.location();
		returned.setY(88);

		assertEquals(1.0, event.location().getX());
		assertEquals(2.0, event.location().getY());
		assertEquals(3.0, event.location().getZ());
		assertEquals(1.25D, event.credMultiplier());
	}
}
