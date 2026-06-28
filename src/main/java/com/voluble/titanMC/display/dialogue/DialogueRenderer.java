package com.voluble.titanMC.display.dialogue;

import com.voluble.titanMC.display.dialogue.titan.TitanDialoguePack;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DialogueRenderer {
	private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
	private static final Pattern HEX_COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");

	public Component render(DialogueSession session) {
		Objects.requireNonNull(session, "session");
		String miniMessage = renderMiniMessage(session);
		return MINI_MESSAGE.deserialize(miniMessage);
	}

	private String renderMiniMessage(DialogueSession session) {
		DialogueDefinition definition = session.definition();
		DialogueTheme theme = definition.theme();
		DialogueVisualStyle style = theme.visualStyle();
		TitanDialoguePack pack = theme.pack();
		StringBuilder output = new StringBuilder();
		output.append(openFont(pack.defaultFont()));

		if (style.backgroundFog()) {
			output.append(TitanDialoguePack.offset(-1536));
			output.append(fog(style, pack));
			output.append(TitanDialoguePack.offset(-1536));
		}

		output.append(imageLayer(
			style.dialogueBackgroundColor(),
			TitanDialoguePack.offset(style.dialogueBackgroundOffset() - pack.imageSize(style.dialogueBackgroundImageKey())),
			pack.image(style.dialogueBackgroundImageKey()),
			TitanDialoguePack.offset(-style.dialogueBackgroundOffset() - pack.imageSize(style.dialogueBackgroundImageKey()))
		));
		if (style.characterName()) {
			output.append(formatCharacterName(definition.speaker(), theme));
		}
		if (style.characterImage()) {
			output.append(imageLayer(
				style.characterBackgroundColor(),
				TitanDialoguePack.offset(style.characterOffset()),
				pack.image(style.characterImageKey()),
				TitanDialoguePack.offset(-style.characterOffset() - pack.imageSize(style.characterImageKey()))
			));
		}

		output.append(TitanDialoguePack.offset(style.dialogueTextOffset()));
		output.append(openColor(theme.dialogueColor()));
		List<String> lines = visibleLines(session);
		for (int index = 0; index < Math.min(lines.size(), pack.layout().maxDialogueLines()); index++) {
			String line = lines.get(index);
			output.append(openFont(pack.dialogueFont(index + 1)));
			output.append(titanColors(line));
			output.append(closeFont(pack.dialogueFont(index + 1)));
			output.append(TitanDialoguePack.offset(-theme.textWidth().width(line)));
		}
		output.append(closeColor(theme.dialogueColor()));
		output.append(TitanDialoguePack.offset(-style.dialogueTextOffset()));

		if (!session.page().answers().isEmpty()) {
			output.append(formatAnswers(session, theme));
		}

		output.append(closeFont(pack.defaultFont()));
		return output.toString();
	}

	private String formatAnswers(DialogueSession session, DialogueTheme theme) {
		DialogueVisualStyle style = theme.visualStyle();
		TitanDialoguePack pack = theme.pack();
		List<DialogueAnswer> answers = session.page().answers();
		StringBuilder output = new StringBuilder();
		String background = imageLayer(
			style.answerBackgroundColor(),
			TitanDialoguePack.offset(style.answerBackgroundOffset()),
			pack.image(style.answerBackgroundImageKey()),
			TitanDialoguePack.offset(-pack.imageSize(style.answerBackgroundImageKey()))
		);
		String arrow = imageLayer(
			style.arrowColor(),
			TitanDialoguePack.offset(style.arrowOffset()),
			pack.image(style.arrowImageKey() + session.selectedAnswer()),
			TitanDialoguePack.offset(-style.arrowOffset() - pack.imageSize(style.arrowImageKey()))
		);

		output.append(background);
		output.append(TitanDialoguePack.offset(style.answerTextOffset()));
		for (int index = 0; index < Math.min(answers.size(), pack.layout().maxAnswerLines()); index++) {
			int lineNumber = index + 1;
			DialogueAnswer answer = answers.get(index);
			String text = style.answerNumbers() ? lineNumber + ". " + answer.text() : answer.text();
			String plain = theme.textWidth().stripFormatting(text);
			TextColor color = lineNumber == session.selectedAnswer()
				? theme.selectedAnswerColor()
				: theme.answerColor();
			if (lineNumber == session.selectedAnswer()) {
				output.append(TitanDialoguePack.offset(-style.answerTextOffset()));
				output.append(arrow);
				output.append(TitanDialoguePack.offset(style.answerTextOffset()));
			}
			output.append(openColor(color));
			output.append(openFont(pack.answerFont(lineNumber)));
			output.append(titanColors(text));
			output.append(closeFont(pack.answerFont(lineNumber)));
			output.append(TitanDialoguePack.offset(-theme.textWidth().width(plain)));
			output.append(closeColor(color));
		}
		output.append(TitanDialoguePack.offset(-style.answerTextOffset()));
		output.append(TitanDialoguePack.offset(-style.answerBackgroundOffset()));
		return output.toString();
	}

	private String formatCharacterName(String name, DialogueTheme theme) {
		DialogueVisualStyle style = theme.visualStyle();
		TitanDialoguePack pack = theme.pack();
		float nameWidth = theme.textWidth().width(name);
		int midRepeats = Math.max(0, (int) nameWidth / 2);
		String mid = pack.image(style.nameMidImageKey()) + TitanDialoguePack.offset(-1);
		double backgroundWidth = pack.imageSize(style.nameStartImageKey()) - 1
			+ (pack.imageSize(style.nameMidImageKey()) - 1) * Math.floor(nameWidth / 2)
			+ pack.imageSize(style.nameEndImageKey()) - 1;

		StringBuilder output = new StringBuilder();
		output.append(TitanDialoguePack.offset(style.nameBackgroundOffset()));
		output.append(openColor(style.nameBackgroundColor()));
		output.append(pack.image(style.nameStartImageKey()));
		output.append(TitanDialoguePack.offset(-1));
		output.append(mid.repeat(midRepeats));
		output.append(pack.image(style.nameEndImageKey()));
		output.append(TitanDialoguePack.offset(-1));
		output.append(closeColor(style.nameBackgroundColor()));
		output.append(TitanDialoguePack.offset((float) ((backgroundWidth * -1) + 3)));
		output.append(TitanDialoguePack.offset(style.nameTextOffset()));
		for (int index = 0; index < name.length();) {
			int codePoint = name.codePointAt(index);
			index += Character.charCount(codePoint);
			output.append(openFont(pack.nameFont()));
			output.append(openColor(theme.speakerColor()));
			output.append(Character.toString(codePoint));
			output.append(closeColor(theme.speakerColor()));
			output.append(closeFont(pack.nameFont()));
		}
		output.append(TitanDialoguePack.offset(-style.nameTextOffset()));
		output.append(TitanDialoguePack.offset(-nameWidth - 3));
		output.append(TitanDialoguePack.offset(-style.nameBackgroundOffset()));
		return output.toString();
	}

	private String fog(DialogueVisualStyle style, TitanDialoguePack pack) {
		String section = openColor(style.fogColor())
			+ pack.image(style.fogImageKey())
			+ TitanDialoguePack.offset(-1)
			+ closeColor(style.fogColor());
		return section.repeat(12);
	}

	private static String imageLayer(TextColor color, String before, String image, String after) {
		return openColor(color) + before + image + after + closeColor(color);
	}

	private List<String> visibleLines(DialogueSession session) {
		List<String> visible = new ArrayList<>();
		int remaining = session.visibleCharacters();
		for (String line : session.page().lines()) {
			if (remaining <= 0) {
				visible.add("");
				continue;
			}
			VisibleText visibleText = visibleText(line, remaining);
			visible.add(visibleText.text());
			remaining -= visibleText.characters();
		}
		return visible;
	}

	private static VisibleText visibleText(String line, int remaining) {
		StringBuilder output = new StringBuilder();
		int visibleCharacters = 0;
		for (int index = 0; index < line.length() && visibleCharacters < remaining;) {
			if (line.charAt(index) == '#' && index + 7 <= line.length() && HEX_COLOR.matcher(line.substring(index, index + 7)).matches()) {
				output.append(line, index, index + 7);
				index += 7;
				continue;
			}
			if (line.charAt(index) == '<') {
				int end = line.indexOf('>', index);
				if (end > index) {
					output.append(line, index, end + 1);
					index = end + 1;
					continue;
				}
			}
			int codePoint = line.codePointAt(index);
			output.appendCodePoint(codePoint);
			index += Character.charCount(codePoint);
			visibleCharacters++;
		}
		return new VisibleText(output.toString(), visibleCharacters);
	}

	private static String titanColors(String text) {
		Matcher matcher = HEX_COLOR.matcher(text);
		StringBuilder output = new StringBuilder();
		while (matcher.find()) {
			matcher.appendReplacement(output, "<color:" + matcher.group() + ">");
		}
		matcher.appendTail(output);
		return output.toString();
	}

	private static String openColor(TextColor color) {
		return "<color:" + color.asHexString() + ">";
	}

	private static String closeColor(TextColor color) {
		return "</color:" + color.asHexString() + ">";
	}

	private static String openFont(Key font) {
		return "<font:" + font.asString() + ">";
	}

	private static String closeFont(Key font) {
		return "</font:" + font.asString() + ">";
	}

	private record VisibleText(String text, int characters) {
	}
}
