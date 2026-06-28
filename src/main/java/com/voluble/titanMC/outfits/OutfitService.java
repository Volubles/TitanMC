package com.voluble.titanMC.outfits;

import com.voluble.titanMC.outfits.config.OutfitConfigurationManager;
import com.voluble.titanMC.outfits.model.OutfitDefinition;
import com.voluble.titanMC.outfits.model.OutfitId;
import com.voluble.titanMC.outfits.model.OutfitPreference;
import com.voluble.titanMC.outfits.model.OutfitRenderMode;
import com.voluble.titanMC.outfits.model.SkinModel;
import com.voluble.titanMC.outfits.persistence.GeneratedOutfitSkin;
import com.voluble.titanMC.outfits.persistence.OutfitStorage;
import com.voluble.titanMC.outfits.skin.MineSkinClient;
import com.voluble.titanMC.outfits.skin.OutfitSkinComposer;
import com.voluble.titanMC.outfits.skin.PlayerSkin;
import com.voluble.titanMC.outfits.skin.PlayerSkinSource;
import com.voluble.titanMC.outfits.skin.SkinApplier;
import com.voluble.titanMC.outfits.skin.SkinHash;
import com.voluble.titanMC.outfits.skin.SkinPropertyData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
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
	private static final UUID SHARED_FULL_SKIN_CACHE_ID = new UUID(0L, 0L);

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
		if (!skinApplier.available()) {
			callback.accept(OutfitResult.SKINS_RESTORER_UNAVAILABLE);
			return;
		}
		prepareOutfitSkin(player, outfitId, prepared -> {
			if (prepared.result() != OutfitResult.APPLIED || prepared.property() == null) {
				callback.accept(prepared.result());
				return;
			}
			try {
				Player online = Bukkit.getPlayer(player.getUniqueId());
				if (online == null) {
					callback.accept(OutfitResult.FAILED);
					return;
				}
				OutfitDefinition outfit = configuration.current().find(outfitId).orElse(null);
				if (outfit == null) {
					callback.accept(OutfitResult.UNKNOWN_OUTFIT);
					return;
				}
				skinApplier.apply(online, prepared.property());
				storage.savePreference(player.getUniqueId(), OutfitPreference.outfit(outfit.id()), System.currentTimeMillis());
				callback.accept(OutfitResult.APPLIED);
			} catch (Exception exception) {
				logger.log(Level.WARNING, "Failed to apply outfit " + outfitId + " for " + player.getUniqueId(), exception);
				callback.accept(OutfitResult.FAILED);
			}
		});
	}

	public void prepareOutfitSkin(Player player, OutfitId outfitId, Consumer<PreparedOutfitSkin> callback) {
		if (!configuration.current().enabled()) {
			callback.accept(PreparedOutfitSkin.failed(OutfitResult.DISABLED));
			return;
		}
		OutfitDefinition outfit = configuration.current().find(outfitId).orElse(null);
		if (outfit == null) {
			callback.accept(PreparedOutfitSkin.failed(OutfitResult.UNKNOWN_OUTFIT));
			return;
		}
		String apiKey = configuration.current().mineSkinApiKey().orElse(null);
		if (apiKey == null) {
			callback.accept(PreparedOutfitSkin.failed(OutfitResult.NO_MINESKIN_KEY));
			return;
		}
		if (!active.add(player.getUniqueId())) {
			callback.accept(PreparedOutfitSkin.failed(OutfitResult.BUSY));
			return;
		}
		UUID playerId = player.getUniqueId();
		PlayerSkin originalSkin = skinSource.skin(player).orElse(null);
		if (originalSkin == null && outfit.renderMode() == OutfitRenderMode.COMPOSITE) {
			active.remove(playerId);
			callback.accept(PreparedOutfitSkin.failed(OutfitResult.NO_ORIGINAL_SKIN));
			return;
		}
		CompletableFuture.supplyAsync(() -> prepare(playerId, apiKey, originalSkin, outfit))
			.whenComplete((property, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
				try {
					if (failure != null || property == null) {
						logger.log(Level.WARNING, "Failed to prepare outfit " + outfit.id() + " for " + playerId, failure);
						callback.accept(PreparedOutfitSkin.failed(OutfitResult.FAILED));
					} else {
						callback.accept(PreparedOutfitSkin.success(property));
					}
				} catch (Exception exception) {
					logger.log(Level.WARNING, "Failed to prepare outfit " + outfit.id() + " for " + playerId, exception);
					callback.accept(PreparedOutfitSkin.failed(OutfitResult.FAILED));
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

	private SkinPropertyData prepare(UUID playerId, String apiKey, PlayerSkin originalSkin, OutfitDefinition outfit) {
		try {
			SkinModel model = originalSkin == null ? SkinModel.CLASSIC : originalSkin.model();
			Path template = outfit.templatePath(model);
			String originalHash = originalSkin == null ? "full-skin" : SkinHash.sha256(originalSkin.url().toString());
			String templateHash = SkinHash.sha256(template);
			UUID cacheOwner = outfit.renderMode() == OutfitRenderMode.FULL_SKIN ? SHARED_FULL_SKIN_CACHE_ID : playerId;
			GeneratedOutfitSkin cached = storage.generatedSkin(
				cacheOwner,
				outfit.id(),
				outfit.renderMode(),
				model,
				originalHash,
				templateHash
			).orElse(null);
			if (cached != null) return cached.property();
			byte[] png = outfit.renderMode() == OutfitRenderMode.FULL_SKIN
				? composer.fullSkin(template)
				: composer.compose(originalSkin, template);
			SkinPropertyData generated = mineSkin.upload(
				apiKey,
				outfit.id(),
				model,
				configuration.current().mineSkinVisibility(),
				png
			);
			storage.saveGeneratedSkin(cacheOwner, new GeneratedOutfitSkin(
				outfit.id(),
				outfit.renderMode(),
				model,
				originalHash,
				templateHash,
				generated,
				System.currentTimeMillis()
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
