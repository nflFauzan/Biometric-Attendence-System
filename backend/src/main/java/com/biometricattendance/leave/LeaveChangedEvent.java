package com.biometricattendance.leave;

/**
 * Diterbitkan saat pengajuan izin dibuat, diputus, dibatalkan, atau diberi lampiran. Dashboard menyiarkannya
 * lewat SSE setelah commit sehingga Owner/HR langsung mendapat notifikasi (K-081) dan rekap ikut diperbarui.
 */
public record LeaveChangedEvent(Long requestId, Long employeeId, String employeeName, String leaveTypeName,
		Action action) {

	public enum Action {
		SUBMITTED, APPROVED, REJECTED, CANCELLED, ATTACHMENT_ADDED
	}

}
