package com.biometricattendance.ingest;

/** Baris kiriman mesin yang tidak bisa dibaca beserta alasannya (K-062). */
public record RejectedLine(String rawLine, String reason) {
}
