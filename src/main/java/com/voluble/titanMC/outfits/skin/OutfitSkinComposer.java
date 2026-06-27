package com.voluble.titanMC.outfits.skin;

import com.voluble.titanMC.outfits.model.OutfitDefinition;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

public final class OutfitSkinComposer {
	private final HttpClient client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	public byte[] compose(URL originalSkinUrl, OutfitDefinition outfit) throws IOException, InterruptedException {
		Objects.requireNonNull(originalSkinUrl, "originalSkinUrl");
		Objects.requireNonNull(outfit, "outfit");
		BufferedImage original = readRemote(URI.create(originalSkinUrl.toString()));
		BufferedImage template = ImageIO.read(outfit.templatePath().toFile());
		if (template == null) throw new IOException("Could not read outfit template: " + outfit.templatePath());
		BufferedImage result = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = result.createGraphics();
		try {
			graphics.drawImage(template, 0, 0, 64, 64, null);
			copyHead(original, graphics);
		} finally {
			graphics.dispose();
		}
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(result, "png", output);
		return output.toByteArray();
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
		graphics.drawImage(original.getSubimage(0, 0, 64, 16), 0, 0, null);
	}
}
