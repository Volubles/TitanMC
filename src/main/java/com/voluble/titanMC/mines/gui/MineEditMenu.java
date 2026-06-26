package com.voluble.titanMC.mines.gui;

import com.voluble.titanMC.TitanMC;
import com.voluble.titanMC.mines.Mine;
import com.voluble.titanMC.mines.MineManager;
import com.voluble.titanMC.mines.MineValidation;
import com.voluble.titanMC.mines.MineResetDefinition;
import com.voluble.titanMC.regions.selection.SelectionException;
import com.voluble.titanMC.mines.selection.WorldEditSelection;
import com.voluble.titanMC.util.ChatUtils;
import com.voluble.titanMC.util.RegionUtils;
import io.voluble.michellelib.menu.MenuService;
import io.voluble.michellelib.menu.item.ClickContext;
import io.voluble.michellelib.menu.item.Items;
import io.voluble.michellelib.menu.item.MenuItem;
import io.voluble.michellelib.menu.template.MenuDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class MineEditMenu {

	public static void open(Player player, Mine mine, MineManager manager) {
		MenuService menus = TitanMC.getInstance().getMenuService();

		MenuDefinition.chest(3)
			.title(t -> Component.text("Edit Mine: " + mine.getName()).color(NamedTextColor.GOLD)
					.decoration(TextDecoration.BOLD, true))
			.refreshEveryTicks(40L) // Enable auto-refresh every 2 seconds to show live mine stats
			.onOpen(ctx -> {
				// Clear any pending delete confirmation when reopening the menu
				menus.cache().putSession(player.getUniqueId(), "deletePending_" + mine.getName(), Boolean.FALSE);

				ctx.setItem(0, createInfoItem(mine.getName(), manager));
				ctx.setItem(1, createToggleEnabled(mine.getName(), manager));
				ctx.setItem(3, createRedefineButton(mine.getName(), manager));
				ctx.setItem(5, createSafeSpawnButton(mine.getName(), manager));

				ctx.setItem(9, createIntervalItem(mine.getName(), manager));
				ctx.setItem(10, createBatchSizeItem(mine.getName(), manager));
				ctx.setItem(11, createDepletionItem(mine.getName(), manager));
				ctx.setItem(12, createCredMultiplierItem(mine.getName(), manager));

				Mine current = manager.get(mine.getName());
				if (current != null && current.getResetDefinition() instanceof MineResetDefinition.Palette) {
					ctx.setItem(14, createPaletteButton(mine.getName(), manager));
				}
				ctx.setItem(15, createDiggableBlocksButton(mine.getName(), manager));
				ctx.setItem(16, createForceResetButton(mine.getName(), manager));

				ctx.setItem(18, new Items.BackItem(() -> MineListMenu.open(player, manager)));
				ctx.setItem(26, createDeleteButton(mine.getName(), manager));
			})
			.build()
			.open(menus, player);
	}

	private static MenuItem createInfoItem(String mineName, MineManager manager) {
		return new MenuItem() {
			@Override
			public ItemStack render(Player viewer) {
				Mine mine = manager.get(mineName);
				if (mine == null) {
					return new ItemStack(Material.BARRIER);
				}
				ItemStack item = new ItemStack(Material.COMPASS);
				ItemMeta meta = item.getItemMeta();
				if (meta == null) return item;

				meta.displayName(ChatUtils.formatItem("<aqua><bold>" + mine.getName()));

				List<Component> lore = new ArrayList<>();
				lore.add(ChatUtils.formatItem("<gray>Volume: " + mine.getTotalBlockCountSafe() + " blocks"));
				lore.add(ChatUtils.formatItem("<gray>Broken: " + mine.getBrokenBlocks()));
				lore.add(ChatUtils.formatItem("<gray>Remaining: " + mine.getRemainingPercent() + "%"));

				meta.lore(lore);
				item.setItemMeta(meta);
				return item;
			}

			@Override
			public boolean onClick(ClickContext ctx) {
				return true;
			}
		};
	}

	private static MenuItem createToggleEnabled(String mineName, MineManager manager) {
		return new MenuItem() {
			@Override
			public ItemStack render(Player viewer) {
				Mine mine = manager.get(mineName);
				if (mine == null) {
					return new ItemStack(Material.BARRIER);
				}
				ItemStack item = new ItemStack(mine.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE);
				ItemMeta meta = item.getItemMeta();
				if (meta == null) return item;

				String color = mine.isEnabled() ? "<green>" : "<red>";
				meta.displayName(ChatUtils.formatItem(color + "<bold>" + (mine.isEnabled() ? "Enabled" : "Disabled")));
				meta.lore(List.of(ChatUtils.formatItem("<gray>Click to toggle")));
				item.setItemMeta(meta);
				return item;
			}

			@Override
			public boolean onClick(ClickContext ctx) {
				Mine mine = manager.get(mineName);
				if (mine != null) {
					manager.setEnabled(mineName, !mine.isEnabled());
					ctx.actions().transition(() -> MineEditMenu.open(ctx.player(), manager.get(mineName), manager));
				}
				return true;
			}
		};
	}

	private static MenuItem createRedefineButton(String mineName, MineManager manager) {
		return new MenuItem() {
			@Override
			public ItemStack render(Player viewer) {
				ItemStack item = new ItemStack(Material.STONE_BUTTON);
				ItemMeta meta = item.getItemMeta();
				if (meta == null) return item;

				meta.displayName(ChatUtils.formatItem("<yellow><bold>Redefine Bounds"));
				meta.lore(List.of(ChatUtils.formatItem("<green>Use your current WorldEdit selection")));
				item.setItemMeta(meta);
				return item;
			}

			@Override
			public boolean onClick(ClickContext ctx) {
				RegionUtils.Cuboid updated;
				try {
					updated = WorldEditSelection.getCuboid(ctx.player());
				} catch (SelectionException exception) {
					ctx.player().sendMessage(exception.getMessage());
					return true;
				}
				String validationError = MineValidation.validateCuboid(updated);
				if (validationError != null) {
					ctx.player().sendMessage(validationError);
					return true;
				}
				Mine overlap = manager.findOverlap(updated, mineName);
				if (overlap != null) {
					ctx.player().sendMessage("That selection overlaps mine '" + overlap.getName() + "'.");
					return true;
				}
				manager.setCuboid(mineName, updated);
				ctx.actions().transition(() -> MineEditMenu.open(ctx.player(), manager.get(mineName), manager));
				ctx.player().sendMessage(ChatUtils.formatItem("<green>Redefined bounds for " + mineName));
				return true;
			}
		};
	}

	private static MenuItem createSafeSpawnButton(String mineName, MineManager manager) {
		return new MenuItem() {
			@Override
			public ItemStack render(Player viewer) {
				ItemStack item = new ItemStack(Material.RED_BED);
				ItemMeta meta = item.getItemMeta();
				if (meta == null) return item;

				meta.displayName(ChatUtils.formatItem("<aqua><bold>Set Safe Spawn"));
				meta.lore(List.of(ChatUtils.formatItem("<green>Click to set at your location")));
				item.setItemMeta(meta);
				return item;
			}

			@Override
			public boolean onClick(ClickContext ctx) {
				manager.setSafeSpawn(mineName, ctx.player().getLocation().clone());
				ctx.actions().transition(() -> MineEditMenu.open(ctx.player(), manager.get(mineName), manager));
				ctx.player().sendMessage(ChatUtils.formatItem("<green>Set safe spawn for " + mineName));
				return true;
			}
		};
	}

	private static MenuItem createIntervalItem(String mineName, MineManager manager) {
		return new MenuItem() {
			@Override
			public ItemStack render(Player viewer) {
				Mine mine = manager.get(mineName);
				if (mine == null) {
					return new ItemStack(Material.BARRIER);
				}
				ItemStack item = new ItemStack(Material.CLOCK);
				ItemMeta meta = item.getItemMeta();
				if (meta == null) return item;

				meta.displayName(ChatUtils.formatItem("<gold><bold>Reset Interval"));
				meta.lore(List.of(
						ChatUtils.formatItem("<yellow>" + mine.getResetIntervalSeconds() + "s"),
						ChatUtils.formatItem("<gray>Left-click: +60s"),
						ChatUtils.formatItem("<gray>Right-click: -60s"),
						ChatUtils.formatItem("<gray>Shift-click: +300s")
				));
				item.setItemMeta(meta);
				return item;
			}

			@Override
			public boolean onClick(ClickContext ctx) {
				Mine mine = manager.get(mineName);
				if (mine == null) return true;
				int change = 60;
				if (ctx.shiftClick()) change = 300;
				int newValue = ctx.click().toString().contains("LEFT")
						? mine.getResetIntervalSeconds() + change
						: Math.max(1, mine.getResetIntervalSeconds() - change);
				manager.setInterval(mineName, newValue);
				ctx.actions().transition(() -> MineEditMenu.open(ctx.player(), manager.get(mineName), manager));
				return true;
			}
		};
	}

	private static MenuItem createBatchSizeItem(String mineName, MineManager manager) {
		return new MenuItem() {
			@Override
			public ItemStack render(Player viewer) {
				Mine mine = manager.get(mineName);
				if (mine == null) {
					return new ItemStack(Material.BARRIER);
				}
				ItemStack item = new ItemStack(Material.COMPARATOR);
				ItemMeta meta = item.getItemMeta();
				if (meta == null) return item;

				meta.displayName(ChatUtils.formatItem("<gold><bold>Batch Size"));
				meta.lore(List.of(
						ChatUtils.formatItem("<yellow>" + mine.getBatchSizePerTick()),
						ChatUtils.formatItem("<gray>Left-click: +100"),
						ChatUtils.formatItem("<gray>Right-click: -100")
				));
				item.setItemMeta(meta);
				return item;
			}

			@Override
			public boolean onClick(ClickContext ctx) {
				Mine mine = manager.get(mineName);
				if (mine == null) return true;
				int change = 100;
				int newValue = ctx.click().toString().contains("LEFT")
						? mine.getBatchSizePerTick() + change
						: Math.max(1, mine.getBatchSizePerTick() - change);
				manager.setBatchPerTick(mineName, newValue);
				ctx.actions().transition(() -> MineEditMenu.open(ctx.player(), manager.get(mineName), manager));
				return true;
			}
		};
	}

	private static MenuItem createDepletionItem(String mineName, MineManager manager) {
		return new MenuItem() {
			@Override
			public ItemStack render(Player viewer) {
				Mine mine = manager.get(mineName);
				if (mine == null) {
					return new ItemStack(Material.BARRIER);
				}
				ItemStack item = new ItemStack(Material.TARGET);
				ItemMeta meta = item.getItemMeta();
				if (meta == null) return item;

				String text = mine.getAutoResetBelowPercent() >= 0
						? String.valueOf(mine.getAutoResetBelowPercent()) + "%"
						: "Disabled";
				meta.displayName(ChatUtils.formatItem("<gold><bold>Auto Reset"));
				meta.lore(List.of(
						ChatUtils.formatItem("<yellow>" + text),
						ChatUtils.formatItem("<gray>Left-click: +10%"),
						ChatUtils.formatItem("<gray>Right-click: -10%"),
						ChatUtils.formatItem("<dark_red>Shift-click: Disable")
				));
				item.setItemMeta(meta);
				return item;
			}

			@Override
			public boolean onClick(ClickContext ctx) {
				Mine mine = manager.get(mineName);
				if (mine == null) return true;
				if (!ctx.shiftClick() && !manager.supportsDepletion(mine)) {
					ctx.player().sendMessage("Depletion reset is unavailable for this mine's reset and diggable-block configuration.");
					return true;
				}
				if (ctx.shiftClick()) {
					manager.setDepletionThreshold(mineName, -1);
				} else {
					int current = mine.getAutoResetBelowPercent();
					if (current < 0) current = 0; // Enable from disabled state
					int change = 10;
					int newValue = ctx.click().toString().contains("LEFT")
							? Math.min(100, current + change)
							: Math.max(-1, current - change);
					manager.setDepletionThreshold(mineName, newValue);
				}
				ctx.actions().transition(() -> MineEditMenu.open(ctx.player(), manager.get(mineName), manager));
				return true;
			}
		};
	}

	private static MenuItem createCredMultiplierItem(String mineName, MineManager manager) {
		return new MenuItem() {
			@Override
			public ItemStack render(Player viewer) {
				Mine mine = manager.get(mineName);
				if (mine == null) {
					return new ItemStack(Material.BARRIER);
				}
				ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
				ItemMeta meta = item.getItemMeta();
				if (meta == null) return item;

				meta.displayName(ChatUtils.formatItem("<aqua><bold>Cred Multiplier"));
				meta.lore(List.of(
					ChatUtils.formatItem("<yellow>" + formatMultiplier(mine.getCredMultiplier()) + "x"),
					ChatUtils.formatItem("<gray>Default: 1.00x"),
					ChatUtils.formatItem("<gray>Left-click: +0.10x"),
					ChatUtils.formatItem("<gray>Right-click: -0.10x"),
					ChatUtils.formatItem("<gray>Shift-click: +/-0.50x")
				));
				item.setItemMeta(meta);
				return item;
			}

			@Override
			public boolean onClick(ClickContext ctx) {
				Mine mine = manager.get(mineName);
				if (mine == null) return true;
				double change = ctx.shiftClick() ? 0.50D : 0.10D;
				double multiplier = ctx.click().toString().contains("LEFT")
					? mine.getCredMultiplier() + change
					: mine.getCredMultiplier() - change;
				manager.setCredMultiplier(mineName, multiplier);
				ctx.actions().transition(() -> MineEditMenu.open(ctx.player(), manager.get(mineName), manager));
				return true;
			}
		};
	}

	private static String formatMultiplier(double multiplier) {
		return String.format(java.util.Locale.ROOT, "%.2f", multiplier);
	}

	private static MenuItem createPaletteButton(String mineName, MineManager manager) {
		return new MenuItem() {
			@Override
			public ItemStack render(Player viewer) {
				ItemStack item = new ItemStack(Material.PAINTING);
				ItemMeta meta = item.getItemMeta();
				if (meta == null) return item;

				meta.displayName(ChatUtils.formatItem("<gold><bold>Edit Reset Palette"));
				meta.lore(List.of(ChatUtils.formatItem("<green>Click to edit")));
				item.setItemMeta(meta);
				return item;
			}

			@Override
			public boolean onClick(ClickContext ctx) {
				Mine mine = manager.get(mineName);
				if (mine != null) {
					ctx.actions().transition(() -> PaletteEditMenu.open(ctx.player(), mine, manager));
				}
				return true;
			}
		};
	}

	private static MenuItem createDiggableBlocksButton(String mineName, MineManager manager) {
		return new MenuItem() {
			@Override
			public ItemStack render(Player viewer) {
				Mine mine = manager.get(mineName);
				ItemStack item = new ItemStack(Material.IRON_PICKAXE);
				ItemMeta meta = item.getItemMeta();
				meta.displayName(ChatUtils.formatItem("<aqua><bold>Edit Diggable Blocks"));
				String mode = mine != null && mine.getBreakProfile() instanceof com.voluble.titanMC.mines.breaking.MineBreakProfile.AllowList
					? "Allow list" : "Unrestricted";
				meta.lore(List.of(ChatUtils.formatItem("<gray>Mode: <white>" + mode), ChatUtils.formatItem("<green>Click to edit")));
				item.setItemMeta(meta);
				return item;
			}

			@Override
			public boolean onClick(ClickContext ctx) {
				Mine mine = manager.get(mineName);
				if (mine != null) ctx.actions().transition(() -> DiggableBlocksMenu.open(ctx.player(), mine, manager));
				return true;
			}
		};
	}

	private static MenuItem createForceResetButton(String mineName, MineManager manager) {
		return new MenuItem() {
			@Override
			public ItemStack render(Player viewer) {
				ItemStack item = new ItemStack(Material.REPEATING_COMMAND_BLOCK);
				ItemMeta meta = item.getItemMeta();
				if (meta == null) return item;

				meta.displayName(ChatUtils.formatItem("<red><bold>Force Reset"));
				meta.lore(List.of(ChatUtils.formatItem("<gray>Instantly reset mine")));
				item.setItemMeta(meta);
				return item;
			}

			@Override
			public boolean onClick(ClickContext ctx) {
				TitanMC.getInstance().getMineScheduler().forceReset(mineName);
				ctx.actions().transition(() -> MineEditMenu.open(ctx.player(), manager.get(mineName), manager));
				ctx.player().sendMessage(ChatUtils.formatItem("<green>Force reset triggered for " + mineName));
				return true;
			}
		};
	}

	private static MenuItem createDeleteButton(String mineName, MineManager manager) {
		return new MenuItem() {
			@Override
			public ItemStack render(Player viewer) {
				MenuService menus = TitanMC.getInstance().getMenuService();
				Boolean pendingDelete = menus.cache().getSession(viewer.getUniqueId(), "deletePending_" + mineName, Boolean.class);

				ItemStack item = new ItemStack(pendingDelete != null && pendingDelete ? Material.LIME_DYE : Material.RED_CONCRETE);
				ItemMeta meta = item.getItemMeta();
				if (meta == null) return item;

				if (pendingDelete != null && pendingDelete) {
					meta.displayName(ChatUtils.formatItem("<green><bold>Confirm Delete"));
					meta.lore(List.of(ChatUtils.formatItem("<dark_red>Click again to delete the mine configuration"), ChatUtils.formatItem("<gray>Blocks in the world will remain untouched")));
				} else {
					meta.displayName(ChatUtils.formatItem("<red><bold>Delete Mine"));
					meta.lore(List.of(ChatUtils.formatItem("<gray>Blocks will remain untouched"), ChatUtils.formatItem("<gray>Click once to confirm")));
				}
				item.setItemMeta(meta);
				return item;
			}

			@Override
			public boolean onClick(ClickContext ctx) {
				MenuService menus = TitanMC.getInstance().getMenuService();
				Boolean pendingDelete = menus.cache().getSession(ctx.player().getUniqueId(), "deletePending_" + mineName, Boolean.class);

				if (pendingDelete != null && pendingDelete) {
					// Confirmed, actually delete
					menus.cache().putSession(ctx.player().getUniqueId(), "deletePending_" + mineName, Boolean.FALSE);
					manager.delete(mineName);
					ctx.player().sendMessage(Component.text("Deleted mine configuration: " + mineName).color(NamedTextColor.RED));
					ctx.actions().transition(() -> MineListMenu.open(ctx.player(), manager));
					return true;
				} else {
					// First click, mark as pending
					menus.cache().putSession(ctx.player().getUniqueId(), "deletePending_" + mineName, true);
					return true;
				}
			}
		};
	}
}
