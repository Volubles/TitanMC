package com.voluble.titanMC.mines.storage;

import com.voluble.titanMC.mines.Mine;
import com.voluble.titanMC.mines.MineResetDefinition;
import com.voluble.titanMC.mines.breaking.MineBreakProfile;
import com.voluble.titanMC.mines.WeightedPalette;
import com.voluble.titanMC.util.RegionUtils;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MineStorageTest {

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
	void persistsTheLatestSnapshotAndDeletionInOrder() {
		var world = server.addSimpleWorld("mines");
		var palette = new WeightedPalette();
		palette.addOrUpdate(Material.STONE, 1);
		var mine = new Mine(
			"alpha",
			new RegionUtils.Cuboid(world.getUID(), 0, 0, 0, 1, 1, 1),
			60,
			true,
			100,
			palette
		);
		mine.setResetDefinition(new MineResetDefinition.Template("woodfarm_v1"));
		mine.setBreakProfile(new MineBreakProfile.AllowList(Set.of(Material.OAK_LOG, Material.OAK_STAIRS)));
		mine.setCredMultiplier(1.35D);

		try (MineStorage storage = new MineStorage(plugin)) {
			storage.saveMine(mine);
			mine.setResetIntervalSeconds(120);
			storage.saveMine(mine);
			storage.flush();

			try (MineStorage reader = new MineStorage(plugin)) {
				Map<String, Mine> loaded = reader.loadAll();
				assertEquals(120, loaded.get("alpha").getResetIntervalSeconds());
				assertEquals(1.35D, loaded.get("alpha").getCredMultiplier());
				assertEquals(
					"woodfarm_v1",
					((MineResetDefinition.Template) loaded.get("alpha").getResetDefinition()).templateId()
				);
				assertEquals(
					Set.of(Material.OAK_LOG, Material.OAK_STAIRS),
					((MineBreakProfile.AllowList) loaded.get("alpha").getBreakProfile()).materials()
				);
			}

			storage.deleteMine("alpha");
		}
		try (MineStorage reader = new MineStorage(plugin)) {
			assertFalse(reader.loadAll().containsKey("alpha"));
		}
	}

	@Test
	void missingCredMultiplierDefaultsToOne() {
		var world = server.addSimpleWorld("legacy");
		var configuration = new org.bukkit.configuration.file.YamlConfiguration();
		var mines = configuration.createSection("mines");
		var alpha = mines.createSection("alpha");
		alpha.set("world", world.getUID().toString());
		alpha.set("min.x", 0);
		alpha.set("min.y", 0);
		alpha.set("min.z", 0);
		alpha.set("max.x", 1);
		alpha.set("max.y", 1);
		alpha.set("max.z", 1);
		alpha.set("palette.STONE", 1);
		try {
			java.io.File file = new java.io.File(plugin.getDataFolder(), "mines/mines.yml");
			java.nio.file.Files.createDirectories(file.toPath().getParent());
			configuration.save(file);
		} catch (java.io.IOException exception) {
			throw new IllegalStateException(exception);
		}

		try (MineStorage storage = new MineStorage(plugin)) {
			assertEquals(1.0D, storage.loadAll().get("alpha").getCredMultiplier());
		}
	}
}
