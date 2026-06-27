package com.voluble.titanMC.onboarding.preview;

import com.voluble.titanMC.outfits.skin.SkinPropertyData;
import com.voluble.titanMC.onboarding.config.OnboardingPreviewStage;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletionStage;

public interface OutfitPreview {
	boolean available();

	CompletionStage<Void> show(Player player, PreviewModel model);

	void remove(Player player);

	record PreviewModel(String name, OnboardingPreviewStage stage, SkinPropertyData skin) {
		public PreviewModel {
			java.util.Objects.requireNonNull(name, "name");
			java.util.Objects.requireNonNull(stage, "stage");
			java.util.Objects.requireNonNull(skin, "skin");
		}
	}
}
