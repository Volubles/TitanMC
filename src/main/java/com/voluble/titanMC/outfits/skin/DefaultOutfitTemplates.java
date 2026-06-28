package com.voluble.titanMC.outfits.skin;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class DefaultOutfitTemplates {
	private DefaultOutfitTemplates() {
	}

	public static void ensure(Path outfitFolder) throws java.io.IOException {
		Path templates = Objects.requireNonNull(outfitFolder, "outfitFolder").resolve("templates");
		Files.createDirectories(templates);
		Path prison = templates.resolve("prison_classic.png");
		if (Files.notExists(prison)) ImageIO.write(prisonClassic(), "png", prison.toFile());
		Path prisonSlim = templates.resolve("prison_slim.png");
		if (Files.notExists(prisonSlim)) ImageIO.write(prisonClassic(), "png", prisonSlim.toFile());
	}

	private static BufferedImage prisonClassic() {
		BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			Color orange = new Color(224, 111, 27);
			Color dark = new Color(95, 56, 36);
			Color light = new Color(244, 147, 48);
			// Body base and overlay.
			fill(graphics, orange, 16, 16, 24, 16);
			fill(graphics, light, 16, 32, 24, 16);
			fill(graphics, dark, 20, 20, 4, 12);
			fill(graphics, dark, 28, 20, 4, 12);
			fill(graphics, orange, 16, 48, 24, 16);
			// Arms and legs in classic skin layout.
			fill(graphics, orange, 40, 16, 16, 16);
			fill(graphics, orange, 32, 48, 16, 16);
			fill(graphics, orange, 0, 16, 16, 16);
			fill(graphics, orange, 16, 48, 16, 16);
		} finally {
			graphics.dispose();
		}
		return image;
	}

	private static void fill(Graphics2D graphics, Color color, int x, int y, int width, int height) {
		graphics.setColor(color);
		graphics.fillRect(x, y, width, height);
	}
}
