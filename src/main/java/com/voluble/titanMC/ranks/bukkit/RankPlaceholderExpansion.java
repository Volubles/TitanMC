package com.voluble.titanMC.ranks.bukkit;

import com.voluble.titanMC.ranks.model.PlayerRank;
import com.voluble.titanMC.ranks.model.PrisonRank;
import com.voluble.titanMC.ranks.model.WardDefinition;
import com.voluble.titanMC.ranks.service.PlayerRankService;
import com.voluble.titanMC.ranks.service.RankCatalog;
import com.voluble.titanMC.ranks.service.RankEconomy;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class RankPlaceholderExpansion extends PlaceholderExpansion {
	private final Plugin plugin;
	private final Supplier<RankCatalog> catalog;
	private final PlayerRankService ranks;
	private final RankEconomy economy;

	public RankPlaceholderExpansion(
		Plugin plugin,
		Supplier<RankCatalog> catalog,
		PlayerRankService ranks,
		RankEconomy economy
	) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.catalog = Objects.requireNonNull(catalog, "catalog");
		this.ranks = Objects.requireNonNull(ranks, "ranks");
		this.economy = Objects.requireNonNull(economy, "economy");
	}

	@Override
	public @NotNull String getIdentifier() {
		return "titanmc";
	}

	@Override
	public @NotNull String getAuthor() {
		return "Voluble";
	}

	@Override
	public @NotNull String getVersion() {
		return plugin.getPluginMeta().getVersion();
	}

	@Override
	public boolean persist() {
		return true;
	}

	@Override
	public String onRequest(OfflinePlayer player, @NotNull String params) {
		if (player == null) return "";
		Optional<PlayerRank> currentRank = ranks.current(player.getUniqueId());
		if (currentRank.isEmpty()) return "";

		RankCatalog catalog = this.catalog.get();
		PrisonRank current = catalog.requireRank(currentRank.get().rankId());
		WardDefinition ward = catalog.requireWard(current.wardId());
		Optional<PrisonRank> next = catalog.nextRank(current.id());
		UUID playerId = player.getUniqueId();
		String key = params.toLowerCase(Locale.ROOT);

		return switch (key) {
			case "rank", "rank_display" -> current.displayName();
			case "rank_id" -> current.id().value();
			case "ward", "ward_display" -> ward.displayName();
			case "ward_id" -> ward.id().value();
			case "next_rank", "next_rank_display" -> next.map(PrisonRank::displayName).orElse("");
			case "next_rank_or_max", "next_rank_display_or_max" -> next.map(PrisonRank::displayName).orElse("Max Rank");
			case "next_rank_id" -> next.map(rank -> rank.id().value()).orElse("");
			case "next_ward", "next_ward_display" -> next
				.map(rank -> catalog.requireWard(rank.wardId()).displayName())
				.orElse("");
			case "next_ward_id" -> next
				.map(rank -> catalog.requireWard(rank.wardId()).id().value())
				.orElse("");
			case "rank_position" -> Integer.toString(catalog.progressionIndex(current.id()) + 1);
			case "rank_total" -> Integer.toString(catalog.ranks().size());
			case "rank_progress_percent" -> rankProgressPercent(current);
			case "next_rank_cost" -> nextRankCost(next).map(cost -> Long.toString(cost)).orElse("");
			case "next_rank_cost_formatted" -> nextRankCost(next).map(this::formatMoney).orElse("");
			case "rankup_remaining" -> next.map(rank -> Long.toString(rankupRemaining(playerId, rank))).orElse("");
			case "rankup_remaining_formatted" -> next.map(rank -> formatMoney(rankupRemaining(playerId, rank))).orElse("");
			case "rankup_remaining_text" -> next.map(rank -> rankupRemainingText(playerId, rank)).orElse("Max Rank");
			case "rankup_progress_percent" -> next.map(rank -> rankupProgressPercent(playerId, rank)).orElse("100");
			default -> null;
		};
	}

	private String rankProgressPercent(PrisonRank current) {
		RankCatalog catalog = this.catalog.get();
		int total = catalog.ranks().size();
		if (total <= 1) return "100";
		int index = catalog.progressionIndex(current.id());
		return Integer.toString(Math.round((100.0f * index) / (total - 1)));
	}

	private Optional<Long> nextRankCost(Optional<PrisonRank> next) {
		return next.flatMap(rank -> rank.rankup().map(requirement -> requirement.cost()));
	}

	private long rankupRemaining(UUID playerId, PrisonRank next) {
		long cost = next.rankup().orElseThrow().cost();
		if (!economy.available()) return cost;
		return Math.max(0L, cost - (long) Math.floor(economy.balance(playerId)));
	}

	private String rankupRemainingText(UUID playerId, PrisonRank next) {
		long remaining = rankupRemaining(playerId, next);
		return remaining <= 0 ? "Ready" : formatMoney(remaining);
	}

	private String rankupProgressPercent(UUID playerId, PrisonRank next) {
		long cost = next.rankup().orElseThrow().cost();
		if (cost <= 0) return "100";
		double balance = economy.available() ? economy.balance(playerId) : 0.0;
		int percent = (int) Math.floor(Math.min(100.0, Math.max(0.0, (balance / cost) * 100.0)));
		return Integer.toString(percent);
	}

	private String formatMoney(long amount) {
		return "$" + NumberFormat.getIntegerInstance(Locale.US).format(amount);
	}
}
