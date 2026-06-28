package com.voluble.titanMC.display.dialogue;

public record DialogueLayout(
	int characterNameAscent,
	int dialogueLineAscent,
	int dialogueLineSpacing,
	int answerLineAscent,
	int answerLineSpacing,
	int maxDialogueLines,
	int maxAnswerLines
) {
	public DialogueLayout {
		if (dialogueLineSpacing < 0) throw new IllegalArgumentException("dialogue line spacing must not be negative");
		if (answerLineSpacing < 0) throw new IllegalArgumentException("answer line spacing must not be negative");
		if (maxDialogueLines <= 0) throw new IllegalArgumentException("max dialogue lines must be positive");
		if (maxAnswerLines < 0) throw new IllegalArgumentException("max answer lines must not be negative");
	}
}
