package com.biometricattendance.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.biometricattendance.attendance.AttendanceDtos.DailyRow;
import com.biometricattendance.attendance.AttendanceDtos.DayRecord;
import com.biometricattendance.attendance.AttendanceDtos.ScanResponse;
import com.biometricattendance.attendance.ScanFlag;
import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.calculation.DailyResult.Flag;
import com.biometricattendance.device.DeviceDtos.DeviceResponse;
import com.biometricattendance.leave.LeaveStatus;

public final class DashboardDtos {

	private DashboardDtos() {
	}

	/**
	 * Ringkasan kehadiran (DASH-001–003, DASH-007).
	 *
	 * @param present ada scan atau koreksi
	 * @param incomplete status TIDAK_LENGKAP (mis. baru scan masuk)
	 * @param needsReview tidak lengkap, aturan belum diatur, penanda scan, atau penanda perhitungan yang perlu dicek
	 * @param onLeave izin/cuti disetujui pada tanggal tersebut (K-076)
	 */
	public record Counts(int totalEmployees, int present, int late, int incomplete, int overtimeCandidates,
			int absent, int needsReview, int ruleMissing, int onLeave) {

		static Counts of(List<DailyRow> rows) {
			int present = 0, late = 0, incomplete = 0, overtime = 0, absent = 0, review = 0, missing = 0, leave = 0;
			for (DailyRow row : rows) {
				DayRecord day = row.day();
				var result = day.result();
				AttendanceStatus status = result == null ? null : result.status();
				boolean hasData = result == null ? day.scans().stream().anyMatch(ScanResponse::counted) || day.correction() != null
						: status == AttendanceStatus.LENGKAP || status == AttendanceStatus.TIDAK_LENGKAP;
				boolean isIncomplete = status == AttendanceStatus.TIDAK_LENGKAP;
				present += hasData ? 1 : 0;
				late += result != null && result.lateSeconds() != null && result.lateSeconds() > 0 ? 1 : 0;
				incomplete += isIncomplete ? 1 : 0;
				overtime += result != null && result.overtimeCandidateSeconds() != null
						&& result.overtimeCandidateSeconds() > 0 ? 1 : 0;
				absent += status == AttendanceStatus.TIDAK_HADIR ? 1 : 0;
				missing += day.ruleMissing() != null ? 1 : 0;
				boolean flagged = result != null && (result.flags().contains(Flag.URUTAN_TIDAK_WAJAR)
						|| result.flags().contains(Flag.PERLU_TINJAUAN_HARI_LIBUR)
						|| result.flags().contains(Flag.HADIR_SAAT_IZIN)
						|| result.flags().contains(Flag.ADA_SCAN_TERTAHAN));
				// CABANG_BERBEDA hanya informasi (scan sah di cabang tambahan), bukan alasan tinjauan.
				boolean scanFlagged = day.scanFlags().stream().anyMatch(f -> f != ScanFlag.CABANG_BERBEDA);
				review += isIncomplete || day.ruleMissing() != null || scanFlagged || flagged ? 1 : 0;
				// Izin disetujui tanpa kehadiran; dihitung juga bila aturan kerja belum diatur (result null).
				boolean approvedLeave = day.leave() != null && day.leave().status() == LeaveStatus.DISETUJUI;
				leave += status == AttendanceStatus.IZIN || (result == null && approvedLeave && !hasData) ? 1 : 0;
			}
			return new Counts(rows.size(), present, late, incomplete, overtime, absent, review, missing, leave);
		}

	}

	public record BranchSummary(Long branchId, String branchName, Counts counts) {
	}

	public record RecentScan(Long id, Instant scannedAt, String devicePin, Long employeeId, String employeeName,
			String deviceName, Set<ScanFlag> flags) {
	}

	/**
	 * @param heldScans scan di cabang yang tidak diizinkan dan belum ditinjau (K-103)
	 * @param pendingLeaveRequests pengajuan izin/cuti yang menunggu keputusan (K-081), semua tanggal */
	public record DashboardSummary(LocalDate date, Counts totals, List<BranchSummary> branches,
			List<DeviceResponse> devices, List<RecentScan> recentScans, long unmatchedScans,
			long heldScans, long pendingLeaveRequests) {
	}

}
