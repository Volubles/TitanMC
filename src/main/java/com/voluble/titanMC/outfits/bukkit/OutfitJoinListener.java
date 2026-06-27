package com.voluble.titanMC.outfits.bukkit;

import com.voluble.titanMC.display.notice.MessageDefaults;
import com.voluble.titanMC.display.notice.PluginMessageService;
import com.voluble.titanMC.outfits.OutfitService;
import com.voluble.titanMC.outfits.config.OutfitConfigurationManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public final class OutfitJoinListener implements Listener {
	private final Plugin plugin;
	private final OutfitConfigurationManager configuration;
	private final OutfitService outfits;
	private final PluginMessageService messages;

	public OutfitJoinListener(
		Plugin plugin,
		OutfitConfigurationManager configuration,
		OutfitService outfits,
		PluginMessageService messages
	) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.configuration = Objects.requireNonNull(configuration, "configuration");
		this.outfits = Objects.requireNonNull(outfits, "outfits");
		this.messages = Objects.requireNonNull(messages, "messages");
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		var config = configuration.current();
		if (!config.enabled() || !config.firstJoinPrompt()) return;
		if (outfits.preference(event.getPlayer().getUniqueId()).isPresent()) return;
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (!event.getPlayer().isOnline()) return;
			messages.send(event.getPlayer(), MessageDefaults.OUTFITS_FIRST_JOIN_PROMPT);
		}, config.firstJoinPromptDelayTicks());
	}
}
