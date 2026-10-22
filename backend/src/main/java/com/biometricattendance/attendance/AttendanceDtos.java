package com.biometricattendance.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import com.biometricattendance.calculation.CalculationDtos.DailyResultResponse;
import com.biometricattendance.leave.LeaveRequest;
import com.biometricattendance.leave.LeaveStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AttendanceDtos {

	private AttendanceDtos() {
	}

	/** Status peninjauan scan yang tertahan karena cabang (K-103). */
	public enum ReviewStatus {
		BELUM_DITINJAU, DITERIMA, DIABAIKAN
	}

	public enum Decision {
		DITERIMA, DIABAIKAN
	}

	/**
	 * Scan asli apa adanya; {@code deviceLocalTime} persis seperti dikirim mesin.
	 *
	 * @param review null bila scan tidak tertahan karena cabang
	 * @param counted false bila scan tertahan dan belum diterima, sehingga tidak masuk rekap
	 */
	public record ScanResponse(Long id, Instant scannedAt, LocalDateTime deviceLocalTime, Long deviceId,
			String deviceName, String devicePin, Long employeeId, Set<ScanFlag> flags, Instant receivedAt,
			ReviewStatus review, boolean counted) {

		/** Scan tanpa konteks peninjauan (mis. scan tak dikenal): tidak pernah tertahan. */
		public static ScanResponse from(AttendanceScan scan) {
			return from(scan, null);
		}

		/** @param review status peninjauan bila scan bertanda CABANG_TIDAK_DIIZINKAN, selain itu diabaikan */
		public static ScanResponse from(AttendanceScan scan, ReviewStatus review) {
			ReviewStatus status = scan.getFlags().contains(ScanFlag.CABANG_TIDAK_DIIZINKAN)
					? (review == null ? ReviewStatus.BELUM_DITINJAU : review) : null;
			return new ScanResponse(scan.getId(), scan.getScannedAt(), scan.getDeviceLocalTime(),
					scan.getDevice().getId(), scan.getDevice().getName(), scan.getDevicePin(), scan.getEmployeeId(),
					scan.getFlags(), scan.getReceivedAt(), status, status == null || status == ReviewStatus.DITERIMA);
		}

	}

	/** Scan tertahan beserta keputusan terakhir (bila ada). */
	public record HeldScanResponse(ScanResponse scan, String employeeName, String placementBranchName,
			String deviceBranchName, ReviewStatus status, String reason, String decidedBy, Instant decidedAt) {
	}

	/** K-103: tinjau satu atau beberapa scan tertahan sekaligus; alasan wajib. */
	public record ReviewRequest(@NotEmpty(message = "Pilih scan yang ditinjau") @Size(max = 200,
			message = "Maksimal 200 scan sekali tinjau") List<@NotNull(message = "ID scan wajib diisi") Long> scanIds,
			@NotNull(message = "Keputusan wajib diisi") Decision decision,
			@Size(max = 500, message = "Alasan maksimal 500 karakter") String reason) {
	}

	public record CorrectionResponse(Long id, Long employeeId, LocalDate workDate, LocalTime checkIn,
			LocalTime checkOut, String reason, String createdBy, Instant createdAt, Instant supersededAt) {

		public static CorrectionResponse from(AttendanceCorrection c) {
			return c == null ? null
					: new CorrectionResponse(c.getId(), c.getEmployeeId(), c.getWorkDate(), c.getCheckIn(),
							c.getCheckOut(), c.getReason(), c.getCreatedBy(), c.getCreatedAt(), c.getSupersededAt());
		}

	}

	/**
	 * Rekap satu karyawan pada satu tanggal, dihitung saat diminta (K-058).
	 *
	 * @param result hasil mesin perhitungan, null bila aturan belum lengkap
	 * @param ruleMissing pesan "Aturan belum diatur: …" (K-047), null bila aturan lengkap
	 * @param scanFlags gabungan penanda scan asli pada tanggal itu (K-065)
	 */
	/** @param leave izin/cuti disetujui (diutamakan) atau menunggu yang mencakup tanggal ini, atau null */
	public record DayRecord(LocalDate date, DailyResultResponse result, String ruleMissing, List<ScanResponse> scans,
			CorrectionResponse correction, Set<ScanFlag> scanFlags, LeaveInfo leave) {
	}

	/** Izin/cuti yang mencakup satu tanggal rekap (K-076). */
	public record LeaveInfo(Long requestId, String leaveTypeName, LeaveStatus status, LocalDate startDate,
			LocalDate endDate) {

		public static LeaveInfo from(LeaveRequest r) {
			return r == null ? null
					: new LeaveInfo(r.getId(), r.getLeaveType().getName(), r.getStatus(), r.getStartDate(),
							r.getEndDate());
		}

	}

	public record DailyRow(Long employeeId, String employeeNumber, String fullName, String devicePin, Long branchId,
			String branchName, String categoryName, DayRecord day) {
	}

	public record DailyAttendanceResponse(LocalDate date, Long branchId, List<DailyRow> rows) {
	}

	public record EmployeeAttendanceResponse(Long employeeId, String employeeNumber, String fullName,
			String branchName, String timezone, String devicePin, LocalDate from, LocalDate to, List<DayRecord> days,
			List<CorrectionResponse> corrections) {
	}

	/** K-059: koreksi jam masuk dan/atau pulang; alasan wajib. */
	public record CorrectionRequest(@NotNull(message = "Karyawan wajib dipilih") Long employeeId,
			@NotNull(message = "Tanggal wajib diisi") LocalDate date, LocalTime checkIn, LocalTime checkOut,
			@NotBlank(message = "Alasan koreksi wajib diisi") @Size(max = 500,
					message = "Alasan maksimal 500 karakter") String reason) {
	}

}
