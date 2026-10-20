package com.biometricattendance.device;

/** "Buat kode baru": kunci tertunda dibuang, sehingga ganti kunci yang sedang berjalan dibatalkan (spec §18.6). */
public record KeyRevokedEvent(Long deviceId) {
}
