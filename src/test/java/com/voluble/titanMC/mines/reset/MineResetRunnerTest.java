package com.voluble.titanMC.mines.reset;

import com.voluble.titanMC.mines.Mine;
import com.voluble.titanMC.mines.WeightedPalette;
import com.voluble.titanMC.util.RegionUtils;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineResetRunnerTest {

	private ServerMock server;

	@BeforeEach
	void startServer() {
		server = MockBukkit.mock();
	}

	@AfterEach
	void stopServer() {
		MockBukkit.unmock();
	}

	@Test
	void skipsBlockWritesWhenThePaletteAlreadySelectedTheExistingMaterial() {
		var world = server.addSimpleWorld("reset");
		world.getBlockAt(0, 64, 0).setType(Material.STONE);
		world.getBlockAt(1, 64, 0).setType(Material.DIRT);
		world.getBlockAt(2, 64, 0).setType(Material.AIR);
		WeightedPalette palette = new WeightedPalette();
		palette.addOrUpdate(Material.STONE, 1);
		Mine mine = new Mine(
			"test",
			new RegionUtils.Cuboid(world.getUID(), 0, 64, 0, 2, 64, 0),
			900,
			true,
			1500,
			palette
		);
		MineResetRunner runner = new MineResetRunner(MockBukkit.createMockPlugin(), mine);

		MineResetWork work = runner.process(3, Long.MAX_VALUE);

		assertEquals(3, work.scannedBlocks());
		assertEquals(2, work.changedBlocks());
		assertTrue(work.finished());
		assertEquals(Material.STONE, world.getBlockAt(0, 64, 0).getType());
		assertEquals(Material.STONE, world.getBlockAt(1, 64, 0).getType());
		assertEquals(Material.STONE, world.getBlockAt(2, 64, 0).getType());
	}

	@Test
	void processesChunksSequentiallyCleansOnlyMineItemsAndReleasesTickets() {
		var world = server.addSimpleWorld("chunked_reset");
		var plugin = MockBukkit.createMockPlugin();
		for (int x = 0; x < 32; x++) {
			world.getBlockAt(x, 64, 0).setType(Material.DIRT);
		}
		var insideItem = world.dropItem(
			new Location(world, 20.5, 64.5, 0.5),
			new ItemStack(Material.DIAMOND)
		);
		var outsideItem = world.dropItem(
			new Location(world, 40.5, 64.5, 0.5),
			new ItemStack(Material.DIAMOND)
		);
		WeightedPalette palette = new WeightedPalette();
		palette.addOrUpdate(Material.STONE, 1);
		Mine mine = new Mine(
			"chunked",
			new RegionUtils.Cuboid(world.getUID(), 0, 64, 0, 31, 64, 0),
			900,
			true,
			8,
			palette
		);
		MineResetRunner runner = new MineResetRunner(plugin, mine);

		int changed = 0;
		for (int tick = 0; tick < 20 && !runner.isFinished(); tick++) {
			changed += runner.process(8, Long.MAX_VALUE).changedBlocks();
		}

		assertTrue(runner.isFinished());
		assertEquals(32, changed);
		assertTrue(insideItem.isDead());
		assertTrue(!outsideItem.isDead());
		assertTrue(world.getChunkAt(0, 0).getPluginChunkTickets().isEmpty());
		assertTrue(world.getChunkAt(1, 0).getPluginChunkTickets().isEmpty());
	}
}
