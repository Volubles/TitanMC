package com.voluble.titanMC.ranks.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.voluble.titanMC.ranks.model.PlayerRank;
import com.voluble.titanMC.ranks.model.PrisonRank;
import com.voluble.titanMC.ranks.model.RankId;
import com.voluble.titanMC.ranks.model.RankupRequirement;
import com.voluble.titanMC.ranks.model.WardDefinition;
import com.voluble.titanMC.ranks.service.PlayerRankService;
import com.voluble.titanMC.ranks.service.RankCatalog;
import com.voluble.titanMC.ranks.service.RankupResult;
import com.voluble.titanMC.ranks.service.RankupService;
import io.voluble.michellelib.commands.CommandModule;
import io.voluble.michellelib.commands.CommandRegistration;
import io.voluble.michellelib.commands.arguments.Args;
import io.voluble.michellelib.commands.context.MichelleCommandContext;
import io.voluble.michellelib.commands.suggest.Suggest;
import io.voluble.michellelib.commands.tree.CommandTree;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RankCommandModule implements CommandModule {
	private static final String USE_PERMISSION = "titanmc.rank.use";
	private static final String ADMIN_PERMISSION = "titanmc.rank.admin";

	private final RankCatalog catalog;
	private final PlayerRankService players;
	private final RankupService rankups;

	public RankCommandModule(RankCatalog catalog, PlayerRankService players, RankupService rankups) {
		this.catalog = Objects.requireNonNull(catalog, "catalog");
		this.players = Objects.requireNonNull(players, "players");
		this.rankups = Objects.requireNonNull(rankups, "rankups");
	}

	@Override
	public void register(CommandRegistration registration) {
		var rankIds = Suggest.fromContext(source -> catalog.ranks().stream().map(rank -> rank.id().value()).toList());
		registration.register(CommandTree.root("rank")
			.aliases("ranks")
			.description("Show or manage prison ranks")
			.requiresAnyPermission(USE_PERMISSION, ADMIN_PERMISSION)
			.executesPlayer((player, ctx) -> showOwnRank(player))
			.literalExec("list", this::list)
			.literal("info", node -> node
				.requiresPermission(ADMIN_PERMISSION)
				.argument("player", Args.word(), name -> name.executes(this::info)))
			.literal("set", node -> node
				.requiresPermission(ADMIN_PERMISSION)
				.argument("player", Args.word(), name -> name
					.argument("rank", Args.word(), rank -> rank.suggests(rankIds).executes(this::set))))
			.spec());
		registration.register(CommandTree.root("rankup")
			.description("Purchase the next prison rank")
			.requiresPermission(USE_PERMISSION)
			.executesPlayer((player, ctx) -> rankup(player))
			.spec());
	}

	private int showOwnRank(Player player) {
		Optional<PlayerRank> rank = players.current(player.getUniqueId());
		if (rank.isEmpty()) {
			player.sendMessage("You do not have a rank yet.");
			return CommandTree.ok();
		}
		PrisonRank prisonRank = catalog.requireRank(rank.get().rankId());
		player.sendMessage("Your rank: " + prisonRank.displayName());
		catalog.nextRank(prisonRank.id()).ifPresent(next -> {
			RankupRequirement requirement = next.rankup().orElseThrow();
			player.sendMessage("Next rank: " + next.displayName() + " ($" + requirement.cost() + ")");
		});
		return CommandTree.ok();
	}

	private int list(MichelleCommandContext context) throws CommandSyntaxException {
		CommandSender sender = context.sender();
		for (WardDefinition ward : catalog.wards()) {
			sender.sendMessage(ward.displayName() + " (" + ward.id().value() + ")");
			for (RankId rankId : ward.ranks()) {
				PrisonRank rank = catalog.requireRank(rankId);
				String cost = rank.rankup()
					.map(requirement -> "$" + requirement.cost())
					.orElse("starter");
				sender.sendMessage("  - " + rank.displayName() + " [" + cost + "]");
			}
		}
		return CommandTree.ok();
	}

	private int info(MichelleCommandContext context) throws CommandSyntaxException {
		CommandSender sender = context.sender();
		OfflinePlayer target = resolvePlayer(context.arg("player", String.class));
		if (target == null) {
			sender.sendMessage("Unknown player. Use a cached name or UUID.");
			return CommandTree.ok();
		}
		Optional<PlayerRank> rank = players.current(target.getUniqueId());
		if (rank.isEmpty()) {
			sender.sendMessage(displayName(target) + " has no rank.");
			return CommandTree.ok();
		}
		PrisonRank prisonRank = catalog.requireRank(rank.get().rankId());
		sender.sendMessage(displayName(target) + " is rank " + prisonRank.displayName());
		return CommandTree.ok();
	}

	private int set(MichelleCommandContext context) throws CommandSyntaxException {
		CommandSender sender = context.sender();
		OfflinePlayer target = resolvePlayer(context.arg("player", String.class));
		if (target == null) {
			sender.sendMessage("Unknown player. Use a cached name or UUID.");
			return CommandTree.ok();
		}
		RankId rankId;
		try {
			rankId = RankId.of(context.arg("rank", String.class));
		} catch (IllegalArgumentException exception) {
			sender.sendMessage("Invalid rank id: " + exception.getMessage());
			return CommandTree.ok();
		}
		PrisonRank prisonRank = catalog.findRank(rankId).orElse(null);
		if (prisonRank == null) {
			sender.sendMessage("Unknown rank: " + rankId.value());
			return CommandTree.ok();
		}
		PlayerRank existing = players.current(target.getUniqueId())
			.orElseGet(() -> players.assignStarting(target.getUniqueId()));
		players.apply(existing.withRank(prisonRank.id(), System.currentTimeMillis()));
		sender.sendMessage("Set " + displayName(target) + " to " + prisonRank.displayName() + ".");
		return CommandTree.ok();
	}

	private int rankup(Player player) {
		RankupResult result = rankups.rankup(player.getUniqueId());
		switch (result) {
			case RankupResult.Success success -> player.sendMessage(
				"Ranked up to " + catalog.requireRank(success.current().rankId()).displayName()
					+ " for $" + success.charged() + "."
			);
			case RankupResult.AtMaxRank ignored -> player.sendMessage("You are already at the highest rank.");
			case RankupResult.MissingRequirement missing -> player.sendMessage(
				"You must hold " + catalog.requireRank(missing.required()).displayName()
					+ " before reaching " + missing.next().displayName() + "."
			);
			case RankupResult.InsufficientFunds funds -> player.sendMessage(
				"You need $" + funds.needed() + " to reach " + funds.next().displayName()
					+ " (you have $" + (long) funds.balance() + ")."
			);
			case RankupResult.EconomyUnavailable ignored -> player.sendMessage(
				"Rankups are unavailable because no economy provider is active."
			);
			case RankupResult.NoCurrentRank ignored -> player.sendMessage(
				"You do not have a rank yet; rejoin to receive the starter rank."
			);
		}
		return CommandTree.ok();
	}

	private static OfflinePlayer resolvePlayer(String input) {
		try {
			return Bukkit.getOfflinePlayer(UUID.fromString(input));
		} catch (IllegalArgumentException ignored) {
			return Bukkit.getOfflinePlayerIfCached(input);
		}
	}

	private static String displayName(OfflinePlayer player) {
		return player.getName() == null ? player.getUniqueId().toString() : player.getName();
	}
}
