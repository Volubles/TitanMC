package com.voluble.titanMC.milestones.ui;

import com.voluble.titanMC.milestones.model.MilestoneProgress;
import com.voluble.titanMC.milestones.model.MilestoneTier;
import com.voluble.titanMC.milestones.model.MilestoneTrack;
import com.voluble.titanMC.milestones.service.MilestoneService;
import com.voluble.titanMC.util.ChatUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

final class TrackMilestoneItemFactory {
	private final MilestoneService milestones;
	private final NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.US);

	TrackMilestoneItemFactory(MilestoneService milestones) {
		this.milestones = Objects.requireNonNull(milestones, "milestones");
	}

	ItemStack createCard(Player player, MilestoneTrack track) {
		ItemStack item = new ItemStack(track.icon());
		ItemMeta meta = item.getItemMeta();
		if (meta == null) return item;
		meta.displayName(ChatUtils.formatItem("<#30bbf1><bold>" + track.name()));
		List<Component> lore = new ArrayList<>();
		lore.add(ChatUtils.formatItem("<gray>Completed: <white>" + completedTiers(player, track) + " / " + track.tiers().size()));
		lore.add(Component.empty());
		nextTier(player, track).ifPresentOrElse(
			tier -> lore.add(ChatUtils.formatItem("<gray>" + cardProgressLabel(track) + ": <#f7d774>" + tier.name() + " <gray>("
				+ numbers.format(Math.min(progress(player, tier).amount(), tier.target())) + " / " + numbers.format(tier.target()) + ")")),
			() -> lore.add(ChatUtils.formatItem("<green>All tiers completed"))
		);
		lore.add(Component.empty());
		lore.add(ChatUtils.formatItem("<green>Click to view tiers"));
		meta.lore(lore);
		item.setItemMeta(meta);
		return item;
	}

	ItemStack createTier(Player player, MilestoneTrack track, MilestoneTier tier) {
		MilestoneProgress progress = progress(player, tier);
		boolean completed = milestones.completed(player.getUniqueId(), tier.id()) || progress.amount() >= tier.target();
		boolean current = !completed && (!track.linear() || nextTier(player, track).map(MilestoneTier::id).orElse("").equals(tier.id()));
		Material material = completed ? Material.LIME_DYE : current ? Material.YELLOW_DYE : Material.GRAY_DYE;
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		if (meta == null) return item;
		String color = completed ? "<green>" : current ? "<#f7d774>" : "<gray>";
		String status = completed ? "DONE" : current ? currentStatus(track) : "LOCKED";
		meta.displayName(ChatUtils.formatItem(color + "<bold>" + tier.name()));
		meta.lore(List.of(
			ChatUtils.formatItem("<gray>Status: " + color + status),
			ChatUtils.formatItem("<gray>Progress: <white>" + numbers.format(Math.min(progress.amount(), tier.target()))
				+ " / " + numbers.format(tier.target())),
			ChatUtils.formatItem("<gray>Reward: <#42d829>" + rewardText(tier))
		));
		item.setItemMeta(meta);
		return item;
	}

	ItemStack createDetails(Player player, MilestoneTrack track) {
		ItemStack item = new ItemStack(track.icon());
		ItemMeta meta = item.getItemMeta();
		if (meta == null) return item;
		meta.displayName(ChatUtils.formatItem("<#30bbf1><bold>" + track.name()));
		List<Component> lore = new ArrayList<>();
		lore.add(ChatUtils.formatItem("<gray>Completed: <white>" + completedTiers(player, track) + " / " + track.tiers().size()));
		lore.add(Component.empty());
		boolean foundCurrent = false;
		for (MilestoneTier tier : track.tiers()) {
			MilestoneProgress progress = progress(player, tier);
			boolean completed = milestones.completed(player.getUniqueId(), tier.id()) || progress.amount() >= tier.target();
			boolean current = !completed && (!track.linear() || !foundCurrent);
			if (current) foundCurrent = true;
			String prefix = completed ? "DONE" : current ? currentStatus(track) : "LOCKED";
			String color = completed ? "<green>" : current ? "<#f7d774>" : "<gray>";
			String value = completed
				? numbers.format(tier.target()) + " / " + numbers.format(tier.target())
				: numbers.format(Math.min(progress.amount(), tier.target())) + " / " + numbers.format(tier.target());
			lore.add(ChatUtils.formatItem(color + prefix + " <white>" + tier.name() + " <dark_gray>- <gray>" + value));
		}
		meta.lore(lore);
		item.setItemMeta(meta);
		return item;
	}

	private int completedTiers(Player player, MilestoneTrack track) {
		int completed = 0;
		for (MilestoneTier tier : track.tiers()) {
			MilestoneProgress progress = progress(player, tier);
			if (milestones.completed(player.getUniqueId(), tier.id()) || progress.amount() >= tier.target()) completed++;
		}
		return completed;
	}

	private Optional<MilestoneTier> nextTier(Player player, MilestoneTrack track) {
		return track.tiers().stream()
			.filter(tier -> !milestones.completed(player.getUniqueId(), tier.id()) && progress(player, tier).amount() < tier.target())
			.findFirst();
	}

	private String currentStatus(MilestoneTrack track) {
		return track.linear() ? "NEXT" : "ACTIVE";
	}

	private String cardProgressLabel(MilestoneTrack track) {
		return track.linear() ? "Next" : "Active";
	}

	private MilestoneProgress progress(Player player, MilestoneTier tier) {
		return milestones.progress(player.getUniqueId(), tier.metric(), tier.subject());
	}

	private String rewardText(MilestoneTier tier) {
		if (tier.rewards().empty()) return "None";
		List<String> rewards = new ArrayList<>();
		if (tier.rewards().cred() > 0) rewards.add(numbers.format(tier.rewards().cred()) + " cred");
		if (tier.rewards().money() > 0) rewards.add("$" + numbers.format(tier.rewards().money()));
		return String.join(", ", rewards);
	}
}
