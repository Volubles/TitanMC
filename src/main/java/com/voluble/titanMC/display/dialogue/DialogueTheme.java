package com.voluble.titanMC.display.dialogue;

import com.voluble.titanMC.display.dialogue.titan.TitanDialoguePack;
import com.voluble.titanMC.display.dialogue.titan.TitanDialogueTextWidth;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;

import java.util.Objects;
import java.util.Optional;

public record DialogueTheme(
	String namespace,
	String fontPrefix,
	TitanDialoguePack pack,
	TitanDialogueTextWidth textWidth,
	TextColor speakerColor,
	TextColor dialogueColor,
	TextColor answerColor,
	TextColor selectedAnswerColor,
	DialogueVisualStyle visualStyle,
	DialogueLayout layout,
	Optional<DialogueSound> typingSound,
	Optional<DialogueSound> selectionSound,
	int typingSpeedTicks
) {
	public DialogueTheme {
		namespace = clean(namespace, "namespace");
		fontPrefix = clean(fontPrefix, "font prefix");
		pack = Objects.requireNonNull(pack, "pack");
		textWidth = Objects.requireNonNull(textWidth, "textWidth");
		speakerColor = Objects.requireNonNull(speakerColor, "speakerColor");
		dialogueColor = Objects.requireNonNull(dialogueColor, "dialogueColor");
		answerColor = Objects.requireNonNull(answerColor, "answerColor");
		selectedAnswerColor = Objects.requireNonNull(selectedAnswerColor, "selectedAnswerColor");
		visualStyle = Objects.requireNonNull(visualStyle, "visualStyle");
		layout = Objects.requireNonNull(layout, "layout");
		typingSound = Objects.requireNonNull(typingSound, "typingSound");
		selectionSound = Objects.requireNonNull(selectionSound, "selectionSound");
		if (typingSpeedTicks <= 0) throw new IllegalArgumentException("typing speed must be positive");
	}

	public Key nameFont() {
		return pack.nameFont();
	}

	public Key dialogueFont(int lineNumber) {
		return pack.dialogueFont(lineNumber);
	}

	public Key answerFont(int lineNumber) {
		return pack.answerFont(lineNumber);
	}

	private static String clean(String value, String label) {
		Objects.requireNonNull(value, label);
		String cleaned = value.trim();
		if (cleaned.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
		return cleaned;
	}
}
