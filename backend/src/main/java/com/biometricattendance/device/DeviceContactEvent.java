package com.biometricattendance.device;

/**
 * Mesin menghubungi server. {@code cameOnline} true jika sebelumnya dianggap offline; {@code firstConnect} true
 * pada kontak pertama yang berhasil dengan kunci alat rakitan yang baru (konfirmasi "baru saja terhubung", K-099).
 */
public record DeviceContactEvent(Long deviceId, String deviceName, boolean cameOnline, boolean firstConnect) {
}
