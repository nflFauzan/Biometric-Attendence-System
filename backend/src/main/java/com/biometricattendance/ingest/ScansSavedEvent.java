package com.biometricattendance.ingest;

/** Scan baru tersimpan; didengar setelah commit untuk memperbarui dashboard (DASH-006). */
public record ScansSavedEvent(Long deviceId, int saved) {
}
