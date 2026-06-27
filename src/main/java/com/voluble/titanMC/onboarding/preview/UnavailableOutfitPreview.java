package com.voluble.titanMC.onboarding.preview;

import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class UnavailableOutfitPreview implements OutfitPreview {
	@Override
	public boolean available() {
		return false;
	}

	@Override
	public CompletionStage<Void> show(Player player, PreviewModel model) {
		return CompletableFuture.failedFuture(new PreviewException("FancyNPCs is not installed or enabled"));
	}

	@Override
	public void remove(Player player) {
	}
}
