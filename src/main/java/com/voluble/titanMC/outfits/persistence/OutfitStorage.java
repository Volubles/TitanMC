package com.voluble.titanMC.outfits.persistence;

import com.voluble.titanMC.outfits.model.OutfitId;
import com.voluble.titanMC.outfits.model.OutfitMode;
import com.voluble.titanMC.outfits.model.OutfitPreference;
import com.voluble.titanMC.outfits.model.OutfitRenderMode;
import com.voluble.titanMC.outfits.model.SkinModel;
import com.voluble.titanMC.outfits.skin.SkinPropertyData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class OutfitStorage implements AutoCloseable {
	private static final int SCHEMA_VERSION = 2;

	private final Connection connection;

	public OutfitStorage(Path databasePath) throws SQLException {
		Objects.requireNonNull(databasePath, "databasePath");
		try {
			Path parent = databasePath.toAbsolutePath().getParent();
			if (parent != null) Files.createDirectories(parent);
			Class.forName("org.sqlite.JDBC");
		} catch (Exception exception) {
			throw new SQLException("Failed to prepare Outfits database", exception);
		}
		connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
		configure();
		initializeSchema();
	}

	private void configure() throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys = ON");
			statement.execute("PRAGMA journal_mode = WAL");
			statement.execute("PRAGMA synchronous = FULL");
			statement.execute("PRAGMA busy_timeout = 5000");
		}
	}

	private void initializeSchema() throws SQLException {
		try (Statement statement = connection.createStatement()) {
			int version;
			try (ResultSet result = statement.executeQuery("PRAGMA user_version")) {
				version = result.next() ? result.getInt(1) : 0;
				if (version > SCHEMA_VERSION) throw new SQLException("Unsupported Outfits database schema " + version);
			}
			if (version > 0 && version < 2) {
				statement.executeUpdate("DROP TABLE IF EXISTS generated_outfit_skins");
			}
			statement.executeUpdate("""
				CREATE TABLE IF NOT EXISTS outfit_preferences (
				    player_uuid TEXT PRIMARY KEY,
				    mode TEXT NOT NULL,
				    outfit_id TEXT,
				    updated_at INTEGER NOT NULL
				)
				""");
			statement.executeUpdate("""
				CREATE TABLE IF NOT EXISTS generated_outfit_skins (
				    player_uuid TEXT NOT NULL,
				    outfit_id TEXT NOT NULL,
				    render_mode TEXT NOT NULL,
				    model TEXT NOT NULL,
				    original_skin_hash TEXT NOT NULL,
				    template_hash TEXT NOT NULL,
				    texture_value TEXT NOT NULL,
				    texture_signature TEXT NOT NULL,
				    generated_at INTEGER NOT NULL,
				    PRIMARY KEY(player_uuid, outfit_id, render_mode, model, original_skin_hash, template_hash)
				)
				""");
			statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
		}
	}

	public synchronized Optional<OutfitPreference> preference(UUID playerId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			SELECT mode, outfit_id FROM outfit_preferences WHERE player_uuid = ?
			""")) {
			statement.setString(1, playerId.toString());
			try (ResultSet result = statement.executeQuery()) {
				if (!result.next()) return Optional.empty();
				OutfitMode mode = OutfitMode.valueOf(result.getString("mode"));
				String outfitId = result.getString("outfit_id");
				return Optional.of(mode == OutfitMode.ORIGINAL
					? OutfitPreference.original()
					: OutfitPreference.outfit(OutfitId.of(outfitId)));
			}
		}
	}

	public synchronized void savePreference(UUID playerId, OutfitPreference preference, long updatedAt) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			INSERT INTO outfit_preferences(player_uuid, mode, outfit_id, updated_at)
			VALUES(?,?,?,?) ON CONFLICT(player_uuid) DO UPDATE SET
			mode = excluded.mode,
			outfit_id = excluded.outfit_id,
			updated_at = excluded.updated_at
			""")) {
			statement.setString(1, playerId.toString());
			statement.setString(2, preference.mode().name());
			statement.setString(3, preference.outfitId().map(OutfitId::value).orElse(null));
			statement.setLong(4, updatedAt);
			statement.executeUpdate();
		}
	}

	public synchronized Optional<GeneratedOutfitSkin> generatedSkin(
		UUID playerId,
		OutfitId outfitId,
		OutfitRenderMode renderMode,
		SkinModel model,
		String originalSkinHash,
		String templateHash
	) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			SELECT texture_value, texture_signature, generated_at FROM generated_outfit_skins
			WHERE player_uuid = ? AND outfit_id = ? AND render_mode = ? AND model = ? AND original_skin_hash = ? AND template_hash = ?
			""")) {
			statement.setString(1, playerId.toString());
			statement.setString(2, outfitId.value());
			statement.setString(3, renderMode.name());
			statement.setString(4, model.name());
			statement.setString(5, originalSkinHash);
			statement.setString(6, templateHash);
			try (ResultSet result = statement.executeQuery()) {
				if (!result.next()) return Optional.empty();
				return Optional.of(new GeneratedOutfitSkin(
					outfitId,
					renderMode,
					model,
					originalSkinHash,
					templateHash,
					new SkinPropertyData(result.getString("texture_value"), result.getString("texture_signature")),
					result.getLong("generated_at")
				));
			}
		}
	}

	public synchronized void saveGeneratedSkin(UUID playerId, GeneratedOutfitSkin skin) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			INSERT INTO generated_outfit_skins(
			    player_uuid, outfit_id, render_mode, model, original_skin_hash, template_hash, texture_value, texture_signature, generated_at
			) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(player_uuid, outfit_id, render_mode, model, original_skin_hash, template_hash) DO UPDATE SET
			texture_value = excluded.texture_value,
			texture_signature = excluded.texture_signature,
			generated_at = excluded.generated_at
			""")) {
			statement.setString(1, playerId.toString());
			statement.setString(2, skin.outfitId().value());
			statement.setString(3, skin.renderMode().name());
			statement.setString(4, skin.model().name());
			statement.setString(5, skin.originalSkinHash());
			statement.setString(6, skin.templateHash());
			statement.setString(7, skin.property().value());
			statement.setString(8, skin.property().signature());
			statement.setLong(9, skin.generatedAtEpochMillis());
			statement.executeUpdate();
		}
	}

	@Override
	public synchronized void close() throws SQLException {
		connection.close();
	}
}
