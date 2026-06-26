package com.voluble.titanMC.milestones.ui;

import com.voluble.titanMC.util.ChatUtils;
import io.voluble.michellelib.menu.item.MenuItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.function.Consumer;

final class MilestoneMenuChrome {
	private MilestoneMenuChrome() {
	}

	static MenuItem previousPageButton(int targetPage, int pages, Runnable action) {
		return button(
			Material.ARROW,
			"<#f7d774><bold>Previous Page",
			List.of(
				"<gray>Open page <white>" + (targetPage + 1) + "</white> of <white>" + pages + "</white>.",
				"<green>Click to navigate."
			),
			context -> context.actions().transition(action)
		);
	}

	static MenuItem nextPageButton(int targetPage, int pages, Runnable action) {
		return button(
			Material.ARROW,
			"<#f7d774><bold>Next Page",
			List.of(
				"<gray>Open page <white>" + (targetPage + 1) + "</white> of <white>" + pages + "</white>.",
				"<green>Click to navigate."
			),
			context -> context.actions().transition(action)
		);
	}

	static MenuItem backButton(String destination, Runnable action) {
		return button(
			Material.ARROW,
			"<#30bbf1><bold>Back",
			List.of(
				"<gray>Return to <white>" + destination + "</white>.",
				"<green>Click to go back."
			),
			context -> context.actions().transition(action)
		);
	}

	static MenuItem closeButton() {
		return button(
			Material.BARRIER,
			"<#d43030><bold>Close",
			List.of("<gray>Close this menu."),
			context -> context.actions().close()
		);
	}

	private static MenuItem button(
		Material material,
		String name,
		List<String> lore,
		Consumer<io.voluble.michellelib.menu.item.ClickContext> clickAction
	) {
		ItemStack stack = new ItemStack(material);
		ItemMeta meta = stack.getItemMeta();
		if (meta != null) {
			meta.displayName(ChatUtils.formatItem(name));
			meta.lore(lore.stream().map(ChatUtils::formatItem).toList());
			stack.setItemMeta(meta);
		}
		return new MenuItem() {
			@Override
			public ItemStack render(Player viewer) {
				return stack.clone();
			}

			@Override
			public boolean onClick(io.voluble.michellelib.menu.item.ClickContext context) {
				clickAction.accept(context);
				return true;
			}
		};
	}

	static int pages(int size, int pageSize) {
		if (size <= 0) return 1;
		return Math.max(1, (int) Math.ceil((double) size / pageSize));
	}

	static int clampPage(int page, int pages) {
		return Math.max(0, Math.min(Math.max(0, pages - 1), page));
	}
}
