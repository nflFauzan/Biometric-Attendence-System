package com.biometricattendance.device;

/** Mesin menghubungi server. {@code cameOnline} true jika sebelumnya dianggap offline. */
public record DeviceContactEvent(Long deviceId, boolean cameOnline) {
}
