package com.voluble.titanMC.outfits.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record OutfitDefinition(
	OutfitId id,
	String displayName,
	List<String> description,
	Path templatePath,
	SkinModel model
) {
	public OutfitDefinition {
		Objects.requireNonNull(id, "id");
		displayName = requireText(displayName, "displayName");
		description = List.copyOf(Objects.requireNonNull(description, "description"));
		Objects.requireNonNull(templatePath, "templatePath");
		Objects.requireNonNull(model, "model");
	}

	private static String requireText(String value, String name) {
		String normalized = Objects.requireNonNull(value, name).trim();
		if (normalized.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
		return normalized;
	}
}
