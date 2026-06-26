package com.voluble.titanMC.display.chat.processor;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record ChatProcessorContext(Player sender, @Nullable Player viewer) {
	public ChatProcessorContext {
		Objects.requireNonNull(sender, "sender");
	}

	public static ChatProcessorContext defaultRender(Player sender) {
		return new ChatProcessorContext(sender, null);
	}

	public boolean hasViewer() {
		return viewer != null;
	}
}
