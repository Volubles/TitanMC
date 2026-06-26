package com.voluble.titanMC.milestones.ui;

import com.voluble.titanMC.milestones.config.MilestoneCatalog;
import com.voluble.titanMC.milestones.model.MilestoneCategory;
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

final class OverviewMilestoneItemFactory {
	private final MilestoneService milestones;
	private final NumberFormat numbers = NumberFormat.getIntegerInstance(Locale.US);

	OverviewMilestoneItemFactory(MilestoneService milestones) {
		this.milestones = Objects.requireNonNull(milestones, "milestones");
	}

	ItemStack create(Player player, MilestoneCatalog catalog) {
		ItemStack item = new ItemStack(Material.NETHER_STAR);
		ItemMeta meta = item.getItemMeta();
		if (meta == null) return item;

		Summary summary = summary(player, catalog);
		meta.displayName(ChatUtils.formatItem("<gradient:#30bbf1:#275ced><bold>Milestones</bold></gradient>"));
		List<Component> lore = new ArrayList<>();
		lore.add(ChatUtils.formatItem("<gray>Completed: <white>" + summary.completed + " / " + summary.total));
		lore.add(ChatUtils.formatItem("<gray>Active categories: <white>" + summary.activeCategories));
		if (summary.next != null) {
			lore.add(Component.empty());
			lore.add(ChatUtils.formatItem("<gray>Next: <#f7d774>" + summary.next));
		}
		lore.add(Component.empty());
		lore.add(ChatUtils.formatItem("<gray>Select a category below to view its tracks."));
		meta.lore(lore);
		item.setItemMeta(meta);
		return item;
	}

	private Summary summary(Player player, MilestoneCatalog catalog) {
		int completed = 0;
		int total = 0;
		int activeCategories = 0;
		String next = null;
		for (MilestoneCategory category : catalog.categories()) {
			if (!category.enabled()) continue;
			activeCategories++;
			for (MilestoneTrack track : catalog.tracks(category.id())) {
				MilestoneProgress progress = milestones.progress(player.getUniqueId(), track.metric(), track.subject());
				for (MilestoneTier tier : track.tiers()) {
					total++;
					if (milestones.completed(player.getUniqueId(), tier.id()) || progress.amount() >= tier.target()) {
						completed++;
					} else if (next == null) {
						next = track.name() + " - " + tier.name() + " (" + numbers.format(Math.min(progress.amount(), tier.target()))
							+ " / " + numbers.format(tier.target()) + ")";
					}
				}
			}
		}
		return new Summary(completed, total, activeCategories, next);
	}

	private record Summary(int completed, int total, int activeCategories, String next) {
	}
}
