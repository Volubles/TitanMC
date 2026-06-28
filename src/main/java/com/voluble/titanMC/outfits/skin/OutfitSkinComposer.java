package com.voluble.titanMC.outfits.skin;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public final class OutfitSkinComposer {
	private static final SkinRegion[] BODY_BASE_REGIONS = {
		new SkinRegion(0, 16, 16, 16),
		new SkinRegion(16, 16, 24, 16),
		new SkinRegion(40, 16, 16, 16),
		new SkinRegion(16, 48, 16, 16),
		new SkinRegion(32, 48, 16, 16)
	};

	private final HttpClient client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	public byte[] compose(PlayerSkin originalSkin, Path templatePath) throws IOException, InterruptedException {
		Objects.requireNonNull(originalSkin, "originalSkin");
		BufferedImage original = readRemote(URI.create(originalSkin.url().toString()));
		BufferedImage template = readTemplate(templatePath);
		BufferedImage result = compose(original, template);
		return png(result);
	}

	public byte[] fullSkin(Path templatePath) throws IOException {
		return png(readTemplate(templatePath));
	}

	private BufferedImage readTemplate(Path templatePath) throws IOException {
		Objects.requireNonNull(templatePath, "templatePath");
		BufferedImage template = ImageIO.read(templatePath.toFile());
		if (template == null) throw new IOException("Could not read outfit template: " + templatePath);
		if (template.getWidth() != 64 || template.getHeight() != 64) {
			throw new IOException("Unsupported outfit template size: " + template.getWidth() + "x" + template.getHeight());
		}
		return template;
	}

	private byte[] png(BufferedImage result) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(result, "png", output);
		return output.toByteArray();
	}

	BufferedImage compose(BufferedImage original, BufferedImage template) {
		Objects.requireNonNull(original, "original");
		Objects.requireNonNull(template, "template");
		BufferedImage result = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = result.createGraphics();
		try {
			copyBodyBase(original, graphics);
			graphics.drawImage(template, 0, 0, 64, 64, null);
			copyHead(original, graphics);
		} finally {
			graphics.dispose();
		}
		return result;
	}

	private BufferedImage readRemote(URI uri) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(20))
			.header("User-Agent", "TitanMC-Outfits")
			.GET()
			.build();
		HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("Skin download failed with HTTP " + response.statusCode());
		}
		try (InputStream body = response.body()) {
			BufferedImage image = ImageIO.read(body);
			if (image == null) throw new IOException("Downloaded skin was not a PNG image");
			if (image.getWidth() != 64 || image.getHeight() < 32) {
				throw new IOException("Unsupported skin size: " + image.getWidth() + "x" + image.getHeight());
			}
			return image;
		}
	}

	private void copyHead(BufferedImage original, Graphics2D graphics) {
		Composite composite = graphics.getComposite();
		graphics.setComposite(AlphaComposite.Clear);
		graphics.fillRect(0, 0, 64, 16);
		graphics.setComposite(composite);
		graphics.drawImage(original.getSubimage(0, 0, 64, 16), 0, 0, null);
	}

	private void copyBodyBase(BufferedImage original, Graphics2D graphics) {
		for (SkinRegion region : BODY_BASE_REGIONS) {
			if (!region.fits(original)) continue;
			graphics.drawImage(
				original.getSubimage(region.x(), region.y(), region.width(), region.height()),
				region.x(),
				region.y(),
				null
			);
		}
	}

	private record SkinRegion(int x, int y, int width, int height) {
		private boolean fits(BufferedImage image) {
			return x + width <= image.getWidth() && y + height <= image.getHeight();
		}
	}
}
