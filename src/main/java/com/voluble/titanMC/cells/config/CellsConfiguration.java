package com.voluble.titanMC.cells.config;

import com.voluble.titanMC.ranks.model.RankId;
import com.voluble.titanMC.ranks.model.WardId;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CellsConfiguration(
	long signRefreshTicks,
	List<String> availableSign,
	List<String> rentedSign,
	List<String> resettingSign,
	Map<String, CellMenuTemplate> menus,
	Map<WardId, RankId> minimumRanksByWard,
	int sellbackRefundPercent,
	boolean confirmEmptyDatabaseRegionCleanup
) {
	public CellsConfiguration {
		availableSign = lines(availableSign, "available");
		rentedSign = lines(rentedSign, "rented");
		resettingSign = lines(resettingSign, "resetting");
		menus = Map.copyOf(menus);
		minimumRanksByWard = Map.copyOf(minimumRanksByWard);
		if (signRefreshTicks < 20L) throw new IllegalArgumentException("sign refresh must be at least one second");
		if (sellbackRefundPercent < 0 || sellbackRefundPercent > 100) {
			throw new IllegalArgumentException("sellback refund must be 0-100");
		}
	}

	public static CellsConfiguration load(FileConfiguration yaml) {
		Map<String, CellMenuTemplate> menus = defaults(yaml);
		return new CellsConfiguration(
			yaml.getLong("signs.refresh-seconds", 30L) * 20L,
			yaml.getStringList("signs.available"),
			yaml.getStringList("signs.rented"),
			yaml.getStringList("signs.resetting"),
			menus,
			minimumRanks(yaml),
			yaml.getInt("sellback.refund-percent", 0),
			yaml.getBoolean("recovery.confirm-empty-database-region-cleanup", false)
		);
	}

	private static Map<WardId, RankId> minimumRanks(FileConfiguration yaml) {
		Map<WardId, RankId> requirements = new LinkedHashMap<>();
		ConfigurationSection section = yaml.getConfigurationSection("rent-requirements.minimum-rank-by-ward");
		if (section == null) {
			requirements.put(WardId.of("e"), RankId.of("e3"));
			requirements.put(WardId.of("d"), RankId.of("d3"));
			requirements.put(WardId.of("c"), RankId.of("c3"));
			requirements.put(WardId.of("b"), RankId.of("b3"));
			requirements.put(WardId.of("a"), RankId.of("a3"));
			return requirements;
		}
		for (String ward : section.getKeys(false)) {
			String rank = section.getString(ward);
			if (rank == null || rank.isBlank()) {
				throw new IllegalArgumentException("Missing minimum cell rank for ward " + ward);
			}
			requirements.put(WardId.of(ward), RankId.of(rank));
		}
		return requirements;
	}

	public CellMenuTemplate menu(String key) {
		CellMenuTemplate menu = menus.get(key);
		if (menu == null) throw new IllegalArgumentException("Missing cell GUI: " + key);
		return menu;
	}

	private static Map<String, CellMenuTemplate> defaults(FileConfiguration yaml) {
		Map<String, CellMenuTemplate> menus = new LinkedHashMap<>();
		menus.put("rental", menu(yaml, "rental", 3, "<dark_green>Rent {display_name}", Map.of(
			"info", item(Material.CLOCK, 11, "<green><bold>{display_name}", "<gray>Ward: <white>{ward}", "<gray>Price: <gold>${price}", "<gray>Duration: <white>{duration}"),
			"confirm", item(Material.LIME_CONCRETE, 15, "<green><bold>Confirm rental", "<gray>Click to pay <gold>${price}"),
			"close", item(Material.BARRIER, 22, "<red>Close")
		)));
		menus.put("management", menu(yaml, "management", 3, "<dark_green>{display_name}", Map.of(
			"info", item(Material.CLOCK, 4, "<green><bold>{display_name}", "<gray>Ward: <white>{ward}", "<gray>Time left: <yellow>{time_left}", "<gray>Members: <white>{member_count}"),
			"extend", item(Material.EMERALD, 10, "<green><bold>Extend rent", "<gray>Time left: <yellow>{time_left}", "<gray>Cost: <gold>${price}", "<gray>Adds: <white>{duration}", "<gray>Maximum: <white>{max_duration}"),
			"members", item(Material.PLAYER_HEAD, 14, "<aqua><bold>Manage members", "<gray>Add or remove access"),
			"sellback", item(Material.RED_CONCRETE, 16, "<red><bold>Sell back cell", "<gray>This starts a full reset"),
			"close", item(Material.BARRIER, 22, "<red>Close")
		)));
		menus.put("status", menu(yaml, "status", 3, "<dark_green>{display_name}", Map.of(
			"info", item(Material.OAK_SIGN, 13, "<green><bold>{display_name}", "<gray>Ward: <white>{ward}", "<gray>Owner: <white>{owner}", "<gray>Time left: <yellow>{time_left}", "{access}"),
			"close", item(Material.BARRIER, 22, "<red>Close")
		)));
		menus.put("members", menu(yaml, "members", 6, "<dark_green>Members: {display_name}", Map.of(
			"member", item(Material.PLAYER_HEAD, 0, "<white>{player}", "<red>Click to remove"),
			"add", item(Material.LIME_CONCRETE, 45, "<green><bold>Add member", "<gray>Enter a username in chat"),
			"back", item(Material.ARROW, 49, "<yellow>Back"),
			"close", item(Material.BARRIER, 53, "<red>Close")
		)));
		menus.put("sellback", menu(yaml, "sellback", 3, "<dark_red>Confirm sellback", Map.of(
			"keep", item(Material.LIME_CONCRETE, 11, "<green><bold>Keep cell"),
			"confirm", item(Material.RED_CONCRETE, 15, "<red><bold>Sell back permanently", "<red>All items in this cell will be removed", "<gray>and sold through the Storage Auction House.")
		)));
		return menus;
	}

	private static CellMenuTemplate menu(
		FileConfiguration yaml,
		String key,
		int defaultRows,
		String defaultTitle,
		Map<String, CellItemTemplate> defaults
	) {
		ConfigurationSection section = yaml.getConfigurationSection("menus." + key);
		int rows = section == null ? defaultRows : section.getInt("rows", defaultRows);
		String legacyTitle = yaml.getString("menus." + key + "-title", defaultTitle);
		String title = section == null ? legacyTitle : section.getString("title", legacyTitle);
		Map<String, CellItemTemplate> items = new LinkedHashMap<>();
		for (var entry : defaults.entrySet()) {
			ConfigurationSection item = section == null ? null : section.getConfigurationSection("items." + entry.getKey());
			items.put(entry.getKey(), CellItemTemplate.load(item, entry.getValue()));
		}
		return new CellMenuTemplate(rows, title, items);
	}

	private static CellItemTemplate item(Material material, int slot, String name, String... lore) {
		return new CellItemTemplate(material, slot, name, List.of(lore));
	}

	private static List<String> lines(List<String> lines, String name) {
		if (lines == null || lines.size() != 4) {
			throw new IllegalArgumentException("signs." + name + " must contain exactly four lines");
		}
		return List.copyOf(lines);
	}
}
