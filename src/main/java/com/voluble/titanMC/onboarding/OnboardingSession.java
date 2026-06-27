package com.voluble.titanMC.onboarding;

import com.voluble.titanMC.cinematics.runtime.CinematicRuntime;
import com.voluble.titanMC.cinematics.runtime.CinematicPlaybackOptions;
import com.voluble.titanMC.display.notice.MessageDefaults;
import com.voluble.titanMC.display.notice.PluginMessageService;
import com.voluble.titanMC.onboarding.config.OnboardingConfiguration;
import com.voluble.titanMC.onboarding.persistence.OnboardingStorage;
import com.voluble.titanMC.onboarding.preview.OutfitPreview;
import com.voluble.titanMC.outfits.OutfitResult;
import com.voluble.titanMC.outfits.OutfitService;
import com.voluble.titanMC.outfits.config.OutfitConfigurationManager;
import com.voluble.titanMC.outfits.model.OutfitDefinition;
import com.voluble.titanMC.outfits.model.OutfitId;
import org.bukkit.Input;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OnboardingSession {
	private final Player player;
	private final OnboardingConfiguration configuration;
	private final CinematicRuntime cinematics;
	private final OutfitService outfits;
	private final OutfitConfigurationManager outfitConfiguration;
	private final OutfitPreview preview;
	private final OnboardingStorage storage;
	private final PluginMessageService messages;
	private final Logger logger;
	private final Consumer<UUID> completion;
	private int outfitIndex;
	private long lastInputMillis;
	private boolean stopping;
	private boolean interactive;
	private int previewGeneration;

	public OnboardingSession(
		Player player,
		OnboardingConfiguration configuration,
		CinematicRuntime cinematics,
		OutfitService outfits,
		OutfitConfigurationManager outfitConfiguration,
		OutfitPreview preview,
		OnboardingStorage storage,
		PluginMessageService messages,
		Logger logger,
		Consumer<UUID> completion
	) {
		this.player = Objects.requireNonNull(player, "player");
		this.configuration = Objects.requireNonNull(configuration, "configuration");
		this.cinematics = Objects.requireNonNull(cinematics, "cinematics");
		this.outfits = Objects.requireNonNull(outfits, "outfits");
		this.outfitConfiguration = Objects.requireNonNull(outfitConfiguration, "outfitConfiguration");
		this.preview = Objects.requireNonNull(preview, "preview");
		this.storage = Objects.requireNonNull(storage, "storage");
		this.messages = Objects.requireNonNull(messages, "messages");
		this.logger = Objects.requireNonNull(logger, "logger");
		this.completion = Objects.requireNonNull(completion, "completion");
	}

	public UUID playerId() {
		return player.getUniqueId();
	}

	public void start() {
		CinematicRuntime.StartResult result = cinematics.start(
			player,
			configuration.cinematic(),
			CinematicPlaybackOptions.holdLastFrame().withHoldCallback(this::beginSelection)
		);
		if (result != CinematicRuntime.StartResult.STARTED) {
			messages.send(player, MessageDefaults.ONBOARDING_START_FAILED);
			stop(false);
			return;
		}
	}

	public void handle(Input input) {
		if (stopping || !interactive || input == null) return;
		long now = System.currentTimeMillis();
		if (now - lastInputMillis < configuration.inputCooldownMillis()) return;
		if (input.isRight() && !input.isLeft()) {
			lastInputMillis = now;
			nextOutfit();
		} else if (input.isLeft() && !input.isRight()) {
			lastInputMillis = now;
			previousOutfit();
		} else if (input.isJump()) {
			lastInputMillis = now;
			confirm();
		} else if (input.isSneak()) {
			lastInputMillis = now;
			cancel();
		}
	}

	public void stop(boolean restorePlayer) {
		if (stopping) return;
		stopping = true;
		preview.remove(player);
		cinematics.stop(player.getUniqueId(), restorePlayer);
		completion.accept(player.getUniqueId());
	}

	private void nextOutfit() {
		outfitIndex = (outfitIndex + 1) % configuration.outfits().size();
		showSelectedOutfit();
	}

	private void beginSelection() {
		if (stopping || !player.isOnline()) return;
		interactive = true;
		messages.send(player, MessageDefaults.ONBOARDING_STARTED);
		showSelectedOutfit();
	}

	private void previousOutfit() {
		outfitIndex = (outfitIndex - 1 + configuration.outfits().size()) % configuration.outfits().size();
		showSelectedOutfit();
	}

	private void showSelectedOutfit() {
		OutfitId outfit = selectedOutfit();
		String name = outfitName(outfit);
		messages.send(player, MessageDefaults.ONBOARDING_OUTFIT_SELECTED, args -> args.plain("outfit", name));
		if (!preview.available()) {
			messages.send(player, MessageDefaults.ONBOARDING_PREVIEW_UNAVAILABLE);
			return;
		}
		int generation = ++previewGeneration;
		outfits.prepareOutfitSkin(player, outfit, prepared -> {
			if (stopping || generation != previewGeneration) return;
			if (prepared.result() != OutfitResult.APPLIED || prepared.property() == null) {
				messages.send(player, MessageDefaults.ONBOARDING_PREVIEW_FAILED);
				return;
			}
			preview.show(player, new OutfitPreview.PreviewModel(
					name,
					configuration.previewStage(),
					prepared.property()
				))
				.whenComplete((ignored, failure) -> {
					if (stopping || generation != previewGeneration) {
						if (failure == null) preview.remove(player);
						return;
					}
					if (failure == null) return;
					logger.log(Level.WARNING, "Failed to show onboarding preview for " + player.getUniqueId(), failure);
					messages.send(player, MessageDefaults.ONBOARDING_PREVIEW_FAILED);
				});
		});
	}

	private void confirm() {
		OutfitId outfit = selectedOutfit();
		messages.send(player, MessageDefaults.ONBOARDING_APPLYING, args -> args.plain("outfit", outfitName(outfit)));
		outfits.applyOutfit(player, outfit, result -> {
			if (result != OutfitResult.APPLIED) {
				messages.send(player, MessageDefaults.ONBOARDING_APPLY_FAILED);
				return;
			}
			try {
				storage.complete(player.getUniqueId(), outfit, System.currentTimeMillis());
			} catch (SQLException exception) {
				logger.log(Level.WARNING, "Failed to save onboarding completion for " + player.getUniqueId(), exception);
				messages.send(player, MessageDefaults.ONBOARDING_APPLY_FAILED);
				return;
			}
			messages.send(player, MessageDefaults.ONBOARDING_COMPLETED);
			stop(true);
		});
	}

	private void cancel() {
		messages.send(player, MessageDefaults.ONBOARDING_CANCELLED);
		stop(true);
	}

	private OutfitId selectedOutfit() {
		List<OutfitId> configured = configuration.outfits();
		return configured.get(Math.max(0, Math.min(outfitIndex, configured.size() - 1)));
	}

	private String outfitName(OutfitId outfit) {
		return outfitConfiguration.current().find(outfit)
			.map(OutfitDefinition::displayName)
			.orElse(outfit.value());
	}
}
