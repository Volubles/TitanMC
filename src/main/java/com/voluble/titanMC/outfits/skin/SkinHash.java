package com.voluble.titanMC.outfits.skin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class SkinHash {
	private SkinHash() {
	}

	public static String sha256(String value) {
		return sha256(value.getBytes(StandardCharsets.UTF_8));
	}

	public static String sha256(Path path) throws IOException {
		return sha256(Files.readAllBytes(path));
	}

	private static String sha256(byte[] value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
			StringBuilder builder = new StringBuilder(digest.length * 2);
			for (byte part : digest) builder.append(String.format("%02x", part));
			return builder.toString();
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}
}
