package com.biometricattendance.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.PaperSize;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.report.ReportDtos.ReportDay;
import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportFinancial;
import com.biometricattendance.report.ReportDtos.ReportModel;
import com.biometricattendance.report.ReportDtos.ReportTotals;

/** Lembar AS-5 dan AS-6 harus sesuai foto format klien. Semua data di sini samaran. */
class TakkaExcelSheetsTests {

	private static final int DATE_COL = TakkaFormat.DATE_COL;

	private static final int HEADER = TakkaFormat.HEADER_ROW;

	private static final int FIRST = TakkaFormat.FIRST_DATA_ROW;

	static ReportDay day(LocalDate date, AttendanceStatus status, String overtime) {
		return new ReportDay(date, "", status, null, null, BigDecimal.ZERO, new BigDecimal(overtime), List.of(), "");
	}

	/** Karyawan: Senin-Sabtu LENGKAP, Minggu TIDAK_HADIR, kecuali diubah {@code override}. */
	static ReportEmployee employee(String name, YearMonth month, ReportFinancial financial,
			Function<ReportDay, ReportDay> override) {
		List<ReportDay> days = new ArrayList<>();
		int counted = 0;
		BigDecimal overtime = BigDecimal.ZERO;
		for (int d = 1; d <= month.lengthOfMonth(); d++) {
			LocalDate date = month.atDay(d);
			AttendanceStatus status = date.getDayOfWeek() == DayOfWeek.SUNDAY ? AttendanceStatus.TIDAK_HADIR
					: AttendanceStatus.LENGKAP;
			ReportDay day = override.apply(day(date, status, "0"));
			days.add(day);
			counted += day.status() == AttendanceStatus.LENGKAP ? 1 : 0;
			overtime = overtime.add(day.validatedOvertimeHours());
		}
		return new ReportEmployee(1L, name, name, "Cabang", "Staf", days,
				new ReportTotals(counted, 0, 0, BigDecimal.ZERO, overtime), financial);
	}

	static ReportFinancial financial(String net) {
		return new ReportFinancial(new BigDecimal("40000"), null, new BigDecimal("14000"), BigDecimal.ZERO,
				BigDecimal.ZERO, new BigDecimal(net), new BigDecimal("3000000"), new BigDecimal("1040000"));
	}

	static ReportModel model(YearMonth month, List<ReportEmployee> employees) {
		List<LocalDate> dates = month.atDay(1).datesUntil(month.atEndOfMonth().plusDays(1)).toList();
		List<LocalDate> off = dates.stream().filter(d -> d.getDayOfWeek() == DayOfWeek.SUNDAY).toList();
		return new ReportModel(ReportType.GAJI_BULANAN, "t", "p", List.of(), dates, employees, List.of(),
				"MANAGER SAMARAN", "DEPARTEMEN SAMARAN", "September 2026", off);
	}

	private static final YearMonth SEP = YearMonth.of(2026, 9);

	private static ReportEmployee plain(String name) {
		return employee(name, SEP, financial("4040000"), d -> d);
	}

	private XSSFWorkbook render(ReportModel model) throws Exception {
		return new XSSFWorkbook(new ByteArrayInputStream(new ExcelReportWriter().write(model)));
	}

	private static String fill(Cell cell) {
		var color = ((XSSFCellStyle) cell.getCellStyle()).getFillForegroundXSSFColor();
		return color == null ? null : color.getARGBHex().substring(2);
	}

	private static String format(Cell cell) {
		return cell.getCellStyle().getDataFormatString();
	}

	private static List<String> headers(XSSFSheet sheet, int from, int to) {
		List<String> out = new ArrayList<>();
		for (int c = from; c < to; c++) {
			out.add(sheet.getRow(HEADER).getCell(c).getStringCellValue());
		}
		return out;
	}

	@Test
	void salaryHeaderMatchesPhoto() throws Exception {
		try (XSSFWorkbook wb = render(model(SEP, List.of(plain("STAF SATU"))))) {
			XSSFSheet s = wb.getSheet("Gaji Staf");
			Cell band = s.getRow(0).getCell(0);
			assertThat(band.getStringCellValue()).isEqualTo("TAKKA STEEL");
			assertThat(fill(band)).isEqualTo("8EA9DB");
			assertThat(s.getMergedRegions()).anyMatch(r -> r.getFirstRow() == 0 && r.getLastColumn() == 40);
			assertThat(s.getRow(1).getCell(0).getStringCellValue()).startsWith("Manager Name:")
				.contains("MANAGER SAMARAN");
			assertThat(s.getRow(2).getCell(0).getStringCellValue()).startsWith("Department:")
				.contains("DEPARTEMEN SAMARAN");
			Cell month = s.getRow(TakkaFormat.SALARY_MONTH_ROW).getCell(40);
			assertThat(month.getStringCellValue()).isEqualTo("September 2026");
			assertThat(fill(month)).isEqualTo("FFE699");
			assertThat(s.getRow(HEADER).getCell(1).getStringCellValue()).isEqualTo("Employee Name");
			assertThat(fill(s.getRow(HEADER).getCell(2))).isEqualTo("E2B96F");
			assertThat(headers(s, DATE_COL, DATE_COL + 2)).containsExactly("1", "2");
			assertThat(s.getRow(HEADER).getCell(DATE_COL + 30).getStringCellValue()).isEqualTo("31");
			assertThat(headers(s, DATE_COL + 31, DATE_COL + 38)).containsExactly("Total Days", "Wages", "UPAH LEMBUR",
					"INPUT PIUTANG", "ONSITE", "GAJI POKOK", "Total Wages");
		}
	}

	@Test
	void overtimeHeaderMatchesPhoto() throws Exception {
		try (XSSFWorkbook wb = render(model(SEP, List.of(plain("STAF SATU"))))) {
			XSSFSheet s = wb.getSheet("Lembur Staf");
			assertThat(s.getRow(0).getCell(0).getStringCellValue()).isEqualTo("REKAP LEMBUR TAKKA STEEL");
			assertThat(s.getRow(HEADER).getCell(1).getStringCellValue()).isEqualTo("NAMA KARYAWAN");
			assertThat(headers(s, DATE_COL + 31, DATE_COL + 34)).containsExactly("TOTAL LEMBUR", "UANG LEMBUR",
					"JUMLAH UPAH");
			assertThat(s.getRow(TakkaFormat.OVERTIME_MONTH_ROW).getCell(36).getStringCellValue())
				.isEqualTo("September 2026");
		}
	}

	@Test
	void sundayColumnRedButWorkedSundayWhite() throws Exception {
		LocalDate sun13 = LocalDate.of(2026, 9, 13);
		ReportEmployee worked = employee("STAF DUA", SEP, financial("1"),
				d -> d.date().equals(sun13) ? day(sun13, AttendanceStatus.LENGKAP, "0") : d);
		try (XSSFWorkbook wb = render(model(SEP, List.of(plain("STAF SATU"), worked)))) {
			XSSFSheet s = wb.getSheet("Gaji Staf");
			for (int d : new int[] { 6, 13, 20, 27, 31 }) {
				assertThat(fill(s.getRow(HEADER).getCell(DATE_COL + d - 1))).as("judul %d", d).isEqualTo("E59191");
				assertThat(fill(s.getRow(FIRST).getCell(DATE_COL + d - 1))).as("sel %d", d).isEqualTo("E59191");
			}
			Cell v = s.getRow(FIRST + 1).getCell(DATE_COL + 12);
			assertThat(v.getStringCellValue()).isEqualTo("V");
			assertThat(fill(v)).isNull();
			assertThat(s.getRow(FIRST).getCell(DATE_COL).getStringCellValue()).isEqualTo("V");
			assertThat(fill(s.getRow(HEADER).getCell(DATE_COL))).isNotEqualTo("E59191");
		}
	}

	@Test
	void statusesRenderAsPhoto() throws Exception {
		ReportEmployee e = employee("STAF SATU", SEP, financial("1"), d -> switch (d.date().getDayOfMonth()) {
			case 1 -> day(d.date(), AttendanceStatus.TIDAK_LENGKAP, "0");
			case 2 -> day(d.date(), AttendanceStatus.IZIN, "0");
			case 3 -> day(d.date(), null, "0");
			default -> d;
		});
		try (XSSFWorkbook wb = render(model(SEP, List.of(e)))) {
			Row row = wb.getSheet("Gaji Staf").getRow(FIRST);
			assertThat(row.getCell(DATE_COL).getStringCellValue()).isEqualTo("?");
			assertThat(fill(row.getCell(DATE_COL))).isNull();
			assertThat(row.getCell(DATE_COL + 1).getStringCellValue()).isEmpty();
			assertThat(fill(row.getCell(DATE_COL + 1))).isEqualTo("E59191");
			assertThat(fill(row.getCell(DATE_COL + 2))).isEqualTo("E59191");
			assertThat(row.getCell(1).getStringCellValue()).isEqualTo("STAF SATU");
			assertThat(row.getCell(0).getNumericCellValue()).isEqualTo(1d);
		}
	}

	@Test
	void februaryKeeps31ColumnsWithMissingDatesRed() throws Exception {
		YearMonth feb = YearMonth.of(2026, 2);
		try (XSSFWorkbook wb = render(model(feb, List.of(employee("STAF SATU", feb, financial("1"), d -> d))))) {
			for (String name : List.of("Gaji Staf", "Lembur Staf")) {
				XSSFSheet s = wb.getSheet(name);
				for (int d : new int[] { 29, 30, 31 }) {
					assertThat(s.getRow(HEADER).getCell(DATE_COL + d - 1).getStringCellValue())
						.isEqualTo(String.valueOf(d));
					assertThat(fill(s.getRow(FIRST).getCell(DATE_COL + d - 1))).isEqualTo("E59191");
				}
			}
		}
	}

	@Test
	void overtimeOnSundayIsReadableOnRed() throws Exception {
		LocalDate sun = LocalDate.of(2026, 9, 6);
		ReportEmployee e = employee("STAF SATU", SEP, financial("1"),
				d -> d.date().equals(sun) ? day(sun, AttendanceStatus.TIDAK_HADIR, "1.50")
						: d.date().getDayOfMonth() == 1 ? day(d.date(), AttendanceStatus.LENGKAP, "1.33") : d);
		try (XSSFWorkbook wb = render(model(SEP, List.of(e)))) {
			Row row = wb.getSheet("Lembur Staf").getRow(FIRST);
			Cell c = row.getCell(DATE_COL + 5);
			assertThat(c.getNumericCellValue()).isEqualTo(1.5d);
			assertThat(fill(c)).isEqualTo("E59191");
			assertThat(format(c)).isEqualTo("General");
			assertThat(row.getCell(DATE_COL).getNumericCellValue()).isEqualTo(1.33d);
			assertThat(row.getCell(DATE_COL + 1).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
			assertThat(row.getCell(DATE_COL + 31).getNumericCellValue()).isEqualTo(2.83d);
		}
	}

	@Test
	void numberFormatsMatchPhoto() throws Exception {
		ReportFinancial loan = new ReportFinancial(new BigDecimal("40000"), null, new BigDecimal("14000"),
				new BigDecimal("280000"), new BigDecimal("500000"), new BigDecimal("1380000"),
				new BigDecimal("1000000"), new BigDecimal("840000"));
		ReportFinancial noBase = new ReportFinancial(new BigDecimal("140000"), null, new BigDecimal("14000"),
				BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("3640000"), null, new BigDecimal("3640000"));
		try (XSSFWorkbook wb = render(model(SEP, List.of(employee("A", SEP, loan, d -> d), employee("B", SEP, noBase, d -> d))))) {
			XSSFSheet s = wb.getSheet("Gaji Staf");
			Row r = s.getRow(FIRST);
			int c = DATE_COL + 31;
			assertThat(format(r.getCell(c))).isEqualTo("General");
			assertThat(format(r.getCell(c + 1))).isEqualTo("#,##0");
			assertThat(format(r.getCell(c + 2))).isEqualTo("#,##0");
			assertThat(r.getCell(c + 3).getNumericCellValue()).isEqualTo(500000d);
			assertThat(format(r.getCell(c + 3))).contains("Rp");
			assertThat(format(r.getCell(c + 4))).contains("Rp");
			assertThat(format(r.getCell(c + 5))).contains("Rp");
			assertThat(format(r.getCell(c + 6))).contains("Rp");
			Row b = s.getRow(FIRST + 1);
			assertThat(b.getCell(c + 2).getNumericCellValue()).isEqualTo(0d);
			assertThat(b.getCell(c + 3).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
			assertThat(b.getCell(c + 4).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
			assertThat(b.getCell(c + 5).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK);
			assertThat(b.getCell(c + 6).getNumericCellValue()).isEqualTo(3640000d);
			XSSFSheet ot = wb.getSheet("Lembur Staf");
			assertThat(format(ot.getRow(FIRST + 1).getCell(c + 2))).contains("\"-\"");
			assertThat(ot.getRow(FIRST + 1).getCell(c + 2).getNumericCellValue()).isEqualTo(0d);
		}
	}

	@Test
	void reconciliationOfRowsWithBaseSalaryAndTotalsRows() throws Exception {
		List<ReportEmployee> rows = new ArrayList<>();
		for (String net : List.of("4040000", "4320000", "3448000", "3467000", "3329000", "3410000", "2939000",
				"2579000", "1380000", "3080000")) {
			rows.add(employee("STAF " + net, SEP, financial(net), d -> d));
		}
		try (XSSFWorkbook wb = render(model(SEP, rows))) {
			XSSFSheet s = wb.getSheet("Gaji Staf");
			int c = DATE_COL + 31;
			Row grand = s.getRow(FIRST + 10);
			assertThat(grand.getCell(c + 6).getNumericCellValue()).isEqualTo(31992000d);
			Row total = s.getRow(FIRST + 11);
			assertThat(total.getCell(0).getStringCellValue()).isEqualTo("TOTAL");
			assertThat(total.getCell(c).getNumericCellValue()).isEqualTo(260d);
			assertThat(fill(total.getCell(c))).isEqualTo("FFD966");
			assertThat(text(s)).contains("Director Signature:").contains("Manager Signature:");
			XSSFSheet ot = wb.getSheet("Lembur Staf");
			assertThat(text(ot)).doesNotContain("TOTAL\n").contains("Director Signature:")
				.doesNotContain("Manager Signature:");
		}
	}

	private static String text(XSSFSheet s) {
		StringBuilder b = new StringBuilder();
		for (Row r : s) {
			for (Cell c : r) {
				if (c.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
					b.append(c.getStringCellValue()).append('\n');
				}
			}
		}
		return b.toString();
	}

	@Test
	void overtimeSheetListsOnlyEmployeesWithRate() throws Exception {
		ReportFinancial noRate = new ReportFinancial(new BigDecimal("40000"), null, null, BigDecimal.ZERO,
				BigDecimal.ZERO, new BigDecimal("1"), null, BigDecimal.ZERO);
		try (XSSFWorkbook wb = render(model(SEP, List.of(plain("ADA TARIF"), employee("TANPA TARIF", SEP, noRate, d -> d))))) {
			assertThat(text(wb.getSheet("Lembur Staf"))).contains("ADA TARIF").doesNotContain("TANPA TARIF");
			assertThat(text(wb.getSheet("Gaji Staf"))).contains("TANPA TARIF");
		}
	}

	@Test
	void dateColumnsWideEnoughForTwoDecimalHours() throws Exception {
		try (XSSFWorkbook wb = render(model(SEP, List.of(plain("A"))))) {
			assertThat(wb.getSheet("Lembur Staf").getColumnWidth(DATE_COL)).isGreaterThanOrEqualTo(4 * 256);
		}
	}

	@Test
	void printSetupFitsLandscapeA4() throws Exception {
		try (XSSFWorkbook wb = render(model(SEP, List.of(plain("A"))))) {
			for (String name : List.of("Gaji Staf", "Lembur Staf")) {
				XSSFSheet s = wb.getSheet(name);
				assertThat(s.getPrintSetup().getLandscape()).isTrue();
				assertThat(s.getPrintSetup().getPaperSizeEnum()).isEqualTo(PaperSize.A4_PAPER);
				assertThat(s.getFitToPage()).isTrue();
				assertThat(s.getPrintSetup().getFitWidth()).isEqualTo((short) 1);
				assertThat(s.isDisplayGridlines()).isFalse();
			}
		}
	}

}
