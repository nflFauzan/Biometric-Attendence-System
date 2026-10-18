package com.biometricattendance.devicecommand;

import java.time.Instant;
import java.util.UUID;

/**
 * Mesin melaporkan ENROLL berhasil; diterbitkan di dalam transaksi hasil agar catatan jari ikut tersimpan. {@code actor}
 * adalah pelaku audit ("mesin <ID mesin>").
 */
public record EnrollSucceededEvent(UUID commandId, Long employeeId, int finger, Long deviceId, String actor,
		Instant at) {
}
