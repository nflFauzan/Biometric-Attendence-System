package com.biometricattendance.attendance;

import java.time.LocalDate;

/** Rekap seorang karyawan berubah karena koreksi; didengar setelah commit untuk memperbarui dashboard. */
public record AttendanceChangedEvent(Long employeeId, LocalDate date) {
}
