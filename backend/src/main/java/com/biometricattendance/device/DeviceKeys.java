package com.biometricattendance.device;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Kunci rahasia dan ID alat rakitan (K-088, docs/protokol-alat-rakitan.md §1). Kunci 32 byte acak hanya dikirim
 * ke browser sekali; server menyimpan SHA-256-nya saja.
 */
public final class DeviceKeys {

	private static final SecureRandom RANDOM = new SecureRandom();

	private DeviceKeys() {
	}

	/** Kunci baru: 32 byte acak, base64url tanpa padding (43 karakter). */
	public static String newKey() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** SHA-256 kunci dalam hex huruf kecil (64 karakter), nilai yang disimpan di {@code devices.key_hash}. */
	public static String hash(String key) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 tidak tersedia", ex);
		}
	}

	/** ID mesin baru: {@code RK-} + 6 karakter heksadesimal huruf besar. */
	public static String newDeviceId() {
		byte[] bytes = new byte[3];
		RANDOM.nextBytes(bytes);
		return "RK-" + HexFormat.of().withUpperCase().formatHex(bytes);
	}

}
