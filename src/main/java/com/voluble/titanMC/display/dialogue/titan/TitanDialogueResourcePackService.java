package com.voluble.titanMC.display.dialogue.titan;

import com.voluble.titanMC.display.dialogue.DialogueLayout;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TitanDialogueResourcePackService {
	private static final String ROOT = "display/dialogue/titan/";
	private static final String OUTPUT = "display/dialogue/Output";
	private static final int[] UNICODE_RANGES = {
		0x16A0, 0x16DF,
		0x1681, 0x168F,
		0x2D30, 0x2D44,
		0x10300, 0x10323,
		0x1032D, 0x1034A,
		0x10380, 0x10393,
		0x2C00, 0x2C5F,
		0x10400, 0x10403
	};

	private final Plugin plugin;

	public TitanDialogueResourcePackService(Plugin plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
	}

	public TitanDialoguePack generate() {
		try {
			YamlConfiguration config = loadYaml("config.yml");
			YamlConfiguration lines = loadYaml("Pack/Lines/lines.yml");
			YamlConfiguration imagesConfig = loadYaml("Pack/Images/images.yml");
			YamlConfiguration soundsConfig = loadYaml("Pack/Sounds/sounds.yml");
			String namespace = config.getString("namespace", "titanmc_dialogue");
			DialogueLayout layout = new DialogueLayout(
				lines.getInt("Character-Name.ascent", 40),
				lines.getInt("Dialogue-Lines.ascent", 25),
				lines.getInt("Dialogue-Lines.space", 9),
				lines.getInt("Answer-Lines.ascent", 75),
				lines.getInt("Answer-Lines.space", 9),
				lines.getInt("Dialogue-Lines.count", 5),
				lines.getInt("Answer-Lines.count", 3)
			);
			Path output = plugin.getDataFolder().toPath().resolve(OUTPUT);
			deleteDirectory(output);
			Files.createDirectories(output);

			writeString(output.resolve("pack.mcmeta"), packMeta(plugin.getDescription().getVersion()));
			copyFontTextures(output, namespace);
			copyPixel(output, namespace);
			Map<String, String> imageUnicodes = new LinkedHashMap<>();
			Map<String, Integer> imageSizes = new HashMap<>();
			createDefaultFont(output, namespace, layout, imagesConfig, imageUnicodes, imageSizes);
			createLineFonts(output, namespace, layout);
			createSounds(output, namespace, soundsConfig);
			return new TitanDialoguePack(namespace, layout, imageUnicodes, imageSizes, output);
		} catch (Exception exception) {
			throw new IllegalStateException("Could not generate TitanMC dialogue resource pack", exception);
		}
	}

	private void createDefaultFont(
		Path output,
		String namespace,
		DialogueLayout layout,
		YamlConfiguration imagesConfig,
		Map<String, String> imageUnicodes,
		Map<String, Integer> imageSizes
	) throws Exception {
		ConfigurationSection section = imagesConfig.getConfigurationSection("Images");
		if (section == null) throw new IllegalStateException("Missing Images section in TitanMC dialogue images.yml");
		List<String> providers = new ArrayList<>();
		int unicodeIndex = 0;
		for (String key : section.getKeys(false)) {
			TitanDialogueImage image = new TitanDialogueImage(
				key,
				section.getString(key + ".file", "default.png"),
				section.getBoolean(key + ".is-arrow", false),
				section.getInt(key + ".reduction-ratio", 1),
				section.getInt(key + ".ascent", 0)
			);
			int width = framedImage(output, namespace, image);
			imageSizes.put(key, (int) Math.ceil((double) width / image.reductionRatio()) + 1);
			String file = namespace + ":" + namespace + "/" + image.file();
			int height = 256 / image.reductionRatio();
			if (image.arrow()) {
				for (int line = 1; line <= layout.maxAnswerLines(); line++) {
					String unicode = unicode(unicodeIndex++);
					imageUnicodes.put(key + line, unicode);
					int ascent = layout.answerLineAscent() - ((line - 1) * layout.answerLineSpacing()) + image.ascent();
					providers.add(bitmapProvider(file, ascent, height, unicode));
				}
			} else {
				String unicode = unicode(unicodeIndex++);
				imageUnicodes.put(key, unicode);
				providers.add(bitmapProvider(file, image.ascent(), height, unicode));
			}
		}
		providers.add(offsetProvider(namespace, true));
		providers.add(offsetProvider(namespace, false));
		String json = "{\n    \"providers\": [\n" + String.join(",\n", providers) + "\n    ]\n}";
		writeString(output.resolve("assets").resolve(namespace).resolve("font").resolve(namespace + "_default.json"), json);
	}

	private void createLineFonts(Path output, String namespace, DialogueLayout layout) throws Exception {
		createFont(output, namespace, namespace + "_line_name", layout.characterNameAscent());
		for (int line = 1; line <= layout.maxDialogueLines(); line++) {
			int ascent = layout.dialogueLineAscent() - ((line - 1) * layout.dialogueLineSpacing());
			createFont(output, namespace, namespace + "_line_" + line, ascent);
		}
		for (int line = 1; line <= layout.maxAnswerLines(); line++) {
			int ascent = layout.answerLineAscent() - ((line - 1) * layout.answerLineSpacing());
			createFont(output, namespace, namespace + "_answer_" + line, ascent);
		}
	}

	private void createFont(Path output, String namespace, String fontName, int ascent) throws Exception {
		String template = readResource("Pack/Lines/example_font.json")
			.replace("<namespace>", namespace)
			.replace("\"ascent\": x+3", "\"ascent\": " + (ascent + 3))
			.replace("\"ascent\": x+1", "\"ascent\": " + (ascent + 1))
			.replace("\"ascent\": x", "\"ascent\": " + ascent);
		writeString(output.resolve("assets").resolve(namespace).resolve("font").resolve(fontName + ".json"), template);
	}

	private void createSounds(Path output, String namespace, YamlConfiguration soundsConfig) throws Exception {
		ConfigurationSection section = soundsConfig.getConfigurationSection("Sounds");
		if (section == null) return;
		Path soundsDirectory = output.resolve("assets").resolve(namespace).resolve("sounds");
		Files.createDirectories(soundsDirectory);
		List<String> entries = new ArrayList<>();
		for (String key : section.getKeys(false)) {
			String file = section.getString(key + ".file");
			if (file == null || file.isBlank()) continue;
			copy("Pack/Sounds/" + file, soundsDirectory.resolve(file));
			String soundName = file.endsWith(".ogg") ? file.substring(0, file.length() - 4) : file;
			entries.add("    \"" + namespace + ".sounds." + key + "\": { \"sounds\": [\"" + namespace + ":" + soundName + "\"] }");
		}
		writeString(output.resolve("assets").resolve(namespace).resolve("sounds.json"), "{\n" + String.join(",\n", entries) + "\n}\n");
	}

	private int framedImage(Path output, String namespace, TitanDialogueImage image) throws Exception {
		String resource = ROOT + "Pack/Images/" + image.file();
		try (InputStream stream = plugin.getResource(resource)) {
			if (stream == null) throw new IllegalStateException("Missing bundled resource: " + resource);
			BufferedImage source = ImageIO.read(stream);
			BufferedImage frame = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = frame.createGraphics();
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setComposite(AlphaComposite.Clear);
			graphics.fillRect(0, 0, 256, 256);
			graphics.setComposite(AlphaComposite.Src);
			graphics.drawImage(source, 0, 0, null);
			graphics.dispose();
			Path target = output.resolve("assets").resolve(namespace).resolve("textures").resolve(namespace).resolve(image.file());
			Files.createDirectories(target.getParent());
			ImageIO.write(frame, "PNG", target.toFile());
			return source.getWidth();
		}
	}

	private void copyFontTextures(Path output, String namespace) throws Exception {
		copy("Output/assets/titanmc_dialogue/textures/font/titanmc_dialogue_font.png",
			output.resolve("assets").resolve(namespace).resolve("textures").resolve("font").resolve(namespace + "_font.png"));
		copy("Output/assets/titanmc_dialogue/textures/font/titanmc_dialogue_nonlatin.png",
			output.resolve("assets").resolve(namespace).resolve("textures").resolve("font").resolve(namespace + "_nonlatin.png"));
		copy("Output/assets/titanmc_dialogue/textures/font/titanmc_dialogue_accented.png",
			output.resolve("assets").resolve(namespace).resolve("textures").resolve("font").resolve(namespace + "_accented.png"));
	}

	private void copyPixel(Path output, String namespace) throws Exception {
		copy("Output/assets/titanmc_dialogue/textures/titanmc_dialogue/pixel.png",
			output.resolve("assets").resolve(namespace).resolve("textures").resolve(namespace).resolve("pixel.png"));
	}

	private YamlConfiguration loadYaml(String path) {
		try (InputStream stream = plugin.getResource(ROOT + path)) {
			if (stream == null) throw new IllegalStateException("Missing bundled resource: " + ROOT + path);
			return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
		} catch (Exception exception) {
			throw new IllegalStateException("Could not load TitanMC dialogue resource: " + path, exception);
		}
	}

	private String readResource(String path) throws Exception {
		try (InputStream stream = plugin.getResource(ROOT + path)) {
			if (stream == null) throw new IllegalStateException("Missing bundled resource: " + ROOT + path);
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private void copy(String resource, Path target) throws Exception {
		try (InputStream stream = plugin.getResource(ROOT + resource)) {
			if (stream == null) throw new IllegalStateException("Missing bundled resource: " + ROOT + resource);
			Files.createDirectories(target.getParent());
			Files.copy(stream, target);
		}
	}

	private static void writeString(Path target, String content) throws Exception {
		Files.createDirectories(target.getParent());
		Files.writeString(target, content, StandardCharsets.UTF_8);
	}

	private static String bitmapProvider(String file, int ascent, int height, String unicode) {
		return "        {\n"
			+ "            \"type\": \"bitmap\",\n"
			+ "            \"file\": \"" + file + "\",\n"
			+ "            \"ascent\": " + ascent + ",\n"
			+ "            \"height\": " + height + ",\n"
			+ "            \"chars\": [\"" + json(unicode) + "\"]\n"
			+ "        }";
	}

	private static String offsetProvider(String namespace, boolean negative) {
		String glyph = negative ? "\\u4E03" : "\\uD857\\uDCFE";
		int height = negative ? -3 : 0;
		return "        {\n"
			+ "            \"type\": \"bitmap\",\n"
			+ "            \"file\": \"" + namespace + ":" + namespace + "/pixel.png\",\n"
			+ "            \"ascent\": -2000,\n"
			+ "            \"height\": " + height + ",\n"
			+ "            \"chars\": [\"" + glyph + "\"]\n"
			+ "        }";
	}

	private static String packMeta(String version) {
		return "{\n"
			+ "  \"pack\": {\n"
			+ "    \"pack_format\": 34,\n"
			+ "    \"supported_formats\": [13, 32767],\n"
			+ "    \"description\": \"TitanMC Dialogue v" + json(version) + "\"\n"
			+ "  }\n"
			+ "}\n";
	}

	private static String unicode(int index) {
		int remaining = index;
		for (int range = 0; range < UNICODE_RANGES.length; range += 2) {
			int start = UNICODE_RANGES[range];
			int end = UNICODE_RANGES[range + 1];
			int size = end - start + 1;
			if (remaining < size) return Character.toString(start + remaining);
			remaining -= size;
		}
		throw new IllegalArgumentException("TitanMC dialogue unicode list exhausted at index " + index);
	}

	private static String json(String value) {
		StringBuilder builder = new StringBuilder();
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			switch (character) {
				case '\\' -> builder.append("\\\\");
				case '"' -> builder.append("\\\"");
				case '\n' -> builder.append("\\n");
				case '\r' -> builder.append("\\r");
				case '\t' -> builder.append("\\t");
				default -> builder.append(character);
			}
		}
		return builder.toString();
	}

	private static void deleteDirectory(Path path) throws Exception {
		if (!Files.exists(path)) return;
		try (var stream = Files.walk(path)) {
			List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
			for (Path current : paths) {
				Files.delete(current);
			}
		}
	}
}
