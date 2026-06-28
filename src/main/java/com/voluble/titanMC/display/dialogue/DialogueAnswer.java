package com.voluble.titanMC.display.dialogue;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DialogueAnswer(
	String id,
	String text,
	Optional<String> targetPageId,
	Optional<String> condition,
	List<String> replyLines,
	Optional<DialogueSound> sound,
	List<String> actions
) {
	public DialogueAnswer {
		id = clean(id, "answer id");
		text = clean(text, "answer text");
		targetPageId = Objects.requireNonNull(targetPageId, "targetPageId")
			.map(value -> clean(value, "target page id"));
		condition = Objects.requireNonNull(condition, "condition")
			.map(value -> clean(value, "condition"));
		Objects.requireNonNull(replyLines, "replyLines");
		replyLines = replyLines.stream()
			.map(String::trim)
			.filter(value -> !value.isBlank())
			.toList();
		sound = Objects.requireNonNull(sound, "sound");
		Objects.requireNonNull(actions, "actions");
		actions = actions.stream()
			.map(String::trim)
			.filter(value -> !value.isBlank())
			.toList();
	}

	public DialogueAnswer(String id, String text, Optional<String> targetPageId) {
		this(id, text, targetPageId, Optional.empty(), List.of(), Optional.empty(), List.of());
	}

	public static DialogueAnswer close(String id, String text) {
		return new DialogueAnswer(id, text, Optional.empty());
	}

	public static DialogueAnswer gotoPage(String id, String text, String targetPageId) {
		return new DialogueAnswer(id, text, Optional.of(targetPageId));
	}

	private static String clean(String value, String label) {
		Objects.requireNonNull(value, label);
		String cleaned = value.trim();
		if (cleaned.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
		return cleaned;
	}
}
