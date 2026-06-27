package com.voluble.titanMC.onboarding.preview;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.skins.SkinData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class FancyNpcOutfitPreview implements OutfitPreview {
	private final Plugin plugin;
	private final Map<UUID, Npc> previews = new ConcurrentHashMap<>();

	public FancyNpcOutfitPreview(Plugin plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
	}

	@Override
	public boolean available() {
		return Bukkit.getPluginManager().isPluginEnabled("FancyNpcs");
	}

	@Override
	public CompletionStage<Void> show(Player player, PreviewModel model) {
		if (!available()) return CompletableFuture.failedFuture(new PreviewException("FancyNPCs is not installed or enabled"));
		CompletableFuture<Void> result = new CompletableFuture<>();
		Location focus;
		try {
			focus = model.stage().focus().toLocation();
		} catch (Exception exception) {
			return CompletableFuture.failedFuture(new PreviewException("Could not resolve onboarding preview focus location", exception));
		}
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				removeNow(player);
				String name = "titan_onboarding_" + player.getUniqueId().toString().substring(0, 8);
				NpcData data = new NpcData(name, player.getUniqueId(), focus)
					.setDisplayName(model.name())
					.setShowInTab(false)
					.setCollidable(false)
					.setTurnToPlayer(false)
					.setSkinData(new SkinData(
						"titanmc_" + name,
						SkinData.SkinVariant.AUTO,
						model.skin().value(),
						model.skin().signature()
					));
				Npc npc = FancyNpcsPlugin.get().getNpcAdapter().apply(data);
				npc.setSaveToFile(false);
				npc.create();
				npc.spawn(player);
				previews.put(player.getUniqueId(), npc);
				complete(result, null);
			} catch (Exception exception) {
				complete(result, new PreviewException("Could not show FancyNPCs outfit preview", exception));
			}
		});
		return result;
	}

	@Override
	public void remove(Player player) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> removeNow(player));
	}

	private void removeNow(Player player) {
		Npc npc = previews.remove(player.getUniqueId());
		if (npc == null) return;
		try {
			npc.remove(player);
		} catch (Exception ignored) {
			// Preview cleanup must not break onboarding teardown.
		}
	}

	private void complete(CompletableFuture<Void> result, PreviewException failure) {
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (failure == null) {
				result.complete(null);
			} else {
				result.completeExceptionally(failure);
			}
		});
	}
}
