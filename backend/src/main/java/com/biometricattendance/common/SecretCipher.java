package com.biometricattendance.common;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Enkripsi AES-256-GCM untuk data rahasia yang harus bisa dibuka lagi oleh server, mis. password Wi-Fi mesin dan
 * kunci tertunda (K-089, spec §18.7). Kunci dibaca dari environment {@code DATA_ENCRYPTION_KEY}, tidak pernah dari
 * database. Format: {@code v1:} + base64(IV 12 byte ‖ ciphertext + tag), supaya kunci bisa diganti kelak.
 */
@Component
public class SecretCipher {

	private static final String PREFIX = "v1:";
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final SecretKeySpec key;

	public SecretCipher(@Value("${app.crypto.data-key:}") String base64Key) {
		if (!StringUtils.hasText(base64Key)) {
			this.key = null;
			return;
		}
		byte[] bytes = Base64.getDecoder().decode(base64Key.trim());
		// Kunci salah ukuran adalah kesalahan pengaturan server: lebih baik gagal start daripada diam-diam lemah.
		if (bytes.length != 32) {
			throw new IllegalArgumentException("DATA_ENCRYPTION_KEY harus 32 byte (base64), bukan " + bytes.length);
		}
		this.key = new SecretKeySpec(bytes, "AES");
	}

	/** Pesan untuk pengguna saat fitur yang butuh enkripsi dipakai tanpa DATA_ENCRYPTION_KEY (spec §18.7). */
	public static final String NOT_CONFIGURED = "Kunci enkripsi server belum diatur. Hubungi pengelola server.";

	public boolean isConfigured() {
		return key != null;
	}

	public String encrypt(String plain) {
		requireConfigured();
		byte[] iv = new byte[IV_BYTES];
		RANDOM.nextBytes(iv);
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			return PREFIX + Base64.getEncoder()
				.encodeToString(ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Enkripsi gagal", ex);
		}
	}

	public String decrypt(String sealed) {
		requireConfigured();
		if (sealed == null || !sealed.startsWith(PREFIX)) {
			throw new IllegalStateException("Format data terenkripsi tidak dikenal");
		}
		try {
			byte[] raw = Base64.getDecoder().decode(sealed.substring(PREFIX.length()));
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES));
			return new String(cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException ex) {
			throw new IllegalStateException("Data terenkripsi rusak atau kunci berbeda", ex);
		}
	}

	private void requireConfigured() {
		if (key == null) {
			throw ApiException.badRequest("Kunci enkripsi server belum diatur. Hubungi pengelola server.");
		}
	}

}
