package com.voluble.titanMC.milestones.bukkit;

import com.voluble.titanMC.display.message.DisplayBroadcastService;
import com.voluble.titanMC.milestones.config.MilestoneConfigurationManager;
import com.voluble.titanMC.milestones.model.MilestoneCompletion;
import com.voluble.titanMC.milestones.model.MilestoneRewards;
import com.voluble.titanMC.milestones.model.MilestoneTier;
import com.voluble.titanMC.milestones.model.MilestoneTrack;
import com.voluble.titanMC.milestones.service.MilestoneUpdate;
import com.voluble.titanMC.progression.model.CredAmount;
import com.voluble.titanMC.progression.model.CredSource;
import com.voluble.titanMC.progression.service.ProgressionEngine;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class MilestoneCompletionHandler {
	private static final CredSource CRED_SOURCE = CredSource.of("milestone");

	private final MilestoneConfigurationManager configuration;
	private final ProgressionEngine progression;
	private final Economy economy;
	private final MilestoneNotificationDispatcher notifications;
	private final NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.US);

	public MilestoneCompletionHandler(
		Plugin plugin,
		Server server,
		MilestoneConfigurationManager configuration,
		ProgressionEngine progression,
		Economy economy,
		DisplayBroadcastService broadcasts
	) {
		this.configuration = Objects.requireNonNull(configuration, "configuration");
		this.progression = Objects.requireNonNull(progression, "progression");
		this.economy = economy;
		this.notifications = new MilestoneNotificationDispatcher(plugin, server, configuration, broadcasts);
	}

	public void handle(Player player, MilestoneUpdate update) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(update, "update");
		for (MilestoneCompletion completion : update.completions()) {
			handle(player, completion);
		}
	}

	private void handle(Player player, MilestoneCompletion completion) {
		var catalog = configuration.current().catalog();
		MilestoneTrack track = catalog.trackForTier(completion.tierId()).orElse(null);
		if (track == null) return;
		Optional<MilestoneTier> tier = track.tiers().stream()
			.filter(candidate -> candidate.id().equals(completion.tierId()))
			.findFirst();
		tier.ifPresent(value -> complete(player, track, value));
	}

	private void complete(Player player, MilestoneTrack track, MilestoneTier tier) {
		award(player, tier.rewards());
		notifications.enqueue(player, track, tier, rewards(tier.rewards()));
	}

	private void award(Player player, MilestoneRewards rewards) {
		if (rewards.cred() > 0) {
			progression.give(player.getUniqueId(), CredAmount.of(rewards.cred()), CRED_SOURCE);
		}
		if (rewards.money() > 0 && economy != null) {
			economy.depositPlayer(player, rewards.money());
		}
	}

	private String rewards(MilestoneRewards rewards) {
		if (rewards.empty()) return "No reward";
		java.util.List<String> parts = new java.util.ArrayList<>();
		if (rewards.cred() > 0) parts.add(numbers.format(rewards.cred()) + " cred");
		if (rewards.money() > 0) parts.add("$" + numbers.format(rewards.money()));
		return String.join(", ", parts);
	}

}
