package com.biometricattendance.device;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Kunci dan ID alat rakitan (K-088, docs/protokol-alat-rakitan.md §1). */
class DeviceKeysTests {

	@Test
	void newKeyIs43CharBase64UrlWithoutPadding() {
		String key = DeviceKeys.newKey();
		assertThat(key).hasSize(43).matches("[A-Za-z0-9_-]{43}");
		assertThat(DeviceKeys.newKey()).isNotEqualTo(key);
	}

	@Test
	void hashIsLowercaseSha256Hex() {
		// SHA-256("abc")
		assertThat(DeviceKeys.hash("abc"))
			.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void deviceIdHasRkPrefixAndSixUpperHex() {
		assertThat(DeviceKeys.newDeviceId()).matches("RK-[0-9A-F]{6}");
	}

}
