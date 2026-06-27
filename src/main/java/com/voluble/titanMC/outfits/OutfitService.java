package com.voluble.titanMC.outfits;

import com.voluble.titanMC.outfits.config.OutfitConfigurationManager;
import com.voluble.titanMC.outfits.model.OutfitDefinition;
import com.voluble.titanMC.outfits.model.OutfitId;
import com.voluble.titanMC.outfits.model.OutfitPreference;
import com.voluble.titanMC.outfits.persistence.GeneratedOutfitSkin;
import com.voluble.titanMC.outfits.persistence.OutfitStorage;
import com.voluble.titanMC.outfits.skin.MineSkinClient;
import com.voluble.titanMC.outfits.skin.OutfitSkinComposer;
import com.voluble.titanMC.outfits.skin.PlayerSkinSource;
import com.voluble.titanMC.outfits.skin.SkinApplier;
import com.voluble.titanMC.outfits.skin.SkinHash;
import com.voluble.titanMC.outfits.skin.SkinPropertyData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.net.URL;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OutfitService implements AutoCloseable {
	private final Plugin plugin;
	private final OutfitConfigurationManager configuration;
	private final OutfitStorage storage;
	private final SkinApplier skinApplier;
	private final PlayerSkinSource skinSource;
	private final OutfitSkinComposer composer;
	private final MineSkinClient mineSkin;
	private final Logger logger;
	private final Set<UUID> active = Collections.newSetFromMap(new ConcurrentHashMap<>());

	public OutfitService(
		Plugin plugin,
		OutfitConfigurationManager configuration,
		OutfitStorage storage,
		SkinApplier skinApplier,
		PlayerSkinSource skinSource,
		OutfitSkinComposer composer,
		MineSkinClient mineSkin,
		Logger logger
	) {
		this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
		this.configuration = java.util.Objects.requireNonNull(configuration, "configuration");
		this.storage = java.util.Objects.requireNonNull(storage, "storage");
		this.skinApplier = java.util.Objects.requireNonNull(skinApplier, "skinApplier");
		this.skinSource = java.util.Objects.requireNonNull(skinSource, "skinSource");
		this.composer = java.util.Objects.requireNonNull(composer, "composer");
		this.mineSkin = java.util.Objects.requireNonNull(mineSkin, "mineSkin");
		this.logger = java.util.Objects.requireNonNull(logger, "logger");
	}

	public void applyOutfit(Player player, OutfitId outfitId, Consumer<OutfitResult> callback) {
		if (!configuration.current().enabled()) {
			callback.accept(OutfitResult.DISABLED);
			return;
		}
		OutfitDefinition outfit = configuration.current().find(outfitId).orElse(null);
		if (outfit == null) {
			callback.accept(OutfitResult.UNKNOWN_OUTFIT);
			return;
		}
		String apiKey = configuration.current().mineSkinApiKey().orElse(null);
		if (apiKey == null) {
			callback.accept(OutfitResult.NO_MINESKIN_KEY);
			return;
		}
		if (!skinApplier.available()) {
			callback.accept(OutfitResult.SKINS_RESTORER_UNAVAILABLE);
			return;
		}
		if (!active.add(player.getUniqueId())) {
			callback.accept(OutfitResult.BUSY);
			return;
		}
		UUID playerId = player.getUniqueId();
		URL originalSkin = skinSource.skinUrl(player).orElse(null);
		if (originalSkin == null) {
			active.remove(playerId);
			callback.accept(OutfitResult.NO_ORIGINAL_SKIN);
			return;
		}
		CompletableFuture.supplyAsync(() -> prepare(playerId, apiKey, originalSkin, outfit))
			.whenComplete((property, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
				try {
					if (failure != null || property == null) {
						logger.log(Level.WARNING, "Failed to prepare outfit " + outfit.id() + " for " + playerId, failure);
						callback.accept(OutfitResult.FAILED);
						return;
					}
					Player online = Bukkit.getPlayer(playerId);
					if (online == null) {
						callback.accept(OutfitResult.FAILED);
						return;
					}
					skinApplier.apply(online, property);
					storage.savePreference(playerId, OutfitPreference.outfit(outfit.id()), System.currentTimeMillis());
					callback.accept(OutfitResult.APPLIED);
				} catch (Exception exception) {
					logger.log(Level.WARNING, "Failed to apply outfit " + outfit.id() + " for " + playerId, exception);
					callback.accept(OutfitResult.FAILED);
				} finally {
					active.remove(playerId);
				}
			}));
	}

	public void applyOriginal(Player player, Consumer<OutfitResult> callback) {
		if (!skinApplier.available()) {
			callback.accept(OutfitResult.SKINS_RESTORER_UNAVAILABLE);
			return;
		}
		try {
			skinApplier.applyOriginal(player);
			storage.savePreference(player.getUniqueId(), OutfitPreference.original(), System.currentTimeMillis());
			callback.accept(OutfitResult.ORIGINAL);
		} catch (Exception exception) {
			logger.log(Level.WARNING, "Failed to restore original skin for " + player.getUniqueId(), exception);
			callback.accept(OutfitResult.FAILED);
		}
	}

	public java.util.Optional<OutfitPreference> preference(UUID playerId) {
		try {
			return storage.preference(playerId);
		} catch (SQLException exception) {
			logger.log(Level.WARNING, "Failed to load outfit preference for " + playerId, exception);
			return java.util.Optional.empty();
		}
	}

	private SkinPropertyData prepare(UUID playerId, String apiKey, URL originalSkin, OutfitDefinition outfit) {
		try {
			String originalHash = SkinHash.sha256(originalSkin.toString());
			GeneratedOutfitSkin cached = storage.generatedSkin(playerId, outfit.id(), outfit.model(), originalHash).orElse(null);
			if (cached != null) return cached.property();
			byte[] png = composer.compose(originalSkin, outfit);
			SkinPropertyData generated = mineSkin.upload(apiKey, outfit.id(), outfit.model(), png);
			storage.saveGeneratedSkin(playerId, new GeneratedOutfitSkin(
				outfit.id(), outfit.model(), originalHash, generated, System.currentTimeMillis()
			));
			return generated;
		} catch (Exception exception) {
			throw new IllegalStateException("Could not generate outfit skin", exception);
		}
	}

	@Override
	public void close() throws SQLException {
		storage.close();
	}
}
