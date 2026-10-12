package com.biometricattendance.device;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** K-064: mesin online bila kontak terakhir masih dalam batas DEVICE_OFFLINE_AFTER. */
class DeviceStatusTests {

	private final DeviceStatus status = new DeviceStatus(Duration.ofMinutes(5));
	private final Instant now = Instant.parse("2026-10-12T06:30:00Z");

	@Test
	void neverSeenIsOffline() {
		assertThat(status.isOnline((Instant) null, now)).isFalse();
	}

	@Test
	void recentContactIsOnline() {
		assertThat(status.isOnline(now.minusSeconds(107), now)).isTrue();
		assertThat(status.isOnline(now.minus(Duration.ofMinutes(5)), now)).isTrue();
	}

	@Test
	void contactOlderThanLimitIsOffline() {
		assertThat(status.isOnline(now.minus(Duration.ofMinutes(5)).minusSeconds(1), now)).isFalse();
	}

}
