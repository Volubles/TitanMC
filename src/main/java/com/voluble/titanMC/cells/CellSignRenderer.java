package com.voluble.titanMC.cells;

import com.voluble.titanMC.cells.config.CellsConfigurationManager;
import com.voluble.titanMC.cells.model.CellDefinition;
import com.voluble.titanMC.cells.model.CellLease;
import com.voluble.titanMC.cells.model.CellSign;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.List;

public final class CellSignRenderer {
	private final Plugin plugin;
	private final CellManager cells;
	private final CellsConfigurationManager configuration;
	private BukkitTask refreshTask;

	public CellSignRenderer(Plugin plugin, CellManager cells, CellsConfigurationManager configuration) {
		this.plugin = plugin;
		this.cells = cells;
		this.configuration = configuration;
	}

	private static String duration(long millis) {
		Duration value = Duration.ofMillis(millis);
		long days = value.toDays();
		if (days > 0) return days + "d " + value.minusDays(days).toHours() + "h";
		long hours = value.toHours();
		if (hours > 0) return hours + "h " + value.minusHours(hours).toMinutes() + "m";
		return Math.max(0L, value.toMinutes()) + "m";
	}

	public void start() {
		if (refreshTask != null) return;
		long period = configuration.current().signRefreshTicks();
		refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshLoaded, period, period);
	}

	public void stop() {
		if (refreshTask != null) {
			refreshTask.cancel();
			refreshTask = null;
		}
	}

	public void refresh(CellDefinition cell) {
		for (CellSign sign : cells.signs()) if (sign.cellId().equals(cell.id())) renderIfLoaded(sign);
	}

	public void refreshLoaded() {
		for (CellSign sign : cells.signs()) renderIfLoaded(sign);
	}

	public void render(Sign sign, CellDefinition cell) {
		CellLease lease = cells.lease(cell.id());
		List<String> template = cells.resetJobs().stream().anyMatch(job -> job.cellId().equals(cell.id()))
				? configuration.current().resettingSign()
				: lease == null ? configuration.current().availableSign() : configuration.current().rentedSign();
		for (int line = 0; line < 4; line++)
			sign.getSide(Side.FRONT).line(line, render(template.get(line), cell, lease));
		sign.update(true, false);
	}

	private void renderIfLoaded(CellSign binding) {
		World world = Bukkit.getWorld(binding.worldId());
		CellDefinition cell = cells.get(binding.cellId());
		if (world == null || cell == null || !world.isChunkLoaded(binding.x() >> 4, binding.z() >> 4)) return;
		if (world.getBlockAt(binding.x(), binding.y(), binding.z()).getState() instanceof Sign sign) render(sign, cell);
		else cells.unregisterSign(binding);
	}

	private Component render(String template, CellDefinition cell, CellLease lease) {
		String owner = lease == null ? "" : Bukkit.getOfflinePlayer(lease.ownerId()).getName();
		if (owner == null && lease != null) owner = lease.ownerId().toString();
		String parsed = template;
		for (String key : List.of("id", "display_name", "ward", "price", "duration", "owner", "time_left", "member_count")) {
			parsed = parsed.replace("{" + key + "}", "<" + key + ">");
		}
		return MiniMessage.miniMessage().deserialize(parsed,
				Placeholder.unparsed("id", cell.id()), Placeholder.unparsed("display_name", cell.displayName()),
				Placeholder.unparsed("ward", cell.wardId().value()),
				Placeholder.unparsed("price", Long.toString(cell.rentPrice())),
				Placeholder.unparsed("duration", duration(cell.rentDurationSeconds() * 1000L)),
				Placeholder.unparsed("owner", owner),
				Placeholder.unparsed("time_left", lease == null ? "" : duration(Math.max(0L, lease.expiresAtEpochMillis() - System.currentTimeMillis()))),
				Placeholder.unparsed("member_count", Integer.toString(cells.members(cell.id()).size()))
		);
	}
}
