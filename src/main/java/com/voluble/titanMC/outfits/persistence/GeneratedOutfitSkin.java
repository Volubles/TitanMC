package com.voluble.titanMC.outfits.persistence;

import com.voluble.titanMC.outfits.model.OutfitId;
import com.voluble.titanMC.outfits.model.SkinModel;
import com.voluble.titanMC.outfits.skin.SkinPropertyData;

import java.util.Objects;

public record GeneratedOutfitSkin(
	OutfitId outfitId,
	SkinModel model,
	String originalSkinHash,
	SkinPropertyData property,
	long generatedAtEpochMillis
) {
	public GeneratedOutfitSkin {
		Objects.requireNonNull(outfitId, "outfitId");
		Objects.requireNonNull(model, "model");
		originalSkinHash = requireText(originalSkinHash, "originalSkinHash");
		Objects.requireNonNull(property, "property");
		if (generatedAtEpochMillis < 0L) throw new IllegalArgumentException("generatedAtEpochMillis must not be negative");
	}

	private static String requireText(String value, String name) {
		String normalized = Objects.requireNonNull(value, name).trim();
		if (normalized.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
		return normalized;
	}
}
