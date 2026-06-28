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
		String playerName = player.getName();
		CompletableFuture.supplyAsync(() -> prepare(playerId, playerName, apiKey, outfit))
			.whenComplete((prepared, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
				try {
					if (failure != null || prepared == null) {
						logger.log(Level.WARNING, "Failed to prepare outfit " + outfit.id() + " for " + playerId, failure);
						callback.accept(PreparedOutfitSkin.failed(OutfitResult.FAILED));
					} else {
						callback.accept(prepared);
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
			UUID playerId = player.getUniqueId();
			String playerName = player.getName();
			skinApplier.clearOriginalAssignment(player);
			CompletableFuture.supplyAsync(() -> {
				try {
					return skinApplier.resolveOriginal(playerName);
				} catch (Exception exception) {
					throw new IllegalStateException(exception);
				}
			})
				.whenComplete((original, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
					try {
						Player online = Bukkit.getPlayer(playerId);
						if (online == null) {
							callback.accept(OutfitResult.FAILED);
							return;
						}
						if (failure != null) {
							logger.log(Level.WARNING, "Failed to resolve original skin for " + playerId, failure);
							skinApplier.applyOriginal(online);
						} else if (original.isPresent()) {
							skinApplier.apply(online, original.get());
						} else {
							skinApplier.applyOriginal(online);
						}
						storage.savePreference(playerId, OutfitPreference.original(), System.currentTimeMillis());
						callback.accept(OutfitResult.ORIGINAL);
					} catch (Exception exception) {
						logger.log(Level.WARNING, "Failed to restore original skin for " + playerId, exception);
						callback.accept(OutfitResult.FAILED);
					}
				}));
		} catch (Exception exception) {
			logger.log(Level.WARNING, "Failed to restore original skin for " + player.getUniqueId(), exception);
			callback.accept(OutfitResult.FAILED);
		}
	}

	public void prepareOriginalSkin(Player player, Consumer<PreparedOutfitSkin> callback) {
		if (!skinApplier.available()) {
			callback.accept(PreparedOutfitSkin.failed(OutfitResult.SKINS_RESTORER_UNAVAILABLE));
			return;
		}
		UUID playerId = player.getUniqueId();
		String playerName = player.getName();
		CompletableFuture.supplyAsync(() -> {
			try {
				return skinApplier.resolveOriginal(playerName);
			} catch (Exception exception) {
				throw new IllegalStateException(exception);
			}
		}).whenComplete((original, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
			if (failure != null) {
				logger.log(Level.WARNING, "Failed to prepare original skin preview for " + playerId, failure);
				callback.accept(PreparedOutfitSkin.failed(OutfitResult.NO_ORIGINAL_SKIN));
				return;
			}
			if (original.isEmpty()) {
				callback.accept(PreparedOutfitSkin.failed(OutfitResult.NO_ORIGINAL_SKIN));
				return;
			}
			callback.accept(PreparedOutfitSkin.success(original.get()));
		}));
	}

	public java.util.Optional<OutfitPreference> preference(UUID playerId) {
		try {
			return storage.preference(playerId);
		} catch (SQLException exception) {
			logger.log(Level.WARNING, "Failed to load outfit preference for " + playerId, exception);
			return java.util.Optional.empty();
		}
	}

	public void restorePreference(Player player, OutfitPreference preference) {
		preference.outfitId().ifPresent(outfitId -> applyOutfit(player, outfitId, result -> {
			if (result == OutfitResult.APPLIED) return;
			logger.warning("Failed to restore outfit " + outfitId + " for " + player.getUniqueId() + ": " + result);
		}));
	}

	private PreparedOutfitSkin prepare(UUID playerId, String playerName, String apiKey, OutfitDefinition outfit) {
		try {
			PlayerSkin originalSkin = originalSkin(outfit, playerName);
			if (originalSkin == null && outfit.renderMode() == OutfitRenderMode.COMPOSITE) {
				return PreparedOutfitSkin.failed(OutfitResult.NO_ORIGINAL_SKIN);
			}
			SkinModel model = skinModel(outfit, originalSkin);
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
			if (cached != null) return PreparedOutfitSkin.success(cached.property());
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
			return PreparedOutfitSkin.success(generated);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not generate outfit skin", exception);
		}
	}

	private PlayerSkin originalSkin(OutfitDefinition outfit, String playerName) throws Exception {
		if (outfit.renderMode() == OutfitRenderMode.FULL_SKIN) return null;
		return skinApplier.resolveOriginal(playerName)
			.flatMap(skinSource::skin)
			.orElse(null);
	}

	private SkinModel skinModel(OutfitDefinition outfit, PlayerSkin originalSkin) {
		return outfit.renderMode() == OutfitRenderMode.FULL_SKIN ? outfit.skinModel() : originalSkin.model();
	}

	@Override
	public void close() throws SQLException {
		storage.close();
	}
}
