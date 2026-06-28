package com.voluble.titanMC.onboarding.readiness;

import com.voluble.titanMC.onboarding.OnboardingOutfitSelection;
import com.voluble.titanMC.onboarding.config.OnboardingConfiguration;
import com.voluble.titanMC.onboarding.config.OnboardingPreviewMode;
import com.voluble.titanMC.onboarding.config.OnboardingWarmupConfiguration;
import com.voluble.titanMC.outfits.OutfitResult;
import com.voluble.titanMC.outfits.OutfitService;
import com.voluble.titanMC.outfits.PreparedOutfitSkin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class OnboardingOutfitWarmup {
	private final Plugin plugin;
	private final OutfitService outfits;

	public OnboardingOutfitWarmup(Plugin plugin, OutfitService outfits) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.outfits = Objects.requireNonNull(outfits, "outfits");
	}

	public CompletableFuture<OnboardingReadinessResult> prepare(Player player, OnboardingConfiguration configuration) {
		OnboardingWarmupConfiguration warmup = configuration.readiness().warmup();
		if (!warmup.enabled()) return CompletableFuture.completedFuture(OnboardingReadinessResult.READY);
		List<OnboardingOutfitSelection> selections = warmupSelections(configuration);
		if (selections.isEmpty()) return CompletableFuture.completedFuture(OnboardingReadinessResult.READY);
		CompletableFuture<OnboardingReadinessResult> future = new CompletableFuture<>();
		BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () ->
			future.complete(OnboardingReadinessResult.WARMUP_TIMEOUT), warmup.timeoutTicks()
		);
		prepareNext(player, selections, 0, future, timeout);
		return future.whenComplete((ignored, failure) -> timeout.cancel());
	}

	private void prepareNext(
		Player player,
		List<OnboardingOutfitSelection> selections,
		int index,
		CompletableFuture<OnboardingReadinessResult> future,
		BukkitTask timeout
	) {
		if (future.isDone()) return;
		if (!player.isOnline()) {
			future.complete(OnboardingReadinessResult.WARMUP_FAILED);
			return;
		}
		if (index >= selections.size()) {
			timeout.cancel();
			future.complete(OnboardingReadinessResult.READY);
			return;
		}
		OnboardingOutfitSelection selection = selections.get(index);
		java.util.function.Consumer<PreparedOutfitSkin> callback = prepared -> {
			if (future.isDone()) return;
			if (prepared.result() != OutfitResult.APPLIED || prepared.property() == null) {
				future.complete(OnboardingReadinessResult.WARMUP_FAILED);
				return;
			}
			Bukkit.getScheduler().runTask(plugin, () -> prepareNext(player, selections, index + 1, future, timeout));
		};
		if (selection.original()) {
			outfits.prepareOriginalSkin(player, callback);
		} else {
			outfits.prepareOutfitSkin(player, selection.outfitId(), callback);
		}
	}

	private List<OnboardingOutfitSelection> warmupSelections(OnboardingConfiguration configuration) {
		List<OnboardingOutfitSelection> configured = configuration.outfits();
		if (configured.isEmpty()) return List.of();
		Set<Integer> indices = new LinkedHashSet<>();
		indices.add(0);
		if (configuration.previewMode() == OnboardingPreviewMode.CAROUSEL && configured.size() > 1) {
			indices.add(configured.size() - 1);
			indices.add(1);
		}
		List<OnboardingOutfitSelection> selections = new ArrayList<>();
		for (int index : indices) selections.add(configured.get(index));
		return selections;
	}
}
