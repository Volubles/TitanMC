package com.voluble.titanMC.milestones.ui;

import com.voluble.titanMC.milestones.config.MilestoneCatalog;
import com.voluble.titanMC.milestones.model.MilestoneCategory;
import com.voluble.titanMC.milestones.model.MilestoneTier;
import com.voluble.titanMC.milestones.model.MilestoneTrack;
import com.voluble.titanMC.milestones.service.MilestoneService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

final class MilestoneItemFactory {
	private final OverviewMilestoneItemFactory overview;
	private final CategoryMilestoneItemFactory categories;
	private final TrackMilestoneItemFactory tracks;

	MilestoneItemFactory(MilestoneService milestones) {
		Objects.requireNonNull(milestones, "milestones");
		overview = new OverviewMilestoneItemFactory(milestones);
		categories = new CategoryMilestoneItemFactory(milestones);
		tracks = new TrackMilestoneItemFactory(milestones);
	}

	ItemStack overview(Player player, MilestoneCatalog catalog) {
		return overview.create(player, catalog);
	}

	ItemStack category(Player player, MilestoneCategory category, MilestoneCatalog catalog) {
		return categories.create(player, category, catalog);
	}

	ItemStack track(Player player, MilestoneTrack track) {
		return tracks.createCard(player, track);
	}

	ItemStack tier(Player player, MilestoneTrack track, MilestoneTier tier) {
		return tracks.createTier(player, track, tier);
	}

	ItemStack trackDetails(Player player, MilestoneTrack track) {
		return tracks.createDetails(player, track);
	}
}
