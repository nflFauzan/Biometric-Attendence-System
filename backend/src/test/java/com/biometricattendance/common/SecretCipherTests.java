package com.biometricattendance.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

/** Enkripsi data rahasia di server: password Wi-Fi mesin dan kunci tertunda (K-089, spec §18.7). */
class SecretCipherTests {

	/** 32 byte 0..31; hanya untuk uji. */
	static final String TEST_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

	private final SecretCipher cipher = new SecretCipher(TEST_KEY);

	@Test
	void roundTrip() {
		String sealed = cipher.encrypt("rahasia");

		assertThat(sealed).startsWith("v1:").doesNotContain("rahasia");
		assertThat(cipher.decrypt(sealed)).isEqualTo("rahasia");
	}

	@Test
	void freshIvEachTime() {
		assertThat(cipher.encrypt("rahasia")).isNotEqualTo(cipher.encrypt("rahasia"));
	}

	@Test
	void tamperedCiphertextRejected() {
		String sealed = cipher.encrypt("rahasia");
		byte[] raw = Base64.getDecoder().decode(sealed.substring(3));
		raw[raw.length - 1] ^= 1;
		String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);

		assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void missingKeyNotConfigured() {
		SecretCipher empty = new SecretCipher("");

		assertThat(empty.isConfigured()).isFalse();
		assertThatThrownBy(() -> empty.encrypt("rahasia")).isInstanceOf(ApiException.class)
			.hasMessage("Kunci enkripsi server belum diatur. Hubungi pengelola server.");
	}

	@Test
	void wrongKeyLengthFailsFast() {
		String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

		assertThatThrownBy(() -> new SecretCipher(shortKey)).isInstanceOf(IllegalArgumentException.class);
	}

}
