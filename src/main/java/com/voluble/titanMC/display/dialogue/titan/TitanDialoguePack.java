package com.voluble.titanMC.display.dialogue.titan;

import com.voluble.titanMC.display.dialogue.DialogueLayout;
import net.kyori.adventure.key.Key;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public record TitanDialoguePack(
	String namespace,
	DialogueLayout layout,
	Map<String, String> imageUnicodes,
	Map<String, Integer> imageSizes,
	Path outputDirectory
) {
	private static final String NEGATIVE_OFFSET = Character.toString(0x4E03);
	private static final String POSITIVE_OFFSET = Character.toString(0x25CFE);

	public TitanDialoguePack {
		namespace = clean(namespace, "namespace");
		layout = Objects.requireNonNull(layout, "layout");
		imageUnicodes = Map.copyOf(Objects.requireNonNull(imageUnicodes, "imageUnicodes"));
		imageSizes = Map.copyOf(Objects.requireNonNull(imageSizes, "imageSizes"));
		outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
	}

	public Key defaultFont() {
		return Key.key(namespace, namespace + "_default");
	}

	public Key nameFont() {
		return Key.key(namespace, namespace + "_line_name");
	}

	public Key dialogueFont(int lineNumber) {
		return Key.key(namespace, namespace + "_line_" + lineNumber);
	}

	public Key answerFont(int lineNumber) {
		return Key.key(namespace, namespace + "_answer_" + lineNumber);
	}

	public String image(String key) {
		String unicode = imageUnicodes.get(key);
		if (unicode == null) throw new IllegalArgumentException("Unknown TitanMC dialogue image: " + key);
		return unicode;
	}

	public int imageSize(String key) {
		Integer size = imageSizes.get(key);
		if (size == null) throw new IllegalArgumentException("Unknown TitanMC dialogue image size: " + key);
		return size;
	}

	public static String offset(float pixels) {
		if (pixels == 0) return "";
		String glyph = pixels < 0 ? NEGATIVE_OFFSET : POSITIVE_OFFSET;
		return glyph.repeat((int) Math.ceil(Math.abs(pixels)));
	}

	private static String clean(String value, String label) {
		Objects.requireNonNull(value, label);
		String cleaned = value.trim();
		if (cleaned.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
		return cleaned;
	}
}
