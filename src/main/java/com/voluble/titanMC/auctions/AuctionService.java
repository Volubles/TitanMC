package com.voluble.titanMC.auctions;

import com.voluble.titanMC.auctions.config.AuctionConfiguration;
import com.voluble.titanMC.auctions.config.AuctionConfigurationManager;
import com.voluble.titanMC.cells.persistence.CellStorage;
import com.voluble.titanMC.ranks.model.WardId;
import com.voluble.titanMC.ranks.service.RankCatalog;
import com.voluble.titanMC.util.ChatUtils;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class AuctionService implements AutoCloseable {
	private final Plugin plugin;
	private final AuctionStorage storage;
	private final CellStorage cellStorage;
	private final AuctionConfigurationManager configuration;
	private final Economy economy;
	private final RankCatalog ranks;
	private final Map<String, AuctionPosition> positions = new LinkedHashMap<>();
	private final Map<Long, AuctionLot> auctions = new LinkedHashMap<>();
	private BukkitTask task;

	public AuctionService(
		Plugin plugin,
		AuctionStorage storage,
		CellStorage cellStorage,
		AuctionConfigurationManager configuration,
		Economy economy,
		RankCatalog ranks
	) {
		this.plugin = plugin;
		this.storage = storage;
		this.cellStorage = cellStorage;
		this.configuration = configuration;
		this.economy = economy;
		this.ranks = ranks;
	}

	public void start() throws SQLException {
		positions.putAll(storage.loadPositions());
		auctions.putAll(index(storage.loadAuctions()));
		validateStoredAssignments();
		for (AuctionPosition position : positions.values()) {
			if (auctions.values().stream().noneMatch(lot -> position.id().equals(lot.positionId()))) removeBlocks(position);
		}
		for (AuctionLot lot : auctions.values()) {
			if (lot.positionId() != null) render(lot, true);
		}
		task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
	}

	public AuctionPosition addPosition(WardId wardId, Location location, BlockFace facing) {
		ranks.requireWard(wardId);
		String id = AuctionPositionIds.next(wardId, positions.keySet());
		for (AuctionPosition existing : positions.values()) {
			if (existing.worldId().equals(location.getWorld().getUID())
				&& existing.x() == location.getBlockX() && existing.y() == location.getBlockY() && existing.z() == location.getBlockZ()) {
				throw new IllegalArgumentException("An auction position already exists at that block");
			}
		}
		AuctionPosition position = new AuctionPosition(
			id, wardId, location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), facing
		);
		try {
			storage.savePosition(position);
			positions.put(id, position);
			return position;
		} catch (SQLException exception) {
			throw new IllegalStateException("Could not save auction position", exception);
		}
	}

	public void removePosition(String id) {
		AuctionPosition position = requiredPosition(id);
		if (auctions.values().stream().anyMatch(lot -> position.id().equals(lot.positionId()))) {
			throw new IllegalStateException("That position currently contains an auction");
		}
		try {
			storage.deletePosition(position.id());
			positions.remove(position.id());
			removeBlocks(position);
		} catch (SQLException exception) {
			throw new IllegalStateException("Could not remove auction position", exception);
		}
	}

	public List<AuctionPosition> positions() {
		return List.copyOf(positions.values());
	}

	public AuctionPosition requiredPosition(String id) {
		AuctionPosition position = positions.get(normalize(id));
		if (position == null) throw new IllegalArgumentException("Unknown auction position: " + id);
		return position;
	}

	public AuctionLot atPosition(String positionId) {
		return auctions.values().stream()
			.filter(lot -> positionId.equals(lot.positionId()))
			.findFirst()
			.orElse(null);
	}

	public AuctionLot atChest(Block block) {
		return auctions.values().stream().filter(lot -> {
			AuctionPosition position = positions.get(lot.positionId());
			return position != null && matches(block, position, false);
		}).findFirst().orElse(null);
	}

	public AuctionLot atSign(Block block) {
		return auctions.values().stream().filter(lot -> {
			AuctionPosition position = positions.get(lot.positionId());
			return position != null && matches(block, position, true);
		}).findFirst().orElse(null);
	}

	public boolean canOpen(Player player, AuctionLot lot) {
		return switch (lot.state()) {
			case FOR_SALE, QUEUED -> false;
			case CLAIMED -> player.getUniqueId().equals(lot.buyerId());
			case PUBLIC -> true;
		};
	}

	public void purchase(Player player, AuctionLot original) {
		AuctionLot lot = auctions.get(original.id());
		if (lot == null || lot.state() != AuctionState.FOR_SALE) {
			player.sendMessage("This auction is no longer for sale.");
			return;
		}
		if (lot.saleExpiresAt() <= System.currentTimeMillis()) {
			try {
				delete(lot);
			} catch (SQLException exception) {
				plugin.getLogger().severe("Could not expire auction: " + exception.getMessage());
			}
			player.sendMessage("This auction has expired.");
			return;
		}
		if (economy == null) {
			player.sendMessage("The economy is unavailable.");
			return;
		}
		if (!economy.has(player, lot.price())) {
			player.sendMessage("You do not have enough money.");
			return;
		}
		var withdrawal = economy.withdrawPlayer(player, lot.price());
		if (!withdrawal.transactionSuccess()) {
			player.sendMessage("The payment failed.");
			return;
		}
		long claimExpiry = System.currentTimeMillis() + configuration.current().claimDurationMillis();
		AuctionLot claimed = lot.claimed(player.getUniqueId(), player.getName(), claimExpiry);
		try {
			storage.saveAuction(claimed);
			auctions.put(claimed.id(), claimed);
			updateSign(claimed);
			player.sendMessage("You bought the mystery chest. You have " + shortTime(configuration.current().claimDurationMillis()) + " of exclusive access.");
		} catch (SQLException exception) {
			economy.depositPlayer(player, lot.price());
			player.sendMessage("The purchase failed; your payment was refunded.");
		}
	}

	public void synchronizeInventory(AuctionLot original) {
		AuctionLot lot = auctions.get(original.id());
		if (lot == null || lot.positionId() == null) return;
		AuctionPosition position = positions.get(lot.positionId());
		World world = world(position);
		if (world == null || !(world.getBlockAt(position.x(), position.y(), position.z()).getState() instanceof Chest chest)) return;
		List<byte[]> items = new ArrayList<>();
		for (ItemStack item : chest.getBlockInventory().getContents()) {
			if (item != null && !item.getType().isAir()) items.add(item.serializeAsBytes());
		}
		try {
			if (items.isEmpty()) {
				delete(lot);
			} else {
				storage.replaceItems(lot.id(), items);
				AuctionLot updated = copyWithItems(lot, items);
				auctions.put(updated.id(), updated);
			}
		} catch (SQLException exception) {
			plugin.getLogger().severe("Could not persist auction inventory: " + exception.getMessage());
		}
	}

	private void tick() {
		try {
			ingestReadyLots();
			long now = System.currentTimeMillis();
			for (AuctionLot lot : List.copyOf(auctions.values())) {
				if (lot.items().isEmpty()) {
					delete(lot);
				} else if (lot.state() == AuctionState.FOR_SALE && lot.saleExpiresAt() <= now) {
					delete(lot);
				} else if (lot.state() == AuctionState.CLAIMED && lot.claimExpiresAt() <= now) {
					AuctionLot publicLot = lot.publicAccess();
					storage.saveAuction(publicLot);
					auctions.put(publicLot.id(), publicLot);
					updateSign(publicLot);
				} else if (lot.positionId() != null) {
					updateSign(lot);
				}
			}
			assignQueued();
		} catch (Exception exception) {
			plugin.getLogger().severe("Auction tick failed: " + exception.getMessage());
		}
	}

	private void ingestReadyLots() throws SQLException {
		boolean changed = false;
		for (var source : cellStorage.loadReadyRecoveryLots().join()) {
			storage.ingest(source, this::randomPrice);
			cellStorage.markRecoveryLotAuctioned(source.id()).join();
			changed = true;
		}
		if (changed) {
			auctions.clear();
			auctions.putAll(index(storage.loadAuctions()));
		}
	}

	private void assignQueued() throws SQLException {
		for (AuctionAssignmentPlanner.Assignment assignment : AuctionAssignmentPlanner.plan(
			positions.values(), auctions.values(), ThreadLocalRandom.current()
		)) {
			AuctionLot lot = auctions.get(assignment.auctionId());
			AuctionPosition position = positions.get(assignment.positionId());
			AuctionLot assigned = lot.atPosition(position.id(), System.currentTimeMillis() + configuration.current().saleDurationMillis());
			storage.saveAuction(assigned);
			auctions.put(assigned.id(), assigned);
			render(assigned, true);
		}
	}

	private void validateStoredAssignments() {
		for (AuctionLot lot : auctions.values()) {
			if (lot.positionId() == null) continue;
			AuctionPosition position = positions.get(lot.positionId());
			if (position == null) {
				throw new IllegalStateException("Auction " + lot.id() + " references unknown position " + lot.positionId());
			}
			if (!position.wardId().equals(lot.wardId())) {
				throw new IllegalStateException(
					"Auction " + lot.id() + " in ward " + lot.wardId()
						+ " references position " + position.id() + " in ward " + position.wardId()
				);
			}
		}
	}

	private void render(AuctionLot lot, boolean populate) {
		AuctionPosition position = positions.get(lot.positionId());
		World world = world(position);
		if (world == null) return;
		AuctionConfiguration config = configuration.current();
		Block chestBlock = world.getBlockAt(position.x(), position.y(), position.z());
		chestBlock.setType(config.chestMaterial(), false);
		if (chestBlock.getBlockData() instanceof org.bukkit.block.data.Directional directional) {
			directional.setFacing(position.facing());
			if (directional instanceof org.bukkit.block.data.type.Chest chestData) {
				chestData.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
			}
			chestBlock.setBlockData(directional, false);
		}
		if (populate && chestBlock.getState() instanceof Chest chest) {
			Inventory inventory = chest.getBlockInventory();
			inventory.clear();
			for (byte[] data : lot.items()) inventory.addItem(ItemStack.deserializeBytes(data));
		}
		Block signBlock = chestBlock.getRelative(position.facing());
		signBlock.setType(config.signMaterial(), false);
		if (signBlock.getBlockData() instanceof org.bukkit.block.data.Directional directional) {
			directional.setFacing(position.facing());
			signBlock.setBlockData(directional, false);
		}
		updateSign(lot);
	}

	private void updateSign(AuctionLot lot) {
		AuctionPosition position = positions.get(lot.positionId());
		World world = world(position);
		if (world == null) return;
		Block signBlock = world.getBlockAt(position.x(), position.y(), position.z()).getRelative(position.facing());
		if (!(signBlock.getState() instanceof Sign sign)) {
			render(lot, false);
			return;
		}
		AuctionConfiguration config = configuration.current();
		List<String> template = switch (lot.state()) {
			case FOR_SALE -> config.forSaleSign();
			case CLAIMED -> config.claimedSign();
			case PUBLIC -> config.publicSign();
			case QUEUED -> List.of("", "", "", "");
		};
		long expiry = lot.state() == AuctionState.FOR_SALE ? lot.saleExpiresAt() : lot.claimExpiresAt();
		Map<String, String> values = Map.of(
			"price", Long.toString(lot.price()),
			"buyer", lot.buyerName() == null ? "" : lot.buyerName(),
			"time_left", shortTime(Math.max(0, expiry - System.currentTimeMillis()))
		);
		for (int line = 0; line < 4; line++) sign.getSide(Side.FRONT).line(line, format(template.get(line), values));
		sign.update(true, false);
	}

	private void delete(AuctionLot lot) throws SQLException {
		storage.deleteAuction(lot.id());
		auctions.remove(lot.id());
		AuctionPosition position = positions.get(lot.positionId());
		if (position != null) removeBlocks(position);
	}

	private void removeBlocks(AuctionPosition position) {
		World world = world(position);
		if (world == null) return;
		Block chest = world.getBlockAt(position.x(), position.y(), position.z());
		chest.getRelative(position.facing()).setType(Material.AIR, false);
		chest.setType(Material.AIR, false);
	}

	private long randomPrice() {
		AuctionConfiguration config = configuration.current();
		if (config.minimumPrice() == config.maximumPrice()) return config.minimumPrice();
		return ThreadLocalRandom.current().nextLong(config.minimumPrice(), config.maximumPrice() + 1);
	}

	private static Map<Long, AuctionLot> index(List<AuctionLot> lots) {
		Map<Long, AuctionLot> indexed = new LinkedHashMap<>();
		for (AuctionLot lot : lots) indexed.put(lot.id(), lot);
		return indexed;
	}

	private static AuctionLot copyWithItems(AuctionLot lot, List<byte[]> items) {
		return new AuctionLot(lot.id(), lot.sourceLotId(), lot.batchIndex(), lot.wardId(), lot.positionId(), lot.price(), lot.state(), lot.buyerId(), lot.buyerName(), lot.saleExpiresAt(), lot.claimExpiresAt(), items);
	}

	private static boolean matches(Block block, AuctionPosition position, boolean sign) {
		Location location = new Location(Bukkit.getWorld(position.worldId()), position.x(), position.y(), position.z());
		if (sign) location = location.getBlock().getRelative(position.facing()).getLocation();
		return block.getWorld().getUID().equals(position.worldId())
			&& block.getX() == location.getBlockX() && block.getY() == location.getBlockY() && block.getZ() == location.getBlockZ();
	}

	private static World world(AuctionPosition position) {
		return position == null ? null : Bukkit.getWorld(position.worldId());
	}

	private static String normalize(String id) {
		String normalized = id.toLowerCase(java.util.Locale.ROOT);
		if (!normalized.matches("[a-z0-9][a-z0-9_-]{0,31}")) throw new IllegalArgumentException("Position names may only contain a-z, 0-9, _ and -");
		return normalized;
	}

	private static Component format(String template, Map<String, String> values) {
		String result = template;
		for (var entry : values.entrySet()) result = result.replace("{" + entry.getKey() + "}", entry.getValue());
		return ChatUtils.format(result);
	}

	private static String shortTime(long millis) {
		Duration duration = Duration.ofMillis(Math.max(0, millis));
		long days = duration.toDays();
		if (days > 0) return days + "d " + duration.minusDays(days).toHours() + "h";
		long hours = duration.toHours();
		if (hours > 0) return hours + "h " + duration.minusHours(hours).toMinutes() + "m";
		long minutes = duration.toMinutes();
		if (minutes > 0) return minutes + "m " + duration.minusMinutes(minutes).toSeconds() + "s";
		return duration.toSeconds() + "s";
	}

	@Override
	public void close() throws Exception {
		if (task != null) task.cancel();
		storage.close();
	}
}
