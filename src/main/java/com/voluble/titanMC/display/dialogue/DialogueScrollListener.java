package com.voluble.titanMC.display.dialogue;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;

import java.util.Objects;

public final class DialogueScrollListener implements Listener {
	private final DialogueService dialogues;

	public DialogueScrollListener(DialogueService dialogues) {
		this.dialogues = Objects.requireNonNull(dialogues, "dialogues");
	}

	@EventHandler
	public void onItemHeld(PlayerItemHeldEvent event) {
		if (!dialogues.active(event.getPlayer())) return;
		event.setCancelled(true);
		dialogues.scrollAnswer(event.getPlayer(), direction(event.getPreviousSlot(), event.getNewSlot()));
	}

	private static int direction(int previousSlot, int newSlot) {
		if (previousSlot == 8 && newSlot == 0) return 1;
		if (previousSlot == 0 && newSlot == 8) return -1;
		return newSlot > previousSlot ? 1 : -1;
	}
}
