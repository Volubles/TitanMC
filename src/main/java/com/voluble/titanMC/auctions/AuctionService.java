package com.voluble.titanMC.auctions;

import com.voluble.titanMC.auctions.config.AuctionConfiguration;
import com.voluble.titanMC.auctions.config.AuctionConfigurationManager;
import com.voluble.titanMC.cells.persistence.CellStorage;
import com.voluble.titanMC.display.notice.MessageDefaults;
import com.voluble.titanMC.display.notice.PluginMessageService;
import com.voluble.titanMC.ranks.model.WardId;
import com.voluble.titanMC.ranks.service.RankCatalog;
import com.voluble.titanMC.ranks.service.PlayerRankService;
import com.voluble.titanMC.ranks.service.WardRankRequirements;
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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class AuctionService implements AuctionBlockAccess, AutoCloseable {
	private final Plugin plugin;
	private final AuctionStorage storage;
	private final CellStorage cellStorage;
	private final AuctionConfigurationManager configuration;
	private final Economy economy;
	private final RankCatalog ranks;
	private final PlayerRankService playerRanks;
	private final WardRankRequirements purchaseRequirements;
	private final AuctionDeliveryService deliveries;
	private final PluginMessageService messages;
	private final Map<String, AuctionPosition> positions = new LinkedHashMap<>();
	private final Map<Long, AuctionLot> auctions = new LinkedHashMap<>();
	private final Map<BlockKey, AuctionLot> chestLots = new HashMap<>();
	private final Map<BlockKey, AuctionLot> signLots = new HashMap<>();
	private final Set<Long> purchasesInFlight = new HashSet<>();
	private final Set<Long> maintenanceInFlight = new HashSet<>();
	private final Set<String> assignmentPositionsInFlight = new HashSet<>();
	private boolean ingestionRunning;
	private BukkitTask task;

	public AuctionService(
		Plugin plugin,
		AuctionStorage storage,
		CellStorage cellStorage,
		AuctionConfigurationManager configuration,
		Economy economy,
		RankCatalog ranks,
		PlayerRankService playerRanks,
		PluginMessageService messages
	) {
		this.plugin = plugin;
		this.storage = storage;
		this.cellStorage = cellStorage;
		this.configuration = configuration;
		this.economy = economy;
		this.ranks = ranks;
		this.playerRanks = playerRanks;
		this.purchaseRequirements = new WardRankRequirements(ranks, configuration.current().minimumRanksByWard());
		this.messages = messages;
		this.deliveries = new AuctionDeliveryService(plugin, storage, messages);
	}

	public void start() throws SQLException {
		positions.putAll(storage.loadPositions());
		auctions.putAll(index(storage.loadAuctions()));
		validateStoredAssignments();
		for (AuctionPosition position : positions.values()) {
			if (auctions.values().stream().noneMatch(lot -> position.id().equals(lot.positionId()))) removeBlocks(position);
		}
		for (AuctionLot lot : auctions.values()) {
			if (lot.positionId() != null) render(lot);
		}
		rebuildPhysicalLotIndex();
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
		return chestLots.get(BlockKey.of(block));
	}

	public AuctionLot atSign(Block block) {
		return signLots.get(BlockKey.of(block));
	}

	public boolean hasPhysicalLots() {
		return !chestLots.isEmpty();
	}

	@Override
	public boolean isAuctionSign(Block block) {
		return atSign(block) != null;
	}

	@Override
	public boolean mayOpenAuctionChest(Player player, Block block) {
		AuctionLot lot = atChest(block);
		return lot != null && canOpen(player, lot);
	}

	public boolean canOpen(Player player, AuctionLot lot) {
		return switch (lot.state()) {
			case FOR_SALE, QUEUED -> false;
			case CLAIMED -> player.getUniqueId().equals(lot.buyerId());
			case PUBLIC -> true;
		};
	}

	public void open(Player player, AuctionLot original) {
		deliveries.recover(player);
		AuctionLot lot = auctions.get(original.id());
		if (lot == null || !canOpen(player, lot)) {
			messages.send(player, MessageDefaults.AUCTIONS_NOT_AVAILABLE);
			return;
		}
		AuctionInventoryHolder holder = new AuctionInventoryHolder(lot.id());
		Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Auction Chest"));
		holder.inventory(inventory);
		for (AuctionItem item : lot.items()) {
			holder.bind(item.slot(), item.id());
			inventory.setItem(item.slot(), ItemStack.deserializeBytes(item.data()));
		}
		player.openInventory(inventory);
	}

	public void claim(Player player, AuctionInventoryHolder holder, int slot) {
		AuctionLot lot = auctions.get(holder.auctionId());
		Long itemId = holder.itemId(slot);
		if (lot == null || itemId == null || !canOpen(player, lot)) return;
		AuctionItem item = lot.items().stream().filter(candidate -> candidate.id() == itemId).findFirst().orElse(null);
		if (item == null) {
			holder.remove(slot);
			holder.getInventory().setItem(slot, null);
			return;
		}
		holder.remove(slot);
		holder.getInventory().setItem(slot, null);
		storage.reserveItem(lot.id(), item.id(), player.getUniqueId()).whenComplete((delivery, failure) ->
			Bukkit.getScheduler().runTask(plugin, () -> finishClaim(player, holder, slot, item, delivery, failure))
		);
	}

	private void finishClaim(
		Player player,
		AuctionInventoryHolder holder,
		int slot,
		AuctionItem item,
		AuctionDelivery delivery,
		Throwable failure
	) {
		AuctionLot lot = auctions.get(holder.auctionId());
		if (failure != null) {
			if (lot != null && lot.items().stream().anyMatch(candidate -> candidate.id() == item.id())) {
				holder.bind(slot, item.id());
				holder.getInventory().setItem(slot, ItemStack.deserializeBytes(item.data()));
			}
			messages.send(player, MessageDefaults.AUCTIONS_ITEM_UNAVAILABLE);
			return;
		}
		if (lot == null) {
			deliveries.deliver(player, delivery);
			return;
		}
		List<AuctionItem> remaining = lot.items().stream().filter(candidate -> candidate.id() != item.id()).toList();
		AuctionLot updated = copyWithItems(lot, remaining);
		auctions.put(updated.id(), updated);
		rebuildPhysicalLotIndex();
		deliveries.deliver(player, delivery);
		if (remaining.isEmpty()) removeEmptyAuction(updated);
	}

	private void removeEmptyAuction(AuctionLot lot) {
		if (!maintenanceInFlight.add(lot.id())) return;
		storage.deleteAuctionAsync(lot.id()).whenComplete((ignored, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
			maintenanceInFlight.remove(lot.id());
			if (failure != null) {
				plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not remove empty auction " + lot.id(), failure);
				return;
			}
			auctions.remove(lot.id());
			rebuildPhysicalLotIndex();
			AuctionPosition position = positions.get(lot.positionId());
			if (position != null) removeBlocks(position);
		}));
	}

	public void recoverDeliveries(Player player) {
		deliveries.recover(player);
	}

	public boolean hasDeliveryReceipt(ItemStack item) {
		return deliveries.hasReceipt(item);
	}

	public boolean discardAt(Block block) {
		AuctionLot lot = atChest(block);
		if (lot == null) lot = atSign(block);
		if (lot == null) return false;
		try {
			delete(lot);
			return true;
		} catch (SQLException exception) {
			throw new IllegalStateException("Could not discard auction", exception);
		}
	}

	public void purchase(Player player, AuctionLot original) {
		AuctionLot lot = auctions.get(original.id());
		if (lot == null || lot.state() != AuctionState.FOR_SALE || !purchasesInFlight.add(lot.id())) {
			messages.send(player, MessageDefaults.AUCTIONS_NO_LONGER_FOR_SALE);
			return;
		}
		if (lot.saleExpiresAt() <= System.currentTimeMillis()) {
			purchasesInFlight.remove(lot.id());
			try {
				delete(lot);
			} catch (SQLException exception) {
				plugin.getLogger().severe("Could not expire auction: " + exception.getMessage());
			}
			messages.send(player, MessageDefaults.AUCTIONS_EXPIRED);
			return;
		}
		var currentRank = playerRanks.current(player.getUniqueId());
		if (currentRank.isEmpty()) {
			purchasesInFlight.remove(lot.id());
			messages.send(player, MessageDefaults.AUCTIONS_RANK_UNAVAILABLE);
			return;
		}
		if (!purchaseRequirements.allows(currentRank.get().rankId(), lot.wardId())) {
			purchasesInFlight.remove(lot.id());
			messages.send(player, MessageDefaults.AUCTIONS_RANK_REQUIRED, args -> args
				.plain("rank", purchaseRequirements.requiredRank(lot.wardId()).value().toUpperCase(java.util.Locale.ROOT))
				.plain("ward", lot.wardId().value().toUpperCase(java.util.Locale.ROOT)));
			return;
		}
		if (economy == null) {
			purchasesInFlight.remove(lot.id());
			messages.send(player, MessageDefaults.AUCTIONS_ECONOMY_UNAVAILABLE);
			return;
		}
		if (!economy.has(player, lot.price())) {
			purchasesInFlight.remove(lot.id());
			messages.send(player, MessageDefaults.AUCTIONS_NOT_ENOUGH_MONEY);
			return;
		}
		var withdrawal = economy.withdrawPlayer(player, lot.price());
		if (!withdrawal.transactionSuccess()) {
			purchasesInFlight.remove(lot.id());
			messages.send(player, MessageDefaults.AUCTIONS_PAYMENT_FAILED);
			return;
		}
		long claimExpiry = System.currentTimeMillis() + configuration.current().claimDurationMillis();
		AuctionLot claimed = lot.claimed(player.getUniqueId(), player.getName(), claimExpiry);
		storage.saveAuctionAsync(claimed).whenComplete((ignored, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
			purchasesInFlight.remove(lot.id());
			if (failure != null) {
				var refund = economy.depositPlayer(player, lot.price());
				if (refund.transactionSuccess()) messages.send(player, MessageDefaults.AUCTIONS_PURCHASE_REFUNDED);
				else {
					plugin.getLogger().log(java.util.logging.Level.SEVERE, "Auction purchase refund failed for " + player.getUniqueId(), failure);
					messages.send(player, MessageDefaults.AUCTIONS_PURCHASE_REFUND_FAILED);
				}
				return;
			}
			auctions.put(claimed.id(), claimed);
			rebuildPhysicalLotIndex();
			updateSign(claimed);
			messages.send(player, MessageDefaults.AUCTIONS_PURCHASED, args -> args
				.plain("duration", shortTime(configuration.current().claimDurationMillis())));
		}));
	}

	private void tick() {
		try {
			startIngestion();
			long now = System.currentTimeMillis();
			for (AuctionLot lot : List.copyOf(auctions.values())) {
				if (lot.items().isEmpty()) {
					removeEmptyAuction(lot);
				} else if (lot.state() == AuctionState.FOR_SALE && lot.saleExpiresAt() <= now
					&& !purchasesInFlight.contains(lot.id())) {
					removeEmptyAuction(lot);
				} else if (lot.state() == AuctionState.CLAIMED && lot.claimExpiresAt() <= now) {
					makePublic(lot);
				} else if (lot.positionId() != null) {
					updateSign(lot);
				}
			}
			assignQueued();
		} catch (Exception exception) {
			plugin.getLogger().severe("Auction tick failed: " + exception.getMessage());
		}
	}

	private void startIngestion() {
		if (ingestionRunning) return;
		ingestionRunning = true;
		AuctionConfiguration priceConfiguration = configuration.current();
		long minimumPrice = priceConfiguration.minimumPrice();
		long maximumPrice = priceConfiguration.maximumPrice();
		cellStorage.loadReadyRecoveryLots().thenCompose(sources -> {
			if (sources.isEmpty()) return CompletableFuture.completedFuture(null);
			CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
			for (var source : sources) {
				chain = chain.thenCompose(ignored -> storage.ingestAsync(
					source, () -> randomPrice(minimumPrice, maximumPrice)
				))
					.thenCompose(ignored -> cellStorage.markRecoveryLotAuctioned(source.id()));
			}
			return chain.thenCompose(ignored -> storage.loadAuctionsAsync());
		}).whenComplete((loaded, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
			ingestionRunning = false;
			if (failure != null) {
				plugin.getLogger().log(java.util.logging.Level.SEVERE, "Auction ingestion failed", failure);
				return;
			}
			if (loaded != null) {
				auctions.clear();
				auctions.putAll(index(loaded));
				rebuildPhysicalLotIndex();
			}
		}));
	}

	private void assignQueued() {
		int submitted = 0;
		for (AuctionAssignmentPlanner.Assignment assignment : AuctionAssignmentPlanner.plan(
			positions.values(), auctions.values(), ThreadLocalRandom.current()
		)) {
			if (submitted >= 4) return;
			if (maintenanceInFlight.contains(assignment.auctionId())
				|| !assignmentPositionsInFlight.add(assignment.positionId())) {
				continue;
			}
			AuctionLot lot = auctions.get(assignment.auctionId());
			AuctionPosition position = positions.get(assignment.positionId());
			AuctionLot assigned = lot.atPosition(position.id(), System.currentTimeMillis() + configuration.current().saleDurationMillis());
			maintenanceInFlight.add(lot.id());
			submitted++;
			storage.saveAuctionAsync(assigned).whenComplete((ignored, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
				maintenanceInFlight.remove(lot.id());
				assignmentPositionsInFlight.remove(position.id());
				if (failure != null) {
					plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not assign auction " + lot.id(), failure);
					return;
				}
				AuctionLot current = auctions.get(lot.id());
				if (current == null || current.state() != AuctionState.QUEUED) return;
				auctions.put(assigned.id(), assigned);
				rebuildPhysicalLotIndex();
				render(assigned);
			}));
		}
	}

	private void makePublic(AuctionLot lot) {
		if (!maintenanceInFlight.add(lot.id())) return;
		AuctionLot publicLot = lot.publicAccess();
		storage.saveAuctionAsync(publicLot).whenComplete((ignored, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
			maintenanceInFlight.remove(lot.id());
			if (failure != null) {
				plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not release auction " + lot.id(), failure);
				return;
			}
			AuctionLot current = auctions.get(lot.id());
			if (current == null || current.state() != AuctionState.CLAIMED) return;
			auctions.put(publicLot.id(), publicLot);
			rebuildPhysicalLotIndex();
			updateSign(publicLot);
		}));
	}

	private void validateStoredAssignments() {
		for (AuctionPosition position : positions.values()) ranks.requireWard(position.wardId());
		for (AuctionLot lot : auctions.values()) {
			ranks.requireWard(lot.wardId());
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

	private void render(AuctionLot lot) {
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
		if (chestBlock.getState() instanceof Chest chest) {
			Inventory inventory = chest.getBlockInventory();
			inventory.clear();
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
			render(lot);
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
			"ward", lot.wardId().value(),
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
		rebuildPhysicalLotIndex();
		AuctionPosition position = positions.get(lot.positionId());
		if (position != null) removeBlocks(position);
	}

	private void rebuildPhysicalLotIndex() {
		chestLots.clear();
		signLots.clear();
		for (AuctionLot lot : auctions.values()) {
			AuctionPosition position = positions.get(lot.positionId());
			if (position == null) continue;
			chestLots.put(BlockKey.chest(position), lot);
			signLots.put(BlockKey.sign(position), lot);
		}
	}

	private void removeBlocks(AuctionPosition position) {
		World world = world(position);
		if (world == null) return;
		Block chest = world.getBlockAt(position.x(), position.y(), position.z());
		chest.getRelative(position.facing()).setType(Material.AIR, false);
		chest.setType(Material.AIR, false);
	}

	private static long randomPrice(long minimum, long maximum) {
		if (minimum == maximum) return minimum;
		return ThreadLocalRandom.current().nextLong(minimum, maximum + 1);
	}

	private static Map<Long, AuctionLot> index(List<AuctionLot> lots) {
		Map<Long, AuctionLot> indexed = new LinkedHashMap<>();
		for (AuctionLot lot : lots) indexed.put(lot.id(), lot);
		return indexed;
	}

	private static AuctionLot copyWithItems(AuctionLot lot, List<AuctionItem> items) {
		return new AuctionLot(lot.id(), lot.sourceLotId(), lot.batchIndex(), lot.wardId(), lot.positionId(), lot.price(), lot.state(), lot.buyerId(), lot.buyerName(), lot.saleExpiresAt(), lot.claimExpiresAt(), items);
	}

	private static World world(AuctionPosition position) {
		return position == null ? null : Bukkit.getWorld(position.worldId());
	}

	private record BlockKey(UUID worldId, int x, int y, int z) {
		static BlockKey of(Block block) {
			return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
		}

		static BlockKey chest(AuctionPosition position) {
			return new BlockKey(position.worldId(), position.x(), position.y(), position.z());
		}

		static BlockKey sign(AuctionPosition position) {
			BlockFace facing = position.facing();
			return new BlockKey(
				position.worldId(),
				position.x() + facing.getModX(),
				position.y() + facing.getModY(),
				position.z() + facing.getModZ()
			);
		}
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
