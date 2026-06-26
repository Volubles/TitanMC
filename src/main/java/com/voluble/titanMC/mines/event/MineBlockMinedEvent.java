package com.voluble.titanMC.mines.event;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class MineBlockMinedEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();

	private final Player player;
	private final String mineName;
	private final Material material;
	private final Location location;

	public MineBlockMinedEvent(Player player, String mineName, Material material, Location location) {
		this.player = Objects.requireNonNull(player, "player");
		this.mineName = Objects.requireNonNull(mineName, "mineName");
		if (mineName.isBlank()) throw new IllegalArgumentException("mineName must not be blank");
		this.material = Objects.requireNonNull(material, "material");
		this.location = Objects.requireNonNull(location, "location").clone();
	}

	public Player player() {
		return player;
	}

	public String mineName() {
		return mineName;
	}

	public Material material() {
		return material;
	}

	public Location location() {
		return location.clone();
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
