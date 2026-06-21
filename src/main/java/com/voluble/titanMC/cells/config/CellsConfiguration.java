package com.voluble.titanMC.cells.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public record CellsConfiguration(
	long signRefreshTicks,
	List<String> availableSign,
	List<String> rentedSign,
	List<String> resettingSign,
	String rentalMenuTitle,
	String managementMenuTitle,
	String membersMenuTitle,
	int sellbackRefundPercent
) {
	public CellsConfiguration {
		availableSign = lines(availableSign, "available");
		rentedSign = lines(rentedSign, "rented");
		resettingSign = lines(resettingSign, "resetting");
		if (signRefreshTicks < 20L) throw new IllegalArgumentException("sign refresh must be at least one second");
		if (sellbackRefundPercent < 0 || sellbackRefundPercent > 100) throw new IllegalArgumentException("sellback refund must be 0-100");
	}

	public static CellsConfiguration load(FileConfiguration yaml) {
		return new CellsConfiguration(
			yaml.getLong("signs.refresh-seconds", 30L) * 20L,
			yaml.getStringList("signs.available"),
			yaml.getStringList("signs.rented"),
			yaml.getStringList("signs.resetting"),
			yaml.getString("menus.rental-title", "<dark_green>Rent {display_name}"),
			yaml.getString("menus.management-title", "<dark_green>{display_name}"),
			yaml.getString("menus.members-title", "<dark_green>Members: {display_name}"),
			yaml.getInt("sellback.refund-percent", 0)
		);
	}

	private static List<String> lines(List<String> lines, String name) {
		if (lines == null || lines.size() != 4) throw new IllegalArgumentException("signs." + name + " must contain exactly four lines");
		return List.copyOf(lines);
	}
}
