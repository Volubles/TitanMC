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
	private final double credMultiplier;

	public MineBlockMinedEvent(Player player, String mineName, Material material, Location location, double credMultiplier) {
		this.player = Objects.requireNonNull(player, "player");
		this.mineName = Objects.requireNonNull(mineName, "mineName");
		if (mineName.isBlank()) throw new IllegalArgumentException("mineName must not be blank");
		this.material = Objects.requireNonNull(material, "material");
		this.location = Objects.requireNonNull(location, "location").clone();
		if (!Double.isFinite(credMultiplier) || credMultiplier < 0.0D) {
			throw new IllegalArgumentException("credMultiplier must be finite and non-negative");
		}
		this.credMultiplier = credMultiplier;
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

	public double credMultiplier() {
		return credMultiplier;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
