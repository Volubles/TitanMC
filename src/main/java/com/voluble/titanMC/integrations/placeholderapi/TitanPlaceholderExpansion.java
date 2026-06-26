package com.voluble.titanMC.integrations.placeholderapi;

import com.voluble.titanMC.progression.model.PlayerProgression;
import com.voluble.titanMC.progression.service.ProgressionEngine;
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

public final class TitanPlaceholderExpansion extends PlaceholderExpansion {
	private final Plugin plugin;
	private final Supplier<RankCatalog> catalog;
	private final PlayerRankService ranks;
	private final RankEconomy economy;
	private final ProgressionEngine progression;

	public TitanPlaceholderExpansion(
		Plugin plugin,
		Supplier<RankCatalog> catalog,
		PlayerRankService ranks,
		RankEconomy economy,
		ProgressionEngine progression
	) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.catalog = Objects.requireNonNull(catalog, "catalog");
		this.ranks = Objects.requireNonNull(ranks, "ranks");
		this.economy = Objects.requireNonNull(economy, "economy");
		this.progression = Objects.requireNonNull(progression, "progression");
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
		UUID playerId = player.getUniqueId();
		String key = params.toLowerCase(Locale.ROOT);

		String rankValue = rankPlaceholder(playerId, key);
		if (rankValue != null) return rankValue;
		return progressionPlaceholder(playerId, key);
	}

	private String rankPlaceholder(UUID playerId, String key) {
		return switch (key) {
			case "rank", "rank_display", "rank_id", "ward", "ward_display", "ward_id",
				"next_rank", "next_rank_display", "next_rank_or_max", "next_rank_display_or_max",
				"next_rank_id", "next_ward", "next_ward_display", "next_ward_id",
				"rank_position", "rank_total", "rank_progress_percent", "next_rank_cost",
				"next_rank_cost_formatted", "rankup_balance_formatted", "rankup_money_text",
				"rankup_remaining", "rankup_remaining_formatted", "rankup_remaining_text",
				"rankup_progress_percent" -> resolveRankPlaceholder(playerId, key);
			default -> null;
		};
	}

	private String resolveRankPlaceholder(UUID playerId, String key) {
		Optional<PlayerRank> currentRank = ranks.current(playerId);
		if (currentRank.isEmpty()) return "";

		RankCatalog catalog = this.catalog.get();
		PrisonRank current = catalog.requireRank(currentRank.get().rankId());
		WardDefinition ward = catalog.requireWard(current.wardId());
		Optional<PrisonRank> next = catalog.nextRank(current.id());

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
			case "rankup_balance_formatted" -> formatMoney(balance(playerId));
			case "rankup_money_text" -> next.map(rank -> rankupMoneyText(playerId, rank)).orElse("Max Rank");
			case "rankup_remaining" -> next.map(rank -> Long.toString(rankupRemaining(playerId, rank))).orElse("");
			case "rankup_remaining_formatted" -> next.map(rank -> formatMoney(rankupRemaining(playerId, rank))).orElse("");
			case "rankup_remaining_text" -> next.map(rank -> rankupRemainingText(playerId, rank)).orElse("Max Rank");
			case "rankup_progress_percent" -> next.map(rank -> rankupProgressPercent(playerId, rank)).orElse("100");
			default -> null;
		};
	}

	private String progressionPlaceholder(UUID playerId, String key) {
		PlayerProgression current = progression.current(playerId);
		return switch (key) {
			case "cred_level" -> Integer.toString(current.level());
			case "cred_total" -> Long.toString(current.totalCred());
			case "cred_total_formatted" -> formatNumber(current.totalCred());
			case "cred_next_level" -> current.level() >= progression.maxLevel() ? "" : Integer.toString(current.level() + 1);
			case "cred_next_level_or_max" -> current.level() >= progression.maxLevel()
				? "Max Level"
				: Integer.toString(current.level() + 1);
			case "cred_remaining" -> current.level() >= progression.maxLevel()
				? ""
				: Long.toString(credRemaining(current));
			case "cred_remaining_formatted" -> current.level() >= progression.maxLevel()
				? ""
				: formatNumber(credRemaining(current));
			case "cred_remaining_text" -> current.level() >= progression.maxLevel()
				? "Max Level"
				: formatNumber(credRemaining(current));
			case "cred_progress_percent" -> credProgressPercent(current);
			case "cred_level_text" -> current.level() >= progression.maxLevel()
				? "Lv. " + current.level() + " | Max"
				: "Lv. " + current.level() + " | " + credProgressPercent(current) + "%";
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
		return Math.max(0L, cost - balance(playerId));
	}

	private String rankupRemainingText(UUID playerId, PrisonRank next) {
		long remaining = rankupRemaining(playerId, next);
		return remaining <= 0 ? "Ready" : formatMoney(remaining);
	}

	private String rankupMoneyText(UUID playerId, PrisonRank next) {
		long cost = next.rankup().orElseThrow().cost();
		return formatMoney(balance(playerId)) + " / " + formatMoney(cost);
	}

	private String rankupProgressPercent(UUID playerId, PrisonRank next) {
		long cost = next.rankup().orElseThrow().cost();
		if (cost <= 0) return "100";
		int percent = (int) Math.floor(Math.min(100.0, Math.max(0.0, ((double) balance(playerId) / cost) * 100.0)));
		return Integer.toString(percent);
	}

	private long balance(UUID playerId) {
		if (!economy.available()) return 0L;
		return Math.max(0L, (long) Math.floor(economy.balance(playerId)));
	}

	private long credRemaining(PlayerProgression current) {
		long nextLevelStart = progression.curve().credForLevel(current.level() + 1);
		return Math.max(0L, nextLevelStart - current.totalCred());
	}

	private String credProgressPercent(PlayerProgression current) {
		if (current.level() >= progression.maxLevel()) return "100";
		long currentLevelStart = progression.curve().credForLevel(current.level());
		long nextLevelStart = progression.curve().credForLevel(current.level() + 1);
		long span = Math.max(1L, nextLevelStart - currentLevelStart);
		long inLevel = Math.max(0L, Math.min(span, current.totalCred() - currentLevelStart));
		return Integer.toString((int) Math.round(((double) inLevel / span) * 100.0));
	}

	private String formatMoney(long amount) {
		return "$" + formatNumber(amount);
	}

	private String formatNumber(long amount) {
		return NumberFormat.getIntegerInstance(Locale.US).format(amount);
	}
}
