package com.voluble.titanMC.progression.bukkit;

import com.voluble.titanMC.progression.event.CredGrantedEvent;
import com.voluble.titanMC.progression.model.PlayerProgression;
import com.voluble.titanMC.progression.service.ProgressionEngine;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class ProgressionBarService implements Listener, AutoCloseable {
	private static final Duration DEFAULT_VISIBILITY = Duration.ofSeconds(30);
	private static final long UPDATE_THROTTLE_MILLIS = 500L;

	private final Plugin plugin;
	private final Server server;
	private final ProgressionEngine engine;
	private final LongSupplier clock;
	private final Map<UUID, ActiveBar> active = new LinkedHashMap<>();
	private BukkitTask task;

	public ProgressionBarService(Plugin plugin, ProgressionEngine engine) {
		this(plugin, engine, System::currentTimeMillis);
	}

	ProgressionBarService(Plugin plugin, ProgressionEngine engine, LongSupplier clock) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.server = plugin.getServer();
		this.engine = Objects.requireNonNull(engine, "engine");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	public void start() {
		if (task != null) return;
		task = server.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
	}

	public void show(Player player) {
		show(player, DEFAULT_VISIBILITY);
	}

	public void show(Player player, Duration duration) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(duration, "duration");
		long now = clock.getAsLong();
		long expiresAt = now + Math.max(1_000L, duration.toMillis());
		ActiveBar existing = active.get(player.getUniqueId());
		if (existing == null) {
			existing = new ActiveBar(createBar(), expiresAt, 0L);
			active.put(player.getUniqueId(), existing);
			player.showBossBar(existing.bar);
		} else {
			existing.expiresAt = expiresAt;
		}
		update(player, existing, engine.current(player.getUniqueId()), now, true);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onCredGranted(CredGrantedEvent event) {
		if (event.applied() == 0L) return;
		ActiveBar bar = active.get(event.playerId());
		if (bar == null) return;
		long now = clock.getAsLong();
		if (bar.expiresAt <= now) {
			hide(event.playerId(), bar);
			return;
		}
		Player player = server.getPlayer(event.playerId());
		if (player == null) {
			hide(event.playerId(), bar);
			return;
		}
		update(player, bar, event.current(), now, false);
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		ActiveBar bar = active.remove(event.getPlayer().getUniqueId());
		if (bar != null) event.getPlayer().hideBossBar(bar.bar);
	}

	private BossBar createBar() {
		return BossBar.bossBar(Component.empty(), 0.0F, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
	}

	private void update(Player player, ActiveBar activeBar, PlayerProgression progression, long now, boolean force) {
		if (!force && now - activeBar.lastUpdatedAt < UPDATE_THROTTLE_MILLIS) return;
		ProgressionBarView view = ProgressionBarView.from(progression, engine.curve(), engine.maxLevel());
		activeBar.bar.name(Component.text(view.title()));
		activeBar.bar.progress((float) view.progress());
		activeBar.lastUpdatedAt = now;
	}

	private void tick() {
		long now = clock.getAsLong();
		Iterator<Map.Entry<UUID, ActiveBar>> iterator = active.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, ActiveBar> entry = iterator.next();
			if (entry.getValue().expiresAt > now) continue;
			Player player = server.getPlayer(entry.getKey());
			if (player != null) player.hideBossBar(entry.getValue().bar);
			iterator.remove();
		}
	}

	private void hide(UUID playerId, ActiveBar bar) {
		active.remove(playerId);
		Player player = server.getPlayer(playerId);
		if (player != null) player.hideBossBar(bar.bar);
	}

	@Override
	public void close() {
		if (task != null) {
			task.cancel();
			task = null;
		}
		for (Map.Entry<UUID, ActiveBar> entry : active.entrySet()) {
			Player player = server.getPlayer(entry.getKey());
			if (player != null) player.hideBossBar(entry.getValue().bar);
		}
		active.clear();
	}

	private static final class ActiveBar {
		private final BossBar bar;
		private long expiresAt;
		private long lastUpdatedAt;

		private ActiveBar(BossBar bar, long expiresAt, long lastUpdatedAt) {
			this.bar = bar;
			this.expiresAt = expiresAt;
			this.lastUpdatedAt = lastUpdatedAt;
		}
	}
}
