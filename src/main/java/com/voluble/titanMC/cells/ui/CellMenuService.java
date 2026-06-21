package com.voluble.titanMC.cells.ui;

import com.voluble.titanMC.cells.CellManagementService;
import com.voluble.titanMC.cells.CellManager;
import com.voluble.titanMC.cells.CellRentalService;
import com.voluble.titanMC.cells.config.CellItemTemplate;
import com.voluble.titanMC.cells.config.CellMenuTemplate;
import com.voluble.titanMC.cells.config.CellsConfigurationManager;
import com.voluble.titanMC.cells.model.CellDefinition;
import com.voluble.titanMC.cells.model.CellLease;
import com.voluble.titanMC.util.ChatUtils;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.voluble.michellelib.menu.MenuService;
import io.voluble.michellelib.menu.item.ClickContext;
import io.voluble.michellelib.menu.item.Items;
import io.voluble.michellelib.menu.item.MenuItem;
import io.voluble.michellelib.menu.template.MenuDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class CellMenuService implements Listener {
	private final Plugin plugin;
	private final MenuService menus;
	private final CellManager cells;
	private final CellRentalService rentals;
	private final CellManagementService management;
	private final CellsConfigurationManager configuration;
	private final Map<UUID, String> pendingMemberAdds = new ConcurrentHashMap<>();

	public CellMenuService(
		Plugin plugin,
		MenuService menus,
		CellManager cells,
		CellRentalService rentals,
		CellManagementService management,
		CellsConfigurationManager configuration
	) {
		this.plugin = plugin;
		this.menus = menus;
		this.cells = cells;
		this.rentals = rentals;
		this.management = management;
		this.configuration = configuration;
	}

	public void openFor(Player player, String cellId) {
		CellDefinition cell = cells.get(cellId);
		if (cell == null) {
			player.sendMessage("Unknown cell.");
			return;
		}
		CellLease lease = cells.lease(cellId);
		if (lease == null) openRental(player, cell);
		else if (lease.ownerId().equals(player.getUniqueId())) openManagement(player, cell);
		else openStatus(player, cell, lease);
	}

	@EventHandler
	public void onChat(AsyncChatEvent event) {
		String cellId = pendingMemberAdds.remove(event.getPlayer().getUniqueId());
		if (cellId == null) return;
		event.setCancelled(true);
		String username = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
		Bukkit.getScheduler().runTask(plugin, () -> addMemberFromChat(event.getPlayer(), cellId, username));
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		pendingMemberAdds.remove(event.getPlayer().getUniqueId());
	}

	private void addMemberFromChat(Player owner, String cellId, String username) {
		CellDefinition cell = cells.get(cellId);
		if (cell == null || !management.isOwner(owner, cellId)) return;
		OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(username);
		if (target == null || target.getName() == null || !target.getName().equalsIgnoreCase(username)) {
			owner.sendMessage("No player named '" + username + "' was found. Open the members menu and try again.");
			return;
		}
		if (management.addMember(owner, cellId, target.getUniqueId())) {
			owner.sendMessage("Added " + target.getName() + " to the cell.");
			openMembers(owner, cell);
		}
	}

	private void openRental(Player player, CellDefinition cell) {
		CellMenuTemplate menu = configuration.current().menu("rental");
		Map<String, String> values = values(cell, null);
		MenuDefinition.chest(menu.rows()).title(title(menu, values)).onOpen(context -> {
			setDisplay(context::setItem, menu.item("info"), values);
			setButton(context::setItem, menu.item("confirm"), values, click -> {
				click.actions().close();
				rentals.rent(click.player(), cell.id());
			});
			setButton(context::setItem, menu.item("close"), values, click -> click.actions().close());
		}).build().open(menus, player);
	}

	private void openManagement(Player player, CellDefinition cell) {
		CellMenuTemplate menu = configuration.current().menu("management");
		MenuDefinition.chest(menu.rows()).title(title(menu, values(cell, cells.lease(cell.id())))).refreshEveryTicks(20L).onOpen(context -> {
			CellLease lease = cells.lease(cell.id());
			if (lease == null) {
				context.setItem(menu.item("info").slot(), new Items.DisplayItem(item(menu.item("info"), Map.of("display_name", "Lease ended"))));
				return;
			}
			Map<String, String> values = values(cell, lease);
			setDisplay(context::setItem, menu.item("info"), values);
			setButton(context::setItem, menu.item("extend"), values, click -> management.extend(
				click.player(), cell.id(), success -> {
					if (success) openManagement(click.player(), cell);
				}
			));
			setButton(context::setItem, menu.item("members"), values, click ->
				click.actions().transition(() -> openMembers(click.player(), cell)));
			setButton(context::setItem, menu.item("sellback"), values, click ->
				click.actions().transition(() -> openSellbackConfirmation(click.player(), cell)));
			setButton(context::setItem, menu.item("close"), values, click -> click.actions().close());
		}).build().open(menus, player);
	}

	private void openStatus(Player player, CellDefinition cell, CellLease lease) {
		CellMenuTemplate menu = configuration.current().menu("status");
		Map<String, String> values = values(cell, lease);
		OfflinePlayer owner = Bukkit.getOfflinePlayer(lease.ownerId());
		values.put("owner", owner.getName() == null ? owner.getUniqueId().toString() : owner.getName());
		values.put("access", cells.members(cell.id()).contains(player.getUniqueId())
			? "<green>You are a member"
			: "<gray>This cell is occupied");
		MenuDefinition.chest(menu.rows()).title(title(menu, values)).onOpen(context -> {
			setDisplay(context::setItem, menu.item("info"), values);
			setButton(context::setItem, menu.item("close"), values, click -> click.actions().close());
		}).build().open(menus, player);
	}

	private void openSellbackConfirmation(Player player, CellDefinition cell) {
		CellMenuTemplate menu = configuration.current().menu("sellback");
		Map<String, String> values = values(cell, cells.lease(cell.id()));
		MenuDefinition.chest(menu.rows()).title(title(menu, values)).onOpen(context -> {
			setButton(context::setItem, menu.item("keep"), values, click ->
				click.actions().transition(() -> openManagement(click.player(), cell)));
			setButton(context::setItem, menu.item("confirm"), values, click -> {
				click.actions().close();
				management.returnCell(click.player(), cell.id());
			});
		}).build().open(menus, player);
	}

	private void openMembers(Player player, CellDefinition cell) {
		CellMenuTemplate menu = configuration.current().menu("members");
		Map<String, String> values = values(cell, cells.lease(cell.id()));
		MenuDefinition.chest(menu.rows()).title(title(menu, values)).onOpen(context -> {
			CellItemTemplate memberTemplate = menu.item("member");
			Set<Integer> reserved = new HashSet<>(List.of(
				menu.item("add").slot(), menu.item("back").slot(), menu.item("close").slot()
			));
			int slot = memberTemplate.slot();
			for (UUID memberId : cells.members(cell.id())) {
				while (reserved.contains(slot) && slot < menu.rows() * 9) slot++;
				if (slot >= menu.rows() * 9) break;
				OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);
				Map<String, String> memberValues = new HashMap<>(values);
				memberValues.put("player", member.getName() == null ? memberId.toString() : member.getName());
				context.setItem(slot++, playerButton(member, memberTemplate, memberValues, click -> {
					if (management.removeMember(click.player(), cell.id(), memberId)) {
						click.actions().transition(() -> openMembers(click.player(), cell));
					}
				}));
			}
			setButton(context::setItem, menu.item("add"), values, click -> {
				pendingMemberAdds.put(click.player().getUniqueId(), cell.id());
				click.actions().close();
				click.player().sendMessage("Type the player's username in chat.");
			});
			setButton(context::setItem, menu.item("back"), values, click ->
				click.actions().transition(() -> openManagement(click.player(), cell)));
			setButton(context::setItem, menu.item("close"), values, click -> click.actions().close());
		}).build().open(menus, player);
	}

	private static MenuItem playerButton(
		OfflinePlayer player,
		CellItemTemplate template,
		Map<String, String> values,
		Consumer<ClickContext> click
	) {
		ItemStack stack = item(template, values);
		if (stack.getItemMeta() instanceof SkullMeta meta) {
			meta.setOwningPlayer(player);
			stack.setItemMeta(meta);
		}
		return button(stack, click);
	}

	private static void setDisplay(ItemSetter setter, CellItemTemplate template, Map<String, String> values) {
		setter.set(template.slot(), new Items.DisplayItem(item(template, values)));
	}

	private static void setButton(
		ItemSetter setter,
		CellItemTemplate template,
		Map<String, String> values,
		Consumer<ClickContext> click
	) {
		setter.set(template.slot(), button(item(template, values), click));
	}

	private static MenuItem button(ItemStack stack, Consumer<ClickContext> click) {
		return new MenuItem() {
			@Override
			public ItemStack render(Player viewer) {
				return stack.clone();
			}

			@Override
			public boolean onClick(ClickContext context) {
				click.accept(context);
				return true;
			}
		};
	}

	private static ItemStack item(CellItemTemplate template, Map<String, String> values) {
		ItemStack item = new ItemStack(template.material());
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			meta.displayName(ChatUtils.formatItem(render(template.name(), values)));
			List<Component> lore = new ArrayList<>();
			for (String line : template.lore()) lore.add(ChatUtils.formatItem(render(line, values)));
			meta.lore(lore);
			item.setItemMeta(meta);
		}
		return item;
	}

	private static Component title(CellMenuTemplate menu, Map<String, String> values) {
		return MiniMessage.miniMessage().deserialize(render(menu.title(), values));
	}

	private Map<String, String> values(CellDefinition cell, CellLease lease) {
		Map<String, String> values = new HashMap<>();
		values.put("id", cell.id());
		values.put("display_name", cell.displayName());
		values.put("ward", cell.wardId().value());
		values.put("price", Long.toString(cell.rentPrice()));
		values.put("duration", duration(cell.rentDurationSeconds() * 1000L));
		values.put("max_duration", duration(cell.maxRentDurationSeconds() * 1000L));
		values.put("member_count", Integer.toString(cells.members(cell.id()).size()));
		values.put("time_left", lease == null ? "0m" : duration(lease.expiresAtEpochMillis() - System.currentTimeMillis()));
		return values;
	}

	private static String render(String template, Map<String, String> values) {
		String rendered = template;
		for (var entry : values.entrySet()) rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
		return rendered;
	}

	private static String duration(long millis) {
		Duration value = Duration.ofMillis(Math.max(0, millis));
		long days = value.toDays();
		if (days > 0) return days + "d " + value.minusDays(days).toHours() + "h";
		long hours = value.toHours();
		if (hours > 0) return hours + "h " + value.minusHours(hours).toMinutes() + "m";
		long minutes = value.toMinutes();
		if (minutes > 0) return minutes + "m";
		return value.toSeconds() + "s";
	}

	@FunctionalInterface
	private interface ItemSetter {
		void set(int slot, MenuItem item);
	}
}
