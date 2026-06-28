package com.voluble.titanMC.onboarding.preview.actor;

import com.voluble.titanMC.onboarding.config.OnboardingPreviewMode;
import com.voluble.titanMC.onboarding.preview.OutfitPreview;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class PreviewActorController {
	private final Map<OnboardingPreviewMode, PreviewStrategy> strategies = new EnumMap<>(OnboardingPreviewMode.class);

	public PreviewActorController(Plugin plugin, Player player, PreviewMotion motion) {
		PreviewActorFactory actors = new PreviewActorFactory(
			Objects.requireNonNull(plugin, "plugin"),
			Objects.requireNonNull(player, "player"),
			Objects.requireNonNull(motion, "motion")
		);
		strategies.put(OnboardingPreviewMode.RUNWAY, new RunwayPreviewStrategy(actors));
		strategies.put(OnboardingPreviewMode.CAROUSEL, new CarouselPreviewStrategy(actors));
	}

	public CompletableFuture<Void> show(OutfitPreview.PreviewModel model) {
		Objects.requireNonNull(model, "model");
		OutfitPreview.PreviewScene scene = new OutfitPreview.PreviewScene(
			OnboardingPreviewMode.RUNWAY,
			model.stage(),
			model,
			model,
			model,
			0,
			1,
			0
		);
		return show(scene);
	}

	public CompletableFuture<Void> show(OutfitPreview.PreviewScene scene) {
		Objects.requireNonNull(scene, "scene");
		return strategy(scene.mode()).show(scene);
	}

	public void remove() {
		strategies.values().forEach(PreviewStrategy::remove);
	}

	private PreviewStrategy strategy(OnboardingPreviewMode mode) {
		PreviewStrategy strategy = strategies.get(mode);
		if (strategy == null) throw new IllegalArgumentException("Unsupported preview mode: " + mode);
		return strategy;
	}
}
