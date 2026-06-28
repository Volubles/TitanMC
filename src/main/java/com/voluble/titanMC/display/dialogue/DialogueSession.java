package com.voluble.titanMC.display.dialogue;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

final class DialogueSession {
	private final UUID playerId;
	private final DialogueDefinition definition;
	private DialoguePage page;
	private int visibleCharacters;
	private int selectedAnswer = 1;

	DialogueSession(Player player, DialogueDefinition definition) {
		Objects.requireNonNull(player, "player");
		this.playerId = player.getUniqueId();
		this.definition = Objects.requireNonNull(definition, "definition");
		this.page = definition.firstPage();
	}

	UUID playerId() {
		return playerId;
	}

	DialogueDefinition definition() {
		return definition;
	}

	DialoguePage page() {
		return page;
	}

	int visibleCharacters() {
		return visibleCharacters;
	}

	int selectedAnswer() {
		return selectedAnswer;
	}

	boolean typingComplete() {
		return visibleCharacters >= totalCharacters();
	}

	boolean selectAnswer(int delta) {
		int count = Math.min(page.answers().size(), definition.theme().layout().maxAnswerLines());
		if (count <= 0) {
			selectedAnswer = 1;
			return false;
		}
		int previous = selectedAnswer;
		selectedAnswer = Math.floorMod(selectedAnswer - 1 + delta, count) + 1;
		return selectedAnswer != previous;
	}

	boolean revealNextCharacter() {
		if (visibleCharacters >= totalCharacters()) return false;
		visibleCharacters++;
		return true;
	}

	void showAll() {
		visibleCharacters = totalCharacters();
	}

	boolean advanceTargetPage() {
		if (page.targetPageId().isEmpty()) return false;
		page(definition.requirePage(page.targetPageId().orElseThrow()));
		return true;
	}

	void page(DialoguePage page) {
		this.page = Objects.requireNonNull(page, "page");
		this.visibleCharacters = 0;
		this.selectedAnswer = 1;
	}

	private int totalCharacters() {
		return page.lines().stream()
			.map(definition.theme().textWidth()::stripFormatting)
			.mapToInt(String::length)
			.sum();
	}
}
