package com.voluble.titanMC.mines.reset;

import com.voluble.titanMC.mines.Mine;
import com.voluble.titanMC.mines.MineResetDefinition;
import com.voluble.titanMC.mines.WeightedPalette;
import com.voluble.titanMC.mines.template.MineTemplate;
import com.voluble.titanMC.mines.template.MineTemplateStorage;
import com.voluble.titanMC.util.RegionUtils;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateMineResetTaskTest {
	@TempDir Path directory;
	private ServerMock server;

	@BeforeEach void setUp() { server = MockBukkit.mock(); }
	@AfterEach void tearDown() { MockBukkit.unmock(); }

	@Test
	void restoresCapturedBlockDataAndAir() throws Exception {
		var plugin = MockBukkit.createMockPlugin();
		var world = server.addSimpleWorld("template-reset");
		Mine mine = new Mine(
			"woodfarm", new RegionUtils.Cuboid(world.getUID(), 0, 64, 0, 1, 64, 0),
			900, true, 10, new WeightedPalette()
		);
		mine.setResetDefinition(new MineResetDefinition.Template("woodfarm_v1"));

		try (MineTemplateStorage storage = new MineTemplateStorage(directory)) {
			storage.save(new MineTemplate(
				"woodfarm_v1", 2, 1, 1,
				List.of("minecraft:oak_log[axis=x]", "minecraft:air"), new int[]{0, 1}
			)).join();
			world.getBlockAt(0, 64, 0).setType(Material.STONE);
			world.getBlockAt(1, 64, 0).setType(Material.STONE);
			MineResetTask task = new MineResetTaskFactory(plugin, storage).create(mine);
			MineResetWork work;
			do {
				work = task.process(10, Long.MAX_VALUE);
				if (!work.finished()) Thread.sleep(1L);
			} while (!work.finished());

			assertTrue(task.successful());
			assertEquals("minecraft:oak_log[axis=x]", world.getBlockAt(0, 64, 0).getBlockData().getAsString());
			assertEquals(Material.AIR, world.getBlockAt(1, 64, 0).getType());
		}
	}
}
