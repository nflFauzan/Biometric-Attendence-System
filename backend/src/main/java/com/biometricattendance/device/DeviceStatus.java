package com.biometricattendance.device;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Status online/offline mesin dari waktu kontak terakhir (K-064). Batasnya parameter teknis
 * {@code DEVICE_OFFLINE_AFTER}, bukan aturan bisnis.
 */
@Component
public class DeviceStatus {

	private final Duration offlineAfter;

	public DeviceStatus(@Value("${app.device.offline-after}") Duration offlineAfter) {
		this.offlineAfter = offlineAfter;
	}

	public boolean isOnline(Device device, Instant now) {
		return isOnline(device.getLastSeenAt(), now);
	}

	public boolean isOnline(Instant lastSeenAt, Instant now) {
		return lastSeenAt != null && !lastSeenAt.isBefore(now.minus(offlineAfter));
	}

	public Duration offlineAfter() {
		return offlineAfter;
	}

}
