package com.voluble.titanMC.milestones.ui;

import com.voluble.titanMC.milestones.config.MilestoneCatalog;
import com.voluble.titanMC.milestones.config.MilestoneConfigurationManager;
import com.voluble.titanMC.milestones.model.MilestoneCategory;
import io.voluble.michellelib.menu.MenuService;
import io.voluble.michellelib.menu.item.MenuItem;
import io.voluble.michellelib.menu.item.Items.DisplayItem;
import io.voluble.michellelib.menu.template.MenuDefinition;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

final class OverviewMilestoneMenu {
	private final MenuService menus;
	private final MilestoneConfigurationManager configuration;
	private final MilestoneItemFactory items;
	private final MilestoneMenuService navigator;

	OverviewMilestoneMenu(
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

	void open(Player player) {
		var config = configuration.current();
		MilestoneCatalog catalog = config.catalog();
		MenuDefinition.chest(config.overviewMenu().rows())
			.title(MiniMessage.miniMessage().deserialize(config.overviewMenu().title()))
			.onOpen(context -> {
				for (int slot : MilestoneMenuLayout.footerSlots(config.overviewMenu().rows())) {
					context.setItem(slot, new DisplayItem(MilestoneMenuChrome.filler()));
				}
				context.setItem(MilestoneMenuLayout.SUMMARY, new DisplayItem(items.overview(player, catalog)));
				List<MilestoneCategory> categories = catalog.categories();
				for (int index = 0; index < categories.size() && index < MilestoneMenuLayout.CATEGORY_SLOTS.size(); index++) {
					MilestoneCategory category = categories.get(index);
					context.setItem(MilestoneMenuLayout.CATEGORY_SLOTS.get(index), categoryItem(player, category, catalog));
				}
				context.setItem(MilestoneMenuLayout.centerFooterSlot(config.overviewMenu().rows()), MilestoneMenuChrome.closeButton());
			})
			.build()
			.open(menus, player);
	}

	private MenuItem categoryItem(Player player, MilestoneCategory category, MilestoneCatalog catalog) {
		ItemStack stack = items.category(player, category, catalog);
		return new MenuItem() {
			@Override
			public ItemStack render(Player viewer) {
				return stack.clone();
			}

			@Override
			public boolean onClick(io.voluble.michellelib.menu.item.ClickContext context) {
				if (category.enabled()) context.actions().transition(() -> navigator.openCategory(context.player(), category.id()));
				return true;
			}
		};
	}
}
