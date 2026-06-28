package com.voluble.titanMC.onboarding.preview;

import com.voluble.titanMC.integrations.entitylib.EntityLibRuntime;
import com.voluble.titanMC.onboarding.preview.actor.PreviewActorController;
import com.voluble.titanMC.onboarding.preview.actor.PreviewMotion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityLibOutfitPreview implements OutfitPreview {
	private final JavaPlugin plugin;
	private final PreviewMotion motion;
	private final Map<UUID, PreviewActorController> controllers = new ConcurrentHashMap<>();

	public EntityLibOutfitPreview(JavaPlugin plugin) {
		this(plugin, PreviewMotion.defaults());
	}

	EntityLibOutfitPreview(JavaPlugin plugin, PreviewMotion motion) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.motion = Objects.requireNonNull(motion, "motion");
	}

	@Override
	public boolean available() {
		return EntityLibRuntime.available(plugin);
	}

	@Override
	public CompletionStage<Void> show(Player player, PreviewModel model) {
		return show(player, new PreviewScene(
			com.voluble.titanMC.onboarding.config.OnboardingPreviewMode.RUNWAY,
			model.stage(),
			model,
			model,
			model,
			0,
			1,
			0
		));
	}

	@Override
	public CompletionStage<Void> show(Player player, PreviewScene scene) {
		if (!available()) return CompletableFuture.failedFuture(new PreviewException("EntityLib outfit previews are unavailable"));
		CompletableFuture<Void> result = new CompletableFuture<>();
		Runnable task = () -> {
			try {
				EntityLibRuntime.initialize(plugin);
				controllers.computeIfAbsent(player.getUniqueId(), id -> new PreviewActorController(plugin, player, motion))
					.show(scene)
					.whenComplete((ignored, failure) -> {
						if (failure == null) {
							result.complete(null);
						} else {
							result.completeExceptionally(new PreviewException("Could not show EntityLib outfit preview", failure));
						}
					});
			} catch (Exception exception) {
				result.completeExceptionally(new PreviewException("Could not show EntityLib outfit preview", exception));
			}
		};
		if (Bukkit.isPrimaryThread()) {
			task.run();
		} else {
			Bukkit.getScheduler().runTask(plugin, task);
		}
		return result;
	}

	@Override
	public void remove(Player player) {
		Runnable task = () -> {
			PreviewActorController controller = controllers.remove(player.getUniqueId());
			if (controller != null) controller.remove();
		};
		if (Bukkit.isPrimaryThread()) {
			task.run();
		} else {
			Bukkit.getScheduler().runTask(plugin, task);
		}
	}
}
