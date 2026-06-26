package com.voluble.titanMC.milestones.ui;

import com.voluble.titanMC.milestones.config.MilestoneConfigurationManager;
import com.voluble.titanMC.milestones.model.MilestoneCategory;
import com.voluble.titanMC.milestones.model.MilestoneTrack;
import io.voluble.michellelib.menu.MenuService;
import io.voluble.michellelib.menu.item.Items.DisplayItem;
import io.voluble.michellelib.menu.template.MenuDefinition;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

final class TrackMilestoneMenu {
	private final MenuService menus;
	private final MilestoneConfigurationManager configuration;
	private final MilestoneItemFactory items;
	private final MilestoneMenuService navigator;

	TrackMilestoneMenu(
		MenuService menus,
		MilestoneConfigurationManager configuration,
		MilestoneItemFactory items,
		MilestoneMenuService navigator
	) {
		this.menus = Objects.requireNonNull(menus, "menus");
		this.configuration = Objects.requireNonNull(configuration, "configuration");
		this.items = Objects.requireNonNull(items, "items");
		this.navigator = Objects.requireNonNull(navigator, "navigator");
	}

	void open(Player player, MilestoneCategory category, MilestoneTrack track, int requestedPage) {
		var config = configuration.current();
		int pages = MilestoneMenuChrome.pages(track.tiers().size(), MilestoneMenuLayout.TIER_SLOTS.size());
		int page = MilestoneMenuChrome.clampPage(requestedPage, pages);
		int start = page * MilestoneMenuLayout.TIER_SLOTS.size();
		int visibleTiers = Math.min(MilestoneMenuLayout.TIER_SLOTS.size(), Math.max(0, track.tiers().size() - start));
		List<Integer> slots = MilestoneMenuLayout.centeredSlots(MilestoneMenuLayout.TIER_SLOTS, visibleTiers);
		MenuDefinition.chest(config.trackMenu().rows())
			.title(MiniMessage.miniMessage().deserialize(config.trackMenu().title().replace("{category}", track.name())))
			.onOpen(context -> {
				for (int slot : MilestoneMenuLayout.footerSlots(config.trackMenu().rows())) {
					context.setItem(slot, new DisplayItem(MilestoneMenuChrome.filler()));
				}
				context.setItem(MilestoneMenuLayout.SUMMARY, new DisplayItem(items.trackDetails(player, track)));
				for (int index = 0; index < slots.size(); index++) {
					int tierIndex = start + index;
					context.setItem(
						slots.get(index),
						new DisplayItem(items.tier(player, track, track.tiers().get(tierIndex)))
					);
				}
				if (page > 0) context.setItem(MilestoneMenuLayout.previousSlot(config.trackMenu().rows()), MilestoneMenuChrome.previousPageButton(
					page - 1, pages, () -> navigator.openTrack(player, category, track, page - 1)
				));
				if (page + 1 < pages) context.setItem(MilestoneMenuLayout.nextSlot(config.trackMenu().rows()), MilestoneMenuChrome.nextPageButton(
					page + 1, pages, () -> navigator.openTrack(player, category, track, page + 1)
				));
				context.setItem(
					MilestoneMenuLayout.centerFooterSlot(config.trackMenu().rows()),
					MilestoneMenuChrome.backButton(category.name(), () -> navigator.openCategory(player, category.id()))
				);
			})
			.build()
			.open(menus, player);
	}

}
