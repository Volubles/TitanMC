package com.voluble.titanMC.outfits.skin;

import com.voluble.titanMC.outfits.model.OutfitId;
import com.voluble.titanMC.outfits.model.SkinModel;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MineSkinClient {
	private static final URI UPLOAD_ENDPOINT = URI.create("https://api.mineskin.org/v2/generate/upload");
	private static final Pattern VALUE = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern SIGNATURE = Pattern.compile("\"signature\"\\s*:\\s*\"([^\"]+)\"");

	private final HttpClient client = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.build();

	public SkinPropertyData upload(String apiKey, OutfitId outfitId, SkinModel model, byte[] png) throws IOException, InterruptedException {
		Objects.requireNonNull(apiKey, "apiKey");
		Objects.requireNonNull(outfitId, "outfitId");
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(png, "png");
		String boundary = "TitanMCBoundary" + System.nanoTime();
		byte[] body = multipart(boundary, outfitId, model, png);
		HttpRequest request = HttpRequest.newBuilder(UPLOAD_ENDPOINT)
			.timeout(Duration.ofSeconds(60))
			.header("Authorization", "Bearer " + apiKey)
			.header("User-Agent", "TitanMC-Outfits")
			.header("Content-Type", "multipart/form-data; boundary=" + boundary)
			.POST(HttpRequest.BodyPublishers.ofByteArray(body))
			.build();
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("MineSkin upload failed with HTTP " + response.statusCode() + ": " + trim(response.body()));
		}
		return property(response.body());
	}

	private static byte[] multipart(String boundary, OutfitId outfitId, SkinModel model, byte[] png) {
		String prefix = ""
			+ "--" + boundary + "\r\n"
			+ "Content-Disposition: form-data; name=\"name\"\r\n\r\n"
			+ "titanmc_" + outfitId.value() + "\r\n"
			+ "--" + boundary + "\r\n"
			+ "Content-Disposition: form-data; name=\"variant\"\r\n\r\n"
			+ (model == SkinModel.SLIM ? "slim" : "classic") + "\r\n"
			+ "--" + boundary + "\r\n"
			+ "Content-Disposition: form-data; name=\"visibility\"\r\n\r\n"
			+ "private\r\n"
			+ "--" + boundary + "\r\n"
			+ "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
			+ "Content-Type: image/png\r\n\r\n";
		String suffix = "\r\n--" + boundary + "--\r\n";
		byte[] before = prefix.getBytes(StandardCharsets.UTF_8);
		byte[] after = suffix.getBytes(StandardCharsets.UTF_8);
		byte[] body = new byte[before.length + png.length + after.length];
		System.arraycopy(before, 0, body, 0, before.length);
		System.arraycopy(png, 0, body, before.length, png.length);
		System.arraycopy(after, 0, body, before.length + png.length, after.length);
		return body;
	}

	private static SkinPropertyData property(String body) throws IOException {
		Matcher value = VALUE.matcher(body);
		Matcher signature = SIGNATURE.matcher(body);
		if (!value.find() || !signature.find()) {
			throw new IOException("MineSkin response did not contain a texture property");
		}
		return new SkinPropertyData(unescape(value.group(1)), unescape(signature.group(1)));
	}

	private static String unescape(String value) {
		return value.replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\");
	}

	private static String trim(String value) {
		if (value == null) return "";
		return value.length() <= 500 ? value : value.substring(0, 500) + "...";
	}
}
