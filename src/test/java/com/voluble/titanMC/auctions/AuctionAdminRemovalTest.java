package com.voluble.titanMC.auctions;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionAdminRemovalTest {
	private ServerMock server;
	private Player player;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		player = server.addPlayer();
		player.addAttachment(MockBukkit.createMockPlugin(), "titanmc.auction.admin", true);
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void auctionAdminMayDiscardInCreativeMode() {
		player.setGameMode(GameMode.CREATIVE);

		assertTrue(AuctionListener.mayDiscard(player));
	}

	@Test
	void survivalModeAndMissingPermissionCannotDiscard() {
		assertFalse(AuctionListener.mayDiscard(player));

		player.setGameMode(GameMode.CREATIVE);
		player.addAttachment(MockBukkit.createMockPlugin(), "titanmc.auction.admin", false);

		assertFalse(AuctionListener.mayDiscard(player));
	}
}
