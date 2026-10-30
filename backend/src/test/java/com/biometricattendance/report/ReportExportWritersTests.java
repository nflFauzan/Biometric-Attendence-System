package com.biometricattendance.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.report.ReportDtos.BranchSummary;
import com.biometricattendance.report.ReportDtos.ReportDay;
import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportFilter;
import com.biometricattendance.report.ReportDtos.ReportFinancial;
import com.biometricattendance.report.ReportDtos.ReportModel;
import com.biometricattendance.report.ReportDtos.ReportTotals;

/**
 * Pengujian ekspor berkas Excel multi-sheet dan PDF untuk format dokumen fisik klien (AS-1 s/d AS-7).
 */
class ReportExportWritersTests {

	private final ExcelReportWriter excelWriter = new ExcelReportWriter();
	private final PdfReportWriter pdfWriter = new PdfReportWriter("Asia/Jakarta");

	private static final LocalDate SAT = LocalDate.parse("2026-10-03");

	private static ReportDay createDay(LocalDate date, AttendanceStatus status, String hours, String ot) {
		boolean hadir = status == AttendanceStatus.LENGKAP;
		return new ReportDay(date, "Sabtu", status, hadir ? LocalTime.of(8, 0) : null,
				hadir ? LocalTime.of(17, 0) : null, new BigDecimal(hours), new BigDecimal(ot),
				hadir ? List.of(LocalTime.of(8, 0), LocalTime.of(17, 0)) : List.of(), hadir ? "V" : "");
	}

	private ReportModel buildWeeklyWageModel() {
		List<LocalDate> dates = List.of(
				SAT, SAT.plusDays(1), SAT.plusDays(2), SAT.plusDays(3),
				SAT.plusDays(4), SAT.plusDays(5), SAT.plusDays(6)
		);
		List<ReportDay> days = List.of(
				createDay(dates.get(0), AttendanceStatus.LENGKAP, "8.00", "2.00"),
				createDay(dates.get(1), AttendanceStatus.LENGKAP, "8.00", "0.00"),
				createDay(dates.get(2), AttendanceStatus.LENGKAP, "8.00", "1.00"),
				createDay(dates.get(3), AttendanceStatus.LENGKAP, "8.00", "0.00"),
				createDay(dates.get(4), AttendanceStatus.LENGKAP, "8.00", "0.00"),
				createDay(dates.get(5), AttendanceStatus.LENGKAP, "8.00", "0.00"),
				createDay(dates.get(6), AttendanceStatus.TIDAK_HADIR, "0.00", "0.00")
		);

		ReportTotals totals = new ReportTotals(6, 0, 1, new BigDecimal("48.00"), new BigDecimal("3.00"));
		// Supir: Upah 100rb * 6 = 600rb. Lembur 3j * 14rb = 42rb. Kasbon 100rb. Sisa = 542rb.
		ReportFinancial financial = new ReportFinancial(
				new BigDecimal("100000"), new BigDecimal("600000"),
				new BigDecimal("14000"), new BigDecimal("42000"),
				new BigDecimal("100000"), new BigDecimal("542000"),
				null, null
		);

		ReportEmployee e1 = new ReportEmployee(1L, "EMP-001", "Asep Supir", "Takka 1", "Supir", days, totals, financial);
		ReportEmployee e2 = new ReportEmployee(2L, "EMP-002", "Ujang Kenek", "Takka 2", "Kenek", days, totals, financial);

		List<BranchSummary> branchSummaries = List.of(
				new BranchSummary("Takka 1", new BigDecimal("542000")),
				new BranchSummary("Takka 2", new BigDecimal("542000")),
				new BranchSummary("Cash", new BigDecimal("1084000"))
		);

		return new ReportModel(
				ReportType.UPAH_MINGGUAN,
				ReportType.UPAH_MINGGUAN.title(),
				"03-10-2026 s.d. 09-10-2026",
				List.of("Semua cabang", "Semua kategori", "Semua karyawan"),
				dates,
				List.of(e1, e2),
				branchSummaries
		);
	}

	private ReportModel buildMonthlySalaryModel() {
		List<LocalDate> dates = List.of(
				LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-03")
		);
		List<ReportDay> days = List.of(
				createDay(dates.get(0), AttendanceStatus.LENGKAP, "8.00", "2.00"),
				createDay(dates.get(1), AttendanceStatus.LENGKAP, "8.00", "1.00"),
				createDay(dates.get(2), AttendanceStatus.LENGKAP, "8.00", "0.00")
		);
		ReportTotals totals = new ReportTotals(3, 0, 0, new BigDecimal("24.00"), new BigDecimal("3.00"));
		// Staf: Onsite 3 hari * 40rb = 120rb. Gaji Pokok 3jt. Lembur 3j * 14rb = 42rb. Kasbon 50rb. Total = 3.112.000
		ReportFinancial financial = new ReportFinancial(
				new BigDecimal("40000"), null,
				new BigDecimal("14000"), new BigDecimal("42000"),
				new BigDecimal("50000"), new BigDecimal("3112000"),
				new BigDecimal("3000000"), new BigDecimal("120000")
		);

		ReportEmployee staff = new ReportEmployee(10L, "STF-001", "Siti Admin", "Toko Takka 2", "Accounting", days, totals, financial);

		return new ReportModel(
				ReportType.GAJI_BULANAN,
				ReportType.GAJI_BULANAN.title(),
				"01-10-2026 s.d. 31-10-2026",
				List.of("Cabang: Toko Takka 2", "Semua kategori", "Semua karyawan"),
				dates,
				List.of(staff),
				List.of(new BranchSummary("Toko Takka 2", new BigDecimal("3112000")))
		);
	}

	@Test
	void testWeeklyWageExcelContainsAS1AndAS2Sheets() throws Exception {
		byte[] xlsx = excelWriter.write(buildWeeklyWageModel());
		try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			assertThat(wb.getNumberOfSheets()).isGreaterThanOrEqualTo(2);
			Sheet as1 = wb.getSheet("Rekap Upah");
			assertThat(as1).isNotNull();
			Sheet as2 = wb.getSheet("Rekap Lemburan");
			assertThat(as2).isNotNull();
		}
	}

	@Test
	void testMonthlySalaryExcelContainsAS5AS6AndAS7Sheets() throws Exception {
		byte[] xlsx = excelWriter.write(buildMonthlySalaryModel());
		try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
			assertThat(wb.getNumberOfSheets()).isGreaterThanOrEqualTo(3);
			Sheet as5 = wb.getSheet("Gaji Staf");
			assertThat(as5).isNotNull();
			Sheet as6 = wb.getSheet("Lembur Staf");
			assertThat(as6).isNotNull();
			Sheet as7 = wb.getSheet("Pengajuan Kas Toko");
			assertThat(as7).isNotNull();
		}
	}

	@Test
	void testWeeklyWageAndMonthlySalaryPdfExportSuccess() {
		byte[] pdfWeekly = pdfWriter.write(buildWeeklyWageModel());
		assertThat(pdfWeekly).isNotEmpty();
		assertThat(new String(pdfWeekly, 0, 5)).isEqualTo("%PDF-");

		byte[] pdfMonthly = pdfWriter.write(buildMonthlySalaryModel());
		assertThat(pdfMonthly).isNotEmpty();
		assertThat(new String(pdfMonthly, 0, 5)).isEqualTo("%PDF-");
	}
}

