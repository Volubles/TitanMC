package com.voluble.titanMC.progression.bukkit;

import com.voluble.titanMC.progression.model.CredAmount;
import com.voluble.titanMC.progression.model.CredSource;
import com.voluble.titanMC.progression.model.PolynomialLevelCurve;
import com.voluble.titanMC.progression.service.CredSourceRegistry;
import com.voluble.titanMC.progression.service.ProgressionEngine;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
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

class BlockBreakCredSourceTest {
	private static final CredSource MINING = CredSource.of("mining");

	@TempDir Path directory;
	private ServerMock server;
	private Plugin plugin;
	private ProgressionEngine engine;
	private CredSourceRegistry registry;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		plugin = MockBukkit.createMockPlugin();
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
	void awardsCredForBreakingMatchingBlock() {
		registry.register(MINING, "Mining", true);
		Map<Material, CredAmount> values = Map.of(Material.STONE, CredAmount.of(5L));
		BlockBreakCredSource listener = new BlockBreakCredSource(engine, registry, MINING, values);
		server.getPluginManager().registerEvents(listener, plugin);

		World world = server.addSimpleWorld("mine");
		Player player = server.addPlayer();
		Block block = world.getBlockAt(0, 64, 0);
		block.setType(Material.STONE);
		server.getPluginManager().callEvent(new BlockBreakEvent(block, player));

		assertEquals(5L, engine.current(player.getUniqueId()).totalCred());
	}

	@Test
	void ignoresBlockTypesNotInTable() {
		registry.register(MINING, "Mining", true);
		Map<Material, CredAmount> values = Map.of(Material.STONE, CredAmount.of(5L));
		BlockBreakCredSource listener = new BlockBreakCredSource(engine, registry, MINING, values);
		server.getPluginManager().registerEvents(listener, plugin);

		World world = server.addSimpleWorld("mine");
		Player player = server.addPlayer();
		Block block = world.getBlockAt(0, 64, 0);
		block.setType(Material.DIRT);
		server.getPluginManager().callEvent(new BlockBreakEvent(block, player));

		assertEquals(0L, engine.current(player.getUniqueId()).totalCred());
	}

	@Test
	void disabledSourceAwardsNothing() {
		registry.register(MINING, "Mining", false);
		Map<Material, CredAmount> values = Map.of(Material.STONE, CredAmount.of(5L));
		BlockBreakCredSource listener = new BlockBreakCredSource(engine, registry, MINING, values);
		server.getPluginManager().registerEvents(listener, plugin);

		World world = server.addSimpleWorld("mine");
		Player player = server.addPlayer();
		Block block = world.getBlockAt(0, 64, 0);
		block.setType(Material.STONE);
		server.getPluginManager().callEvent(new BlockBreakEvent(block, player));

		assertEquals(0L, engine.current(player.getUniqueId()).totalCred());
	}

	@Test
	void creativePlayersDoNotEarnCred() {
		registry.register(MINING, "Mining", true);
		Map<Material, CredAmount> values = Map.of(Material.STONE, CredAmount.of(5L));
		BlockBreakCredSource listener = new BlockBreakCredSource(engine, registry, MINING, values);
		server.getPluginManager().registerEvents(listener, plugin);

		World world = server.addSimpleWorld("mine");
		Player player = server.addPlayer();
		player.setGameMode(GameMode.CREATIVE);
		Block block = world.getBlockAt(0, 64, 0);
		block.setType(Material.STONE);
		server.getPluginManager().callEvent(new BlockBreakEvent(block, player));

		assertEquals(0L, engine.current(player.getUniqueId()).totalCred());
	}
}
