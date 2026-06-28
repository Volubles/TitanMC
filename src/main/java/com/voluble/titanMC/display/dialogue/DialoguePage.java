package com.voluble.titanMC.display.dialogue;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DialoguePage(
	String id,
	List<String> lines,
	List<DialogueAnswer> answers,
	Optional<String> targetPageId,
	List<String> preActions,
	List<String> postActions,
	List<String> exitActions
) {
	public DialoguePage {
		id = clean(id, "page id");
		Objects.requireNonNull(lines, "lines");
		if (lines.isEmpty()) throw new IllegalArgumentException("dialogue page must contain at least one line");
		if (lines.stream().anyMatch(line -> line == null || line.isBlank())) {
			throw new IllegalArgumentException("dialogue lines must not be blank");
		}
		Objects.requireNonNull(answers, "answers");
		if (answers.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("answers must not contain null");
		targetPageId = Objects.requireNonNull(targetPageId, "targetPageId")
			.map(value -> clean(value, "target page id"));
		lines = lines.stream().map(String::trim).toList();
		answers = List.copyOf(answers);
		preActions = actions(preActions, "preActions");
		postActions = actions(postActions, "postActions");
		exitActions = actions(exitActions, "exitActions");
	}

	public DialoguePage(String id, List<String> lines, List<DialogueAnswer> answers) {
		this(id, lines, answers, Optional.empty(), List.of(), List.of(), List.of());
	}

	public static DialoguePage of(String id, List<String> lines) {
		return new DialoguePage(id, lines, List.of());
	}

	private static List<String> actions(List<String> values, String label) {
		Objects.requireNonNull(values, label);
		return values.stream()
			.map(String::trim)
			.filter(value -> !value.isBlank())
			.toList();
	}

	private static String clean(String value, String label) {
		Objects.requireNonNull(value, label);
		String cleaned = value.trim();
		if (cleaned.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
		return cleaned;
	}
}
