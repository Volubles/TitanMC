package com.voluble.titanMC.outfits.model;

import java.util.Objects;
import java.util.Optional;

public record OutfitPreference(OutfitMode mode, Optional<OutfitId> outfitId) {
	public static OutfitPreference original() {
		return new OutfitPreference(OutfitMode.ORIGINAL, Optional.empty());
	}

	public static OutfitPreference outfit(OutfitId outfitId) {
		return new OutfitPreference(OutfitMode.OUTFIT, Optional.of(outfitId));
	}

	public OutfitPreference {
		Objects.requireNonNull(mode, "mode");
		outfitId = Objects.requireNonNull(outfitId, "outfitId");
		if (mode == OutfitMode.OUTFIT && outfitId.isEmpty()) {
			throw new IllegalArgumentException("outfit preferences require an outfit id");
		}
		if (mode == OutfitMode.ORIGINAL && outfitId.isPresent()) {
			throw new IllegalArgumentException("original preferences must not contain an outfit id");
		}
	}
}
