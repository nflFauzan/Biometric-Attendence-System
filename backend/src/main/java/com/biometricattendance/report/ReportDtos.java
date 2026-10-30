package com.biometricattendance.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.biometricattendance.calculation.DailyResult.AttendanceStatus;

/** Model laporan yang sudah jadi: penulis Excel/PDF hanya merender, tanpa logika sendiri. */
public final class ReportDtos {

	private ReportDtos() {
	}

	public record ReportFilter(LocalDate from, LocalDate to, Long branchId, Long categoryId, Long employeeId) {
	}

	public record ReportModel(ReportType type, String title, String periodLabel, List<String> filterLabels,
			List<LocalDate> dates, List<ReportEmployee> employees) {
	}

	public record ReportEmployee(String employeeNumber, String fullName, String branchName, String categoryName,
			List<ReportDay> days, ReportTotals totals) {
	}

	/**
	 * @param status null untuk hari di luar masa kerja
	 * @param mark isi sel kisi: "V", "?", "I", atau kosong (TIDAK_HADIR/TIDAK_ADA_SCAN/di luar masa kerja)
	 */
	public record ReportDay(LocalDate date, String dayName, AttendanceStatus status, LocalTime checkIn,
			LocalTime checkOut, BigDecimal regularHours, BigDecimal validatedOvertimeHours,
			List<LocalTime> scanTimes, String mark) {
	}

	public record ReportTotals(int countingDays, int leaveDays, int absentDays, BigDecimal countingHours,
			BigDecimal validatedOvertimeHours) {
	}

}
