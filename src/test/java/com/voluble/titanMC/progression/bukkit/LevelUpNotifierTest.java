package com.voluble.titanMC.progression.bukkit;

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
			"<green>Level up: {level}",
			"<gold>{player} hit {level}",
			5,
			Optional.empty(),       // skip sound to keep MockBukkit happy
			Optional.empty(),
			Map.of()
		);
		server.getPluginManager().registerEvents(new LevelUpNotifier(server, () -> config), plugin);
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
		assertEquals("Level up: 3", PlainTextComponentSerializer.plainText().serialize(message));
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
		assertEquals(player.getName() + " hit 5",
			PlainTextComponentSerializer.plainText().serialize(observerMessage));
	}
}
