package com.biometricattendance.ingest;

/**
 * Ringkasan satu kiriman mesin.
 *
 * @param saved scan baru yang tersimpan
 * @param duplicates scan yang sudah pernah tersimpan (kiriman ulang)
 * @param ignored scan lebih lama dari "Terima scan sejak" (K-057)
 * @param rejected baris rusak yang disimpan di rejected_scan_lines (K-062)
 */
public record IngestResult(int saved, int duplicates, int ignored, int rejected) {
}
