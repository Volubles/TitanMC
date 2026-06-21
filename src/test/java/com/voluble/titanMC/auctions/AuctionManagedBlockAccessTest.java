package com.voluble.titanMC.auctions;

import com.voluble.titanMC.regions.protection.model.ProtectionAction;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionManagedBlockAccessTest {
	private ServerMock server;
	private Player player;
	private Block sign;
	private Block chest;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		player = server.addPlayer();
		var world = server.addSimpleWorld("auction_access");
		sign = world.getBlockAt(1, 64, 1);
		sign.setType(Material.OAK_WALL_SIGN);
		chest = world.getBlockAt(1, 64, 2);
		chest.setType(Material.CHEST);
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void allowsAuctionSignAndAuthorizedChestInteractions() {
		AuctionManagedBlockAccess access = new AuctionManagedBlockAccess(source(true));

		assertTrue(access.allows(player, ProtectionAction.BLOCK_INTERACT, sign));
		assertTrue(access.allows(player, ProtectionAction.CONTAINER_OPEN, chest));
	}

	@Test
	void rejectsUnauthorizedChestAndUnrelatedActions() {
		AuctionManagedBlockAccess access = new AuctionManagedBlockAccess(source(false));

		assertFalse(access.allows(player, ProtectionAction.CONTAINER_OPEN, chest));
		assertFalse(access.allows(player, ProtectionAction.BLOCK_BREAK, chest));
		assertFalse(access.allows(player, ProtectionAction.BLOCK_INTERACT, chest));
	}

	private AuctionBlockAccess source(boolean mayOpen) {
		return new AuctionBlockAccess() {
			@Override
			public boolean isAuctionSign(Block block) {
				return block.equals(sign);
			}

			@Override
			public boolean mayOpenAuctionChest(Player actor, Block block) {
				return actor.equals(player) && block.equals(chest) && mayOpen;
			}
		};
	}
}
