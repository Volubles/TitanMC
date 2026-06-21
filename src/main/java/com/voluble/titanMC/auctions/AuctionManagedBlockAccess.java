package com.voluble.titanMC.auctions;

import com.voluble.titanMC.regions.protection.bukkit.ManagedBlockAccess;
import com.voluble.titanMC.regions.protection.model.ProtectionAction;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class AuctionManagedBlockAccess implements ManagedBlockAccess {
	private final AuctionBlockAccess auctions;

	public AuctionManagedBlockAccess(AuctionBlockAccess auctions) {
		this.auctions = Objects.requireNonNull(auctions, "auctions");
	}

	@Override
	public boolean allows(Player player, ProtectionAction action, Block block) {
		return switch (action) {
			case BLOCK_INTERACT -> auctions.isAuctionSign(block);
			case CONTAINER_OPEN -> auctions.mayOpenAuctionChest(player, block);
			default -> false;
		};
	}
}
