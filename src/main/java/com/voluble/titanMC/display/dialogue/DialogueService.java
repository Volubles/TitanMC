package com.voluble.titanMC.display.dialogue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DialogueService implements AutoCloseable {
	private static final ShadowColor NO_SHADOW = ShadowColor.shadowColor(0, 0, 0, 0);

	private final Plugin plugin;
	private final DialogueRenderer renderer;
	private final Map<UUID, RunningDialogue> sessions = new HashMap<>();

	public DialogueService(Plugin plugin, DialogueRenderer renderer) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.renderer = Objects.requireNonNull(renderer, "renderer");
	}

	public void show(Player player, DialogueDefinition definition) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(definition, "definition");
		clear(player);
		DialogueSession session = new DialogueSession(player, definition);
		BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(
			plugin,
			() -> tick(player, session),
			0L,
			definition.theme().typingSpeedTicks()
		);
		sessions.put(player.getUniqueId(), new RunningDialogue(session, task));
	}

	public void skipTyping(Player player) {
		RunningDialogue running = sessions.get(player.getUniqueId());
		if (running == null) return;
		if (running.session().typingComplete()) {
			running.session().advanceTargetPage();
		} else {
			if (running.session().definition().settings().preventSkip()) return;
			running.session().showAll();
		}
		sendActionBar(player, running.session());
	}

	public void scrollAnswer(Player player, int delta) {
		RunningDialogue running = sessions.get(player.getUniqueId());
		if (running == null || !running.session().typingComplete()) return;
		boolean changed = running.session().selectAnswer(delta);
		if (changed) {
			running.session().definition().theme().selectionSound()
				.ifPresent(sound -> player.playSound(sound.sound()));
		}
		sendActionBar(player, running.session());
	}

	public void clear(Player player) {
		RunningDialogue running = sessions.remove(player.getUniqueId());
		if (running != null) running.task().cancel();
		player.sendActionBar(Component.empty());
	}

	public boolean active(Player player) {
		return sessions.containsKey(player.getUniqueId());
	}

	@Override
	public void close() {
		for (RunningDialogue running : sessions.values()) {
			running.task().cancel();
		}
		sessions.clear();
	}

	private void tick(Player player, DialogueSession session) {
		if (!player.isOnline()) {
			RunningDialogue running = sessions.remove(session.playerId());
			if (running != null) running.task().cancel();
			return;
		}
		boolean revealed = session.revealNextCharacter();
		if (revealed) {
			session.definition().theme().typingSound()
				.ifPresent(sound -> player.playSound(sound.sound()));
		}
		sendActionBar(player, session);
	}

	private void sendActionBar(Player player, DialogueSession session) {
		player.sendActionBar(renderer.render(session).shadowColor(NO_SHADOW));
	}

	private record RunningDialogue(DialogueSession session, BukkitTask task) {
	}
}
