package com.voluble.titanMC.cinematics.editor;

import com.voluble.titanMC.cinematics.model.CinematicEventPosition;

import java.util.List;

final class CinematicEditorLore {
	private CinematicEditorLore() {
	}

	static List<String> edit(String label, String value) {
		return List.of("<gray>" + label + ": <white>" + value, "<green>Click to edit.");
	}

	static List<String> captureLocation(CinematicEventPosition position) {
		return List.of(
			"<gray>Current: <white>" + rounded(position.x()) + ", " + rounded(position.y()) + ", " + rounded(position.z()),
			"<green>Click to use your current position."
		);
	}

	static String rounded(double value) {
		return String.format(java.util.Locale.US, "%.2f", value);
	}
}
