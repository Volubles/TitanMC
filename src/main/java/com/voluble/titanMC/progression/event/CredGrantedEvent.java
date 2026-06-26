package com.voluble.titanMC.progression.event;

import com.voluble.titanMC.progression.model.CredSource;
import com.voluble.titanMC.progression.model.PlayerProgression;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public final class CredGrantedEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();

	private final UUID playerId;
	private final PlayerProgression previous;
	private final PlayerProgression current;
	private final long applied;
	private final CredSource source;

	public CredGrantedEvent(
		UUID playerId,
		PlayerProgression previous,
		PlayerProgression current,
		long applied,
		CredSource source
	) {
		this.playerId = Objects.requireNonNull(playerId, "playerId");
		this.previous = Objects.requireNonNull(previous, "previous");
		this.current = Objects.requireNonNull(current, "current");
		this.applied = applied;
		this.source = Objects.requireNonNull(source, "source");
		if (!previous.playerId().equals(playerId) || !current.playerId().equals(playerId)) {
			throw new IllegalArgumentException("previous/current player id does not match event player id");
		}
	}

	public UUID playerId() {
		return playerId;
	}

	public PlayerProgression previous() {
		return previous;
	}

	public PlayerProgression current() {
		return current;
	}

	/**
	 * Signed delta actually applied. Positive for a grant, negative for a revoke,
	 * zero when an award was capped (already at max level) or floored (already at zero).
	 */
	public long applied() {
		return applied;
	}

	public CredSource source() {
		return source;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
