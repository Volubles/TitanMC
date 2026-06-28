package com.voluble.titanMC.onboarding.preview;

import com.voluble.titanMC.outfits.skin.SkinPropertyData;
import com.voluble.titanMC.onboarding.config.OnboardingPreviewMode;
import com.voluble.titanMC.onboarding.config.OnboardingPreviewStage;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public interface OutfitPreview {
	boolean available();

	CompletionStage<Void> show(Player player, PreviewModel model);

	default CompletionStage<Void> show(Player player, PreviewScene scene) {
		return show(player, scene.focus());
	}

	void remove(Player player);

	record PreviewModel(String name, OnboardingPreviewStage stage, SkinPropertyData skin) {
		public PreviewModel {
			java.util.Objects.requireNonNull(name, "name");
			java.util.Objects.requireNonNull(stage, "stage");
			java.util.Objects.requireNonNull(skin, "skin");
		}
	}

	record PreviewScene(
		OnboardingPreviewMode mode,
		OnboardingPreviewStage stage,
		PreviewModel previous,
		PreviewModel focus,
		PreviewModel next,
		int selectedIndex,
		int selectionSize,
		int rotationDirection
	) {
		public PreviewScene {
			Objects.requireNonNull(mode, "mode");
			Objects.requireNonNull(stage, "stage");
			Objects.requireNonNull(previous, "previous");
			Objects.requireNonNull(focus, "focus");
			Objects.requireNonNull(next, "next");
			if (selectionSize <= 0) throw new IllegalArgumentException("selection size must be positive");
			if (selectedIndex < 0 || selectedIndex >= selectionSize) throw new IllegalArgumentException("selected index out of bounds");
			rotationDirection = Integer.compare(rotationDirection, 0);
		}
	}
}
