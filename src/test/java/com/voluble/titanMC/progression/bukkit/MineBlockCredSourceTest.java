package com.voluble.titanMC.progression.bukkit;

import com.voluble.titanMC.mines.event.MineBlockMinedEvent;
import com.voluble.titanMC.progression.model.CredAmount;
import com.voluble.titanMC.progression.model.CredSource;
import com.voluble.titanMC.progression.model.PolynomialLevelCurve;
import com.voluble.titanMC.progression.service.CredSourceRegistry;
import com.voluble.titanMC.progression.service.MineBlockCredPolicy;
import com.voluble.titanMC.progression.service.ProgressionEngine;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MineBlockCredSourceTest {
	private static final CredSource MINING = CredSource.of("mining");

	@TempDir Path directory;
	private ServerMock server;
	private Plugin plugin;
	private ProgressionEngine engine;
	private CredSourceRegistry registry;
	private World world;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		plugin = MockBukkit.createMockPlugin();
		world = server.addSimpleWorld("mine");
		Logger logger = Logger.getAnonymousLogger();
		logger.setLevel(Level.OFF);
		engine = ProgressionEngine.open(
			directory.resolve("progression.db"),
			new PolynomialLevelCurve(100.0, 1.0),
			100,
			event -> {},
			logger
		);
		registry = new CredSourceRegistry();
	}

	@AfterEach
	void tearDown() throws Exception {
		engine.close();
		MockBukkit.unmock();
	}

	@Test
	void awardsCredForMinedMineBlockWithMatchingMaterial() {
		registry.register(MINING, "Mining", true);
		MineBlockCredSource listener = listener(Map.of(Material.STONE, CredAmount.of(5L)));
		server.getPluginManager().registerEvents(listener, plugin);

		Player player = server.addPlayer();
		server.getPluginManager().callEvent(mined(player, Material.STONE));

		assertEquals(5L, engine.current(player.getUniqueId()).totalCred());
	}

	@Test
	void ignoresMineBlockMaterialsNotInTable() {
		registry.register(MINING, "Mining", true);
		MineBlockCredSource listener = listener(Map.of(Material.STONE, CredAmount.of(5L)));
		server.getPluginManager().registerEvents(listener, plugin);

		Player player = server.addPlayer();
		server.getPluginManager().callEvent(mined(player, Material.DIRT));

		assertEquals(0L, engine.current(player.getUniqueId()).totalCred());
	}

	@Test
	void disabledSourceAwardsNothing() {
		registry.register(MINING, "Mining", false);
		MineBlockCredSource listener = listener(Map.of(Material.STONE, CredAmount.of(5L)));
		server.getPluginManager().registerEvents(listener, plugin);

		Player player = server.addPlayer();
		server.getPluginManager().callEvent(mined(player, Material.STONE));

		assertEquals(0L, engine.current(player.getUniqueId()).totalCred());
	}

	@Test
	void creativePlayersDoNotEarnCred() {
		registry.register(MINING, "Mining", true);
		MineBlockCredSource listener = listener(Map.of(Material.STONE, CredAmount.of(5L)));
		server.getPluginManager().registerEvents(listener, plugin);

		Player player = server.addPlayer();
		player.setGameMode(GameMode.CREATIVE);
		server.getPluginManager().callEvent(mined(player, Material.STONE));

		assertEquals(0L, engine.current(player.getUniqueId()).totalCred());
	}

	private MineBlockCredSource listener(Map<Material, CredAmount> values) {
		return new MineBlockCredSource(engine, registry, MINING, new MineBlockCredPolicy(values));
	}

	private MineBlockMinedEvent mined(Player player, Material material) {
		return new MineBlockMinedEvent(player, "test_mine", material, new Location(world, 0, 64, 0));
	}
}
