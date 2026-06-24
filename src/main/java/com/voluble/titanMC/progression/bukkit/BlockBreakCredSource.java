package com.voluble.titanMC.progression.bukkit;

import com.voluble.titanMC.progression.model.CredAmount;
import com.voluble.titanMC.progression.model.CredSource;
import com.voluble.titanMC.progression.service.CredSourceRegistry;
import com.voluble.titanMC.progression.service.ProgressionEngine;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Map;
import java.util.Objects;

public final class BlockBreakCredSource implements Listener {

	private final ProgressionEngine engine;
	private final CredSourceRegistry registry;
	private final CredSource sourceId;
	private final Map<Material, CredAmount> values;

	public BlockBreakCredSource(
		ProgressionEngine engine,
		CredSourceRegistry registry,
		CredSource sourceId,
		Map<Material, CredAmount> values
	) {
		this.engine = Objects.requireNonNull(engine, "engine");
		this.registry = Objects.requireNonNull(registry, "registry");
		this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
		this.values = Map.copyOf(Objects.requireNonNull(values, "values"));
	}

	public CredSource sourceId() {
		return sourceId;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		if (!registry.isEnabled(sourceId)) return;
		Player player = event.getPlayer();
		if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
		CredAmount value = values.get(event.getBlock().getType());
		if (value == null || value.isZero()) return;
		engine.give(player.getUniqueId(), value, sourceId);
	}
}
