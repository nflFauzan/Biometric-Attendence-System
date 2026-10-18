package com.biometricattendance.devicecommand;

import java.time.Instant;
import java.util.UUID;

/** Mesin melaporkan ENROLL berhasil; diterbitkan di dalam transaksi hasil agar catatan jari ikut tersimpan. */
public record EnrollSucceededEvent(UUID commandId, Long employeeId, int finger, Long deviceId, Instant at) {
}
