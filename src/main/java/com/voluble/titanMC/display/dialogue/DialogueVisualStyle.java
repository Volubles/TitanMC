package com.voluble.titanMC.display.dialogue;

import net.kyori.adventure.text.format.TextColor;

import java.util.Objects;

public record DialogueVisualStyle(
	boolean characterName,
	boolean characterImage,
	boolean backgroundFog,
	boolean answerNumbers,
	int nameTextOffset,
	int nameBackgroundOffset,
	int dialogueBackgroundOffset,
	int dialogueTextOffset,
	int answerBackgroundOffset,
	int answerTextOffset,
	int arrowOffset,
	int characterOffset,
	String characterImageKey,
	String arrowImageKey,
	String dialogueBackgroundImageKey,
	String answerBackgroundImageKey,
	String nameStartImageKey,
	String nameMidImageKey,
	String nameEndImageKey,
	String fogImageKey,
	TextColor nameBackgroundColor,
	TextColor dialogueBackgroundColor,
	TextColor answerBackgroundColor,
	TextColor characterBackgroundColor,
	TextColor arrowColor,
	TextColor fogColor
) {
	public DialogueVisualStyle {
		characterImageKey = clean(characterImageKey, "character image key");
		arrowImageKey = clean(arrowImageKey, "arrow image key");
		dialogueBackgroundImageKey = clean(dialogueBackgroundImageKey, "dialogue background image key");
		answerBackgroundImageKey = clean(answerBackgroundImageKey, "answer background image key");
		nameStartImageKey = clean(nameStartImageKey, "name start image key");
		nameMidImageKey = clean(nameMidImageKey, "name mid image key");
		nameEndImageKey = clean(nameEndImageKey, "name end image key");
		fogImageKey = clean(fogImageKey, "fog image key");
		nameBackgroundColor = Objects.requireNonNull(nameBackgroundColor, "nameBackgroundColor");
		dialogueBackgroundColor = Objects.requireNonNull(dialogueBackgroundColor, "dialogueBackgroundColor");
		answerBackgroundColor = Objects.requireNonNull(answerBackgroundColor, "answerBackgroundColor");
		characterBackgroundColor = Objects.requireNonNull(characterBackgroundColor, "characterBackgroundColor");
		arrowColor = Objects.requireNonNull(arrowColor, "arrowColor");
		fogColor = Objects.requireNonNull(fogColor, "fogColor");
	}

	private static String clean(String value, String label) {
		Objects.requireNonNull(value, label);
		String cleaned = value.trim();
		if (cleaned.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
		return cleaned;
	}
}
