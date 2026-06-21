package com.voluble.titanMC.auctions;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public interface AuctionBlockAccess {
	boolean isAuctionSign(Block block);

	boolean mayOpenAuctionChest(Player player, Block block);
}
