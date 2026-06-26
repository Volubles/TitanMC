package com.voluble.titanMC.progression.bukkit;

import com.voluble.titanMC.display.message.DisplayBroadcastService;
import com.voluble.titanMC.progression.config.NotificationConfig;
import com.voluble.titanMC.progression.event.PlayerLeveledUpEvent;
import com.voluble.titanMC.progression.model.PlayerProgression;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LevelUpNotifierTest {
	private ServerMock server;
	private Plugin plugin;
	private NotificationConfig config;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		plugin = MockBukkit.createMockPlugin();
		config = new NotificationConfig(
			List.of("<green>Level up: {level}"),
			List.of("<gold>{player} hit {level}"),
			5,
			Optional.empty(),       // skip sound to keep MockBukkit happy
			Optional.empty(),
			Map.of()
		);
		server.getPluginManager().registerEvents(
			new LevelUpNotifier(server, DisplayBroadcastService.create(server), () -> config), plugin
		);
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void playerReceivesMessageOnEveryLevelUp() {
		Player player = server.addPlayer();
		PlayerProgression progression = new PlayerProgression(player.getUniqueId(), 200L, 3, 1L);

		server.getPluginManager().callEvent(
			new PlayerLeveledUpEvent(player.getUniqueId(), 2, 3, progression)
		);

		Component message = ((org.mockbukkit.mockbukkit.entity.PlayerMock) player).nextComponentMessage();
		assertNotNull(message);
		String plain = PlainTextComponentSerializer.plainText().serialize(message);
		assertEquals("Level up: 3", plain);
	}

	@Test
	void noBroadcastBetweenMilestones() {
		Player player = server.addPlayer();
		Player observer = server.addPlayer("observer");
		PlayerProgression progression = new PlayerProgression(player.getUniqueId(), 300L, 4, 1L);

		server.getPluginManager().callEvent(
			new PlayerLeveledUpEvent(player.getUniqueId(), 3, 4, progression)
		);

		// Observer should not receive any message.
		Component observerMessage = ((org.mockbukkit.mockbukkit.entity.PlayerMock) observer).nextComponentMessage();
		assertNull(observerMessage);
	}

	@Test
	void broadcastOnEveryFifthLevel() {
		Player player = server.addPlayer();
		Player observer = server.addPlayer("observer");
		PlayerProgression progression = new PlayerProgression(player.getUniqueId(), 400L, 5, 1L);

		server.getPluginManager().callEvent(
			new PlayerLeveledUpEvent(player.getUniqueId(), 4, 5, progression)
		);

		// Observer receives the broadcast.
		Component observerMessage = ((org.mockbukkit.mockbukkit.entity.PlayerMock) observer).nextComponentMessage();
		assertNotNull(observerMessage);
		String plain = PlainTextComponentSerializer.plainText().serialize(observerMessage);
		assertEquals(player.getName() + " hit 5", plain);
	}

	@Test
	void milestoneLevelSendsOnlyBroadcastToLevelingPlayer() {
		Player player = server.addPlayer();
		PlayerProgression progression = new PlayerProgression(player.getUniqueId(), 900L, 10, 1L);

		server.getPluginManager().callEvent(
			new PlayerLeveledUpEvent(player.getUniqueId(), 9, 10, progression)
		);

		var playerMock = (org.mockbukkit.mockbukkit.entity.PlayerMock) player;
		Component first = playerMock.nextComponentMessage();
		assertNotNull(first);
		assertEquals(player.getName() + " hit 10", PlainTextComponentSerializer.plainText().serialize(first));
		assertNull(playerMock.nextComponentMessage());
	}

	@Test
	void broadcastCanRenderMultipleLines() {
		Player player = server.addPlayer();
		Player observer = server.addPlayer("observer");
		config = new NotificationConfig(
			List.of("<green>Level up: {level}"),
			List.of("", "<gold>{player}</gold>", "<yellow>Level {level}</yellow>"),
			5,
			Optional.empty(),
			Optional.empty(),
			Map.of()
		);
		PlayerProgression progression = new PlayerProgression(player.getUniqueId(), 400L, 5, 1L);

		server.getPluginManager().callEvent(
			new PlayerLeveledUpEvent(player.getUniqueId(), 4, 5, progression)
		);

		var observerMock = (org.mockbukkit.mockbukkit.entity.PlayerMock) observer;
		assertEquals("", PlainTextComponentSerializer.plainText().serialize(observerMock.nextComponentMessage()));
		assertEquals(player.getName(), PlainTextComponentSerializer.plainText().serialize(observerMock.nextComponentMessage()));
		assertEquals("Level 5", PlainTextComponentSerializer.plainText().serialize(observerMock.nextComponentMessage()));
	}
}
