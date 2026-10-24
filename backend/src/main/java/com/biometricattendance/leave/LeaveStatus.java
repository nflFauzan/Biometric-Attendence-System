package com.biometricattendance.leave;

/**
 * Status pengajuan izin/cuti (K-074). Pending dan DISETUJUI "aktif" untuk cek tumpang tindih (K-075); DRAFT belum
 * diajukan sehingga tidak dihitung. Kumpulan status ada di {@link LeaveStatuses}.
 */
public enum LeaveStatus {
	DRAFT, PERLU_TINJAU_HR, PERLU_TINJAU_OWNER, DISETUJUI, DITOLAK, DIBATALKAN;

	public boolean isPending() {
		return this == PERLU_TINJAU_HR || this == PERLU_TINJAU_OWNER;
	}
}
