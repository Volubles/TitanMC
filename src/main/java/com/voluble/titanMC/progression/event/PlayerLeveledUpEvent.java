package com.voluble.titanMC.progression.event;

import com.voluble.titanMC.progression.model.PlayerProgression;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public final class PlayerLeveledUpEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();

	private final UUID playerId;
	private final int previousLevel;
	private final int currentLevel;
	private final PlayerProgression progression;

	public PlayerLeveledUpEvent(UUID playerId, int previousLevel, int currentLevel, PlayerProgression progression) {
		this.playerId = Objects.requireNonNull(playerId, "playerId");
		this.previousLevel = previousLevel;
		this.currentLevel = currentLevel;
		this.progression = Objects.requireNonNull(progression, "progression");
		if (currentLevel <= previousLevel) {
			throw new IllegalArgumentException("currentLevel must be greater than previousLevel");
		}
		if (!progression.playerId().equals(playerId)) {
			throw new IllegalArgumentException("progression player id does not match event player id");
		}
	}

	public UUID playerId() {
		return playerId;
	}

	public int previousLevel() {
		return previousLevel;
	}

	public int currentLevel() {
		return currentLevel;
	}

	public PlayerProgression progression() {
		return progression;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
