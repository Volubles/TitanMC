package com.voluble.titanMC.mines.listeners;

import com.voluble.titanMC.mines.Mine;
import com.voluble.titanMC.mines.WeightedPalette;
import com.voluble.titanMC.mines.event.MineBlockMinedEvent;
import com.voluble.titanMC.util.RegionUtils;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MineBlockListenerTest {
	private ServerMock server;
	private Plugin plugin;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		plugin = MockBukkit.createMockPlugin();
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void successfulMineBreakPublishesMinedEventAndUpdatesDepletion() {
		World world = server.addSimpleWorld("mine");
		Mine mine = mineAt(world, "mine_a");
		List<String> depletionResets = new ArrayList<>();
		List<MineBlockMinedEvent> minedEvents = new ArrayList<>();
		server.getPluginManager().registerEvents(new MineBlockListener(plugin, location -> mine, depletionResets::add), plugin);
		server.getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onMineBlockMined(MineBlockMinedEvent event) {
				minedEvents.add(event);
			}
		}, plugin);

		Player player = server.addPlayer();
		Block block = world.getBlockAt(0, 64, 0);
		block.setType(Material.STONE);
		server.getPluginManager().callEvent(new BlockBreakEvent(block, player));

		assertEquals(1, mine.getBrokenBlocks());
		assertEquals(List.of("mine_a"), depletionResets);
		assertEquals(1, minedEvents.size());
		MineBlockMinedEvent mined = minedEvents.getFirst();
		assertEquals(player, mined.player());
		assertEquals("mine_a", mined.mineName());
		assertEquals(Material.STONE, mined.material());
		assertEquals(0.0, mined.location().getX());
		assertEquals(64.0, mined.location().getY());
		assertEquals(0.0, mined.location().getZ());
	}

	private static Mine mineAt(World world, String name) {
		WeightedPalette palette = new WeightedPalette();
		palette.addOrUpdate(Material.STONE, 1);
		Mine mine = new Mine(
			name,
			new RegionUtils.Cuboid(world.getUID(), 0, 64, 0, 0, 64, 0),
			900,
			true,
			1500,
			palette
		);
		mine.setAutoResetBelowPercent(99);
		return mine;
	}
}
