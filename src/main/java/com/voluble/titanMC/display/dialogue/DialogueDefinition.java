package com.voluble.titanMC.display.dialogue;

import java.util.List;
import java.util.Objects;

public record DialogueDefinition(
	String id,
	String speaker,
	DialogueSettings settings,
	DialogueTheme theme,
	List<DialoguePage> pages
) {
	public DialogueDefinition {
		id = clean(id, "dialogue id");
		speaker = clean(speaker, "dialogue speaker");
		settings = Objects.requireNonNull(settings, "settings");
		theme = Objects.requireNonNull(theme, "theme");
		Objects.requireNonNull(pages, "pages");
		if (pages.isEmpty()) throw new IllegalArgumentException("dialogue must contain at least one page");
		if (pages.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("dialogue pages must not contain null");
		pages = List.copyOf(pages);
	}

	public DialogueDefinition(String id, String speaker, DialogueTheme theme, List<DialoguePage> pages) {
		this(id, speaker, DialogueSettings.defaults(), theme, pages);
	}

	public DialoguePage firstPage() {
		return pages.getFirst();
	}

	public DialoguePage requirePage(String id) {
		String pageId = clean(id, "page id");
		return pages.stream()
			.filter(page -> page.id().equals(pageId))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown dialogue page: " + pageId));
	}

	private static String clean(String value, String label) {
		Objects.requireNonNull(value, label);
		String cleaned = value.trim();
		if (cleaned.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
		return cleaned;
	}
}
