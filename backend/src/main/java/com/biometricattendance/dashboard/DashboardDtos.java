package com.biometricattendance.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.biometricattendance.attendance.AttendanceDtos.DailyRow;
import com.biometricattendance.attendance.AttendanceDtos.DayRecord;
import com.biometricattendance.attendance.ScanFlag;
import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.calculation.DailyResult.Flag;
import com.biometricattendance.device.DeviceDtos.DeviceResponse;

public final class DashboardDtos {

	private DashboardDtos() {
	}

	/**
	 * Ringkasan kehadiran (DASH-001–003, DASH-007).
	 *
	 * @param present ada scan atau koreksi
	 * @param incomplete status TIDAK_LENGKAP (mis. baru scan masuk)
	 * @param needsReview tidak lengkap, aturan belum diatur, penanda scan, atau penanda perhitungan yang perlu dicek
	 */
	public record Counts(int totalEmployees, int present, int late, int incomplete, int overtimeCandidates,
			int absent, int needsReview, int ruleMissing) {

		static Counts of(List<DailyRow> rows) {
			int present = 0, late = 0, incomplete = 0, overtime = 0, absent = 0, review = 0, missing = 0;
			for (DailyRow row : rows) {
				DayRecord day = row.day();
				var result = day.result();
				AttendanceStatus status = result == null ? null : result.status();
				boolean hasData = result == null ? !day.scans().isEmpty() || day.correction() != null
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
						|| result.flags().contains(Flag.PERLU_TINJAUAN_HARI_LIBUR));
				review += isIncomplete || day.ruleMissing() != null || !day.scanFlags().isEmpty() || flagged ? 1 : 0;
			}
			return new Counts(rows.size(), present, late, incomplete, overtime, absent, review, missing);
		}

	}

	public record BranchSummary(Long branchId, String branchName, Counts counts) {
	}

	public record RecentScan(Long id, Instant scannedAt, String devicePin, Long employeeId, String employeeName,
			String deviceName, Set<ScanFlag> flags) {
	}

	public record DashboardSummary(LocalDate date, Counts totals, List<BranchSummary> branches,
			List<DeviceResponse> devices, List<RecentScan> recentScans, long unmatchedScans) {
	}

}
