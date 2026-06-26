package com.voluble.titanMC.progression.bukkit;

import com.voluble.titanMC.progression.model.CredAmount;
import com.voluble.titanMC.progression.model.CredSource;
import com.voluble.titanMC.progression.model.PolynomialLevelCurve;
import com.voluble.titanMC.progression.service.ProgressionEngine;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionBarServiceTest {
	private static final CredSource MINING = CredSource.of("mining");

	@TempDir Path directory;
	private ServerMock server;
	private Plugin plugin;
	private ProgressionEngine engine;
	private ProgressionBarService bars;
	private AtomicLong clock;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		plugin = MockBukkit.createMockPlugin();
		Logger logger = Logger.getAnonymousLogger();
		logger.setLevel(Level.OFF);
		engine = ProgressionEngine.open(
			directory.resolve("progression.db"),
			new PolynomialLevelCurve(100.0D, 1.0D),
			100,
			event -> server.getPluginManager().callEvent(event),
			logger
		);
		clock = new AtomicLong(1_000L);
		bars = new ProgressionBarService(plugin, engine, clock::get);
		bars.start();
		server.getPluginManager().registerEvents(bars, plugin);
	}

	@AfterEach
	void tearDown() throws Exception {
		if (bars != null) bars.close();
		if (engine != null) engine.close();
		MockBukkit.unmock();
	}

	@Test
	void showDisplaysCurrentCredProgress() {
		Player player = server.addPlayer();

		bars.show(player);

		BossBar bar = onlyBossBar(player);
		assertEquals("Cred Level 1 -> 2 | 0% | 100 cred left", title(bar));
		assertEquals(0.0F, bar.progress());
	}

	@Test
	void activeBarUpdatesWhenCredIsGranted() {
		Player player = server.addPlayer();
		bars.show(player);

		clock.addAndGet(UPDATE_WINDOW());
		engine.give(player.getUniqueId(), CredAmount.of(150L), MINING);

		BossBar bar = onlyBossBar(player);
		assertEquals("Cred Level 2 -> 3 | 50% | 50 cred left", title(bar));
		assertEquals(0.5F, bar.progress());
	}

	@Test
	void barExpiresAfterRequestedDuration() {
		Player player = server.addPlayer();
		bars.show(player, Duration.ofSeconds(1));

		clock.addAndGet(1_500L);
		server.getScheduler().performTicks(20L);

		assertTrue(bossBars(player).isEmpty());
	}

	private static long UPDATE_WINDOW() {
		return 600L;
	}

	private static BossBar onlyBossBar(Player player) {
		List<BossBar> bars = bossBars(player);
		assertEquals(1, bars.size());
		return bars.getFirst();
	}

	private static List<BossBar> bossBars(Player player) {
		List<BossBar> bars = new ArrayList<>();
		player.activeBossBars().forEach(bars::add);
		return bars;
	}

	private static String title(BossBar bar) {
		return PlainTextComponentSerializer.plainText().serialize(bar.name());
	}
}
