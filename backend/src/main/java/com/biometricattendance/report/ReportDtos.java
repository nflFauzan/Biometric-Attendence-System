package com.biometricattendance.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.Size;

import com.biometricattendance.calculation.DailyResult.AttendanceStatus;

/** Model laporan yang sudah jadi: penulis Excel/PDF hanya merender, tanpa logika sendiri. */
public final class ReportDtos {

	private ReportDtos() {
	}

	public record ReportFilter(LocalDate from, LocalDate to, Long branchId, Long categoryId, Long employeeId) {
	}

	public record BranchSummary(String branchName, BigDecimal totalAmount) {
	}

	public record ReportFinancial(
			BigDecimal dailyWage,
			BigDecimal grossDailyWage,
			BigDecimal overtimeRate,
			BigDecimal overtimeWage,
			BigDecimal loanDeduction,
			BigDecimal netWage,
			BigDecimal baseSalary,
			BigDecimal onsiteWage) {
	}

	public record ReportSettingRequest(@Size(max = 150, message = "Nama manager maksimal 150 karakter") String managerName,
			@Size(max = 150, message = "Departemen maksimal 150 karakter") String departmentName) {
	}

	public record ReportSettingResponse(String managerName, String departmentName) {
	}

	/**
	 * @param managerName isi kop "Manager Name" format klien, boleh null
	 * @param departmentName isi kop "Department" format klien, boleh null
	 * @param monthLabel isi kop "Month", misalnya "September 2026"; null untuk laporan non-bulanan
	 * @param offDates tanggal libur untuk kolom merah di kisi format klien, urut naik
	 */
	public record ReportModel(ReportType type, String title, String periodLabel, List<String> filterLabels,
			List<LocalDate> dates, List<ReportEmployee> employees, List<BranchSummary> branchSummaries,
			String managerName, String departmentName, String monthLabel, List<LocalDate> offDates) {

		public ReportModel(ReportType type, String title, String periodLabel, List<String> filterLabels,
				List<LocalDate> dates, List<ReportEmployee> employees, List<BranchSummary> branchSummaries) {
			this(type, title, periodLabel, filterLabels, dates, employees, branchSummaries, null, null, null,
					List.of());
		}

		public ReportModel(ReportType type, String title, String periodLabel, List<String> filterLabels,
				List<LocalDate> dates, List<ReportEmployee> employees) {
			this(type, title, periodLabel, filterLabels, dates, employees, List.of());
		}
	}

	public record ReportEmployee(Long employeeId, String employeeNumber, String fullName, String branchName,
			String categoryName, List<ReportDay> days, ReportTotals totals, ReportFinancial financial) {

		public ReportEmployee(String employeeNumber, String fullName, String branchName, String categoryName,
				List<ReportDay> days, ReportTotals totals) {
			this(null, employeeNumber, fullName, branchName, categoryName, days, totals, null);
		}
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
