package com.voluble.titanMC.progression.bukkit;

import com.voluble.titanMC.mines.event.MineBlockMinedEvent;
import com.voluble.titanMC.progression.model.CredAmount;
import com.voluble.titanMC.progression.model.CredSource;
import com.voluble.titanMC.progression.service.CredSourceRegistry;
import com.voluble.titanMC.progression.service.MineBlockCredPolicy;
import com.voluble.titanMC.progression.service.ProgressionEngine;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Objects;

public final class MineBlockCredSource implements Listener {

	private final ProgressionEngine engine;
	private final CredSourceRegistry registry;
	private final CredSource sourceId;
	private final MineBlockCredPolicy policy;

	public MineBlockCredSource(
		ProgressionEngine engine,
		CredSourceRegistry registry,
		CredSource sourceId,
		MineBlockCredPolicy policy
	) {
		this.engine = Objects.requireNonNull(engine, "engine");
		this.registry = Objects.requireNonNull(registry, "registry");
		this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
		this.policy = Objects.requireNonNull(policy, "policy");
	}

	public CredSource sourceId() {
		return sourceId;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onMineBlockMined(MineBlockMinedEvent event) {
		if (!registry.isEnabled(sourceId)) return;
		Player player = event.player();
		if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
		CredAmount value = policy.rewardFor(event.material(), event.credMultiplier()).orElse(null);
		if (value == null) return;
		engine.give(player.getUniqueId(), value, sourceId);
	}
}
