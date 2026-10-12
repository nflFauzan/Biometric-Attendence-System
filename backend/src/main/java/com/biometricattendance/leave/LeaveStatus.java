package com.biometricattendance.leave;

/** Status pengajuan izin/cuti (K-074). Hanya MENUNGGU dan DISETUJUI yang "aktif" untuk cek tumpang tindih (K-075). */
public enum LeaveStatus {
	MENUNGGU, DISETUJUI, DITOLAK, DIBATALKAN
}
