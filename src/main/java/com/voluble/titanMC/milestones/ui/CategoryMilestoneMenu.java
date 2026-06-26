package com.voluble.titanMC.milestones.ui;

import com.voluble.titanMC.milestones.config.MilestoneCatalog;
import com.voluble.titanMC.milestones.config.MilestoneConfigurationManager;
import com.voluble.titanMC.milestones.model.MilestoneCategory;
import com.voluble.titanMC.milestones.model.MilestoneTrack;
import io.voluble.michellelib.menu.MenuService;
import io.voluble.michellelib.menu.item.MenuItem;
import io.voluble.michellelib.menu.item.Items.DisplayItem;
import io.voluble.michellelib.menu.template.MenuDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

final class CategoryMilestoneMenu {
	private final MenuService menus;
	private final MilestoneConfigurationManager configuration;
	private final MilestoneItemFactory items;
	private final MilestoneMenuService navigator;

	CategoryMilestoneMenu(
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

	void open(Player player, String categoryId, int requestedPage) {
		var config = configuration.current();
		MilestoneCatalog catalog = config.catalog();
		MilestoneCategory category = catalog.category(categoryId).orElse(null);
		if (category == null || !category.enabled()) {
			navigator.openOverview(player);
			return;
		}
		List<MilestoneTrack> tracks = catalog.tracks(category.id());
		int pages = MilestoneMenuChrome.pages(tracks.size(), MilestoneMenuLayout.TRACK_SLOTS.size());
		int page = MilestoneMenuChrome.clampPage(requestedPage, pages);
		int start = page * MilestoneMenuLayout.TRACK_SLOTS.size();
		int visibleTracks = Math.min(MilestoneMenuLayout.TRACK_SLOTS.size(), Math.max(0, tracks.size() - start));
		List<Integer> slots = MilestoneMenuLayout.centeredSlots(MilestoneMenuLayout.TRACK_SLOTS, visibleTracks);
		MenuDefinition.chest(config.categoryMenu().rows())
			.title(title(config.categoryMenu().title(), category))
			.onOpen(context -> {
				for (int slot : MilestoneMenuLayout.footerSlots(config.categoryMenu().rows())) {
					context.setItem(slot, new DisplayItem(MilestoneMenuChrome.filler()));
				}
				context.setItem(MilestoneMenuLayout.SUMMARY, new DisplayItem(items.category(player, category, catalog)));
				for (int index = 0; index < slots.size(); index++) {
					int trackIndex = start + index;
					context.setItem(slots.get(index), trackItem(player, category, tracks.get(trackIndex)));
				}
				if (page > 0) context.setItem(MilestoneMenuLayout.previousSlot(config.categoryMenu().rows()), MilestoneMenuChrome.previousPageButton(
					page - 1, pages, () -> navigator.openCategory(player, category.id(), page - 1)
				));
				if (page + 1 < pages) context.setItem(MilestoneMenuLayout.nextSlot(config.categoryMenu().rows()), MilestoneMenuChrome.nextPageButton(
					page + 1, pages, () -> navigator.openCategory(player, category.id(), page + 1)
				));
				context.setItem(
					MilestoneMenuLayout.centerFooterSlot(config.categoryMenu().rows()),
					MilestoneMenuChrome.backButton("Milestones", () -> navigator.openOverview(player))
				);
			})
			.build()
			.open(menus, player);
	}

	private MenuItem trackItem(Player player, MilestoneCategory category, MilestoneTrack track) {
		ItemStack stack = items.track(player, track);
		return new MenuItem() {
			@Override
			public ItemStack render(Player viewer) {
				return stack.clone();
			}

			@Override
			public boolean onClick(io.voluble.michellelib.menu.item.ClickContext context) {
				context.actions().transition(() -> navigator.openTrack(context.player(), category, track, 0));
				return true;
			}
		};
	}

	private Component title(String template, MilestoneCategory category) {
		return MiniMessage.miniMessage().deserialize(template.replace("{category}", category.name()));
	}
}
