package com.voluble.titanMC.cells;

import com.voluble.titanMC.cells.model.CellDefinition;
import com.voluble.titanMC.cells.model.CellLease;
import com.voluble.titanMC.ranks.service.PlayerRankService;
import com.voluble.titanMC.ranks.service.WardRankRequirements;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class CellRentalService {
	private final Plugin plugin;
	private final CellManager cells;
	private final Economy economy;
	private final CellSignRenderer signs;
	private final PlayerRankService playerRanks;
	private final WardRankRequirements eligibility;
	private final Set<String> reservations = new HashSet<>();
	private final Set<UUID> playerReservations = new HashSet<>();

	public CellRentalService(
		Plugin plugin,
		CellManager cells,
		Economy economy,
		CellSignRenderer signs,
		PlayerRankService playerRanks,
		WardRankRequirements eligibility
	) {
		this.plugin = plugin;
		this.cells = cells;
		this.economy = economy;
		this.signs = signs;
		this.playerRanks = playerRanks;
		this.eligibility = eligibility;
	}

	public void rent(Player player, String cellId) {
		CellDefinition cell = cells.get(cellId);
		if (cell == null) {
			player.sendMessage("Unknown cell.");
			return;
		}
		if (economy == null) {
			player.sendMessage("Renting is unavailable because no economy provider is active.");
			return;
		}
		var currentRank = playerRanks.current(player.getUniqueId());
		if (currentRank.isEmpty()) {
			player.sendMessage("Your prison rank is not available yet.");
			return;
		}
		if (!eligibility.allows(currentRank.get().rankId(), cell.wardId())) {
			player.sendMessage(
				"You need rank " + eligibility.requiredRank(cell.wardId()).value().toUpperCase(java.util.Locale.ROOT)
					+ " to rent a cell in ward " + cell.wardId().value().toUpperCase(java.util.Locale.ROOT) + "."
			);
			return;
		}
		if (!playerReservations.add(player.getUniqueId())) {
			player.sendMessage("Another cell rental is already being processed for you.");
			return;
		}
		if (!reservations.add(cell.id())) {
			playerReservations.remove(player.getUniqueId());
			player.sendMessage("This cell is currently being processed.");
			return;
		}
		CellLease lease;
		try {
			lease = cells.planLease(cell.id(), player.getUniqueId());
		} catch (RuntimeException e) {
			reservations.remove(cell.id());
			playerReservations.remove(player.getUniqueId());
			player.sendMessage(e.getMessage());
			return;
		}
		if (!economy.has(player, cell.rentPrice())) {
			reservations.remove(cell.id());
			playerReservations.remove(player.getUniqueId());
			player.sendMessage("You do not have enough money.");
			return;
		}
		var withdrawal = economy.withdrawPlayer(player, cell.rentPrice());
		if (!withdrawal.transactionSuccess()) {
			reservations.remove(cell.id());
			playerReservations.remove(player.getUniqueId());
			player.sendMessage("The payment failed.");
			return;
		}
		cells.persistLease(lease).whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
			try {
				if (error != null) throw new IllegalStateException(error);
				cells.activateLease(lease);
				signs.refresh(cell);
				player.sendMessage("You rented " + cell.displayName() + ".");
			} catch (RuntimeException failure) {
				cells.discardLease(lease);
				economy.depositPlayer(player, cell.rentPrice());
				player.sendMessage("The rental could not be completed; your payment was refunded.");
				plugin.getLogger().warning("Failed to activate cell lease: " + failure.getMessage());
			} finally {
				reservations.remove(cell.id());
				playerReservations.remove(player.getUniqueId());
			}
		}));
	}
}
