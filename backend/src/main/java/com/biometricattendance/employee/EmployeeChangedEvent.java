package com.biometricattendance.employee;

/**
 * Karyawan baru dibuat atau diubah; diterbitkan sinkron di dalam transaksi sebelum disimpan, supaya modul lain bisa
 * menolak perubahan (kunci PIN, K-093) atau ikut bertindak (hapus sidik jari, K-090) dalam transaksi yang sama.
 *
 * @param pinBefore PIN sebelum perubahan; null untuk karyawan baru
 */
public record EmployeeChangedEvent(Employee employee, String pinBefore, boolean created) {
}
