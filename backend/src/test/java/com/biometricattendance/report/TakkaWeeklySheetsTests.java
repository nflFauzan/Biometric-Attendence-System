package com.biometricattendance.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
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

/** Lembar AS-1 "Absensi" dan AS-2 "Lemburan" harus sesuai foto format klien. Semua nama di sini samaran. */
class TakkaWeeklySheetsTests {

	static final LocalDate SAT = LocalDate.of(2026, 9, 26);

	private static final int DAY = 2;

	private static final int JMLH = DAY + 7;

	private static final int ABSENSI_FIRST = 5;

	private static final int LEMBURAN_FIRST = 4;

	static List<LocalDate> week() {
		return SAT.datesUntil(SAT.plusDays(7)).toList();
	}

	/** {@code absent}: indeks hari tanpa V. {@code overtime}: jam per hari, "0" berarti kosong. */
	static ReportEmployee driver(String name, String number, String daily, String rate, String loan, String net,
			int[] absent, String... overtime) {
		List<ReportDay> days = new ArrayList<>();
		int counted = 0;
		BigDecimal hours = BigDecimal.ZERO;
		for (int i = 0; i < 7; i++) {
			int index = i;
			boolean out = Arrays.stream(absent).anyMatch(a -> a == index);
			BigDecimal ot = new BigDecimal(overtime[i]);
			days.add(new ReportDay(SAT.plusDays(i), "", out ? AttendanceStatus.TIDAK_HADIR : AttendanceStatus.LENGKAP,
					null, null, BigDecimal.ZERO, ot, List.of(), ""));
			counted += out ? 0 : 1;
			hours = hours.add(ot);
		}
		BigDecimal gross = new BigDecimal(daily).multiply(BigDecimal.valueOf(counted));
		BigDecimal lembur = new BigDecimal(rate).multiply(hours);
		return new ReportEmployee(1L, number, name, "Cabang", "Supir", days,
				new ReportTotals(counted, 0, absent.length, BigDecimal.ZERO, hours), new ReportFinancial(
						new BigDecimal(daily), gross, new BigDecimal(rate), lembur, new BigDecimal(loan),
						new BigDecimal(net), null, null));
	}

	/** Tiga baris yang angkanya tercatat di dokumen AS-IS (Supir 1, 3, dan 6). */
	static ReportModel model() {
		ReportEmployee a = driver("supir satu", "001", "100000", "14000", "0", "924000", new int[] {}, "2.5", "5",
				"1.5", "0", "5.5", "1", "0.5");
		ReportEmployee b = driver("supir tiga", "003", "100000", "14000", "100000", "612000", new int[] { 1 }, "2.5",
				"0", "1.5", "1", "1.5", "1", "0.5");
		ReportEmployee c = driver("kenek enam", "006", "110000", "14000", "150000", "1362000", new int[] {}, "8", "8",
				"8", "8", "5", "8", "8");
		return new ReportModel(ReportType.UPAH_MINGGUAN, "t", "p", List.of(), week(), List.of(a, b, c), List.of());
	}

	private XSSFWorkbook render(ReportModel model) throws Exception {
		return new XSSFWorkbook(new ByteArrayInputStream(new ExcelReportWriter().write(model)));
	}

	private static String text(XSSFSheet s, int row, int col) {
		Cell c = s.getRow(row).getCell(col);
		return c == null ? "" : c.getStringCellValue();
	}

	private static double num(XSSFSheet s, int row, int col) {
		return s.getRow(row).getCell(col).getNumericCellValue();
	}

	private static String fill(Cell cell) {
		var color = ((XSSFCellStyle) cell.getCellStyle()).getFillForegroundXSSFColor();
		return color == null ? null : color.getARGBHex().substring(2);
	}

	private static String fontColor(Cell cell) {
		var color = ((XSSFCellStyle) cell.getCellStyle()).getFont().getXSSFColor();
		return color == null ? null : color.getARGBHex().substring(2);
	}

	private static boolean merged(XSSFSheet s, int r1, int r2, int c1, int c2) {
		return s.getMergedRegions()
			.stream()
			.anyMatch(m -> m.getFirstRow() == r1 && m.getLastRow() == r2 && m.getFirstColumn() == c1
					&& m.getLastColumn() == c2);
	}

	private static int findRow(XSSFSheet s, int col, String value) {
		for (Row r : s) {
			Cell c = r.getCell(col);
			if (c != null && c.getCellType() == CellType.STRING && value.equals(c.getStringCellValue())) {
				return r.getRowNum();
			}
		}
		throw new AssertionError("Tidak ada baris dengan " + value);
	}

	@Test
	void workbookHasOnlyAbsensiAndLemburanSheets() throws Exception {
		try (XSSFWorkbook wb = render(model())) {
			assertThat(wb.getNumberOfSheets()).isEqualTo(2);
			assertThat(wb.getSheetName(0)).isEqualTo("Absensi");
			assertThat(wb.getSheetName(1)).isEqualTo("Lemburan");
		}
	}

	@Test
	void absensiKopMatchesPhoto() throws Exception {
		try (XSSFWorkbook wb = render(model())) {
			XSSFSheet s = wb.getSheet("Absensi");
			int title = JMLH - 1;
			assertThat(text(s, 0, title)).isEqualTo("ABSENSI");
			assertThat(text(s, 1, 0)).isEqualTo("PEKERJAAN");
			assertThat(text(s, 1, DAY)).isEqualTo(":");
			assertThat(text(s, 1, DAY + 1)).isEqualTo("Upah Supir dan Kenek");
			assertThat(text(s, 2, 0)).isEqualTo("LOKASI");
			assertThat(text(s, 2, DAY + 3)).isEqualTo("TOKO");
			assertThat(text(s, 1, JMLH + 3)).startsWith("PERIODE").endsWith(":");
			assertThat(text(s, 1, JMLH + 4)).isEqualTo("26 SEPTEMBER - 02 OKTOBER 2026");
			assertThat(text(s, 2, JMLH + 3)).startsWith("MINGGU").endsWith(":");
			assertThat(text(s, 2, JMLH + 4)).isEqualTo("KE - 1");
			assertThat(((XSSFCellStyle) s.getRow(0).getCell(title).getCellStyle()).getFont().getFontName())
				.isEqualTo("Arial");
		}
	}

	@Test
	void absensiHeaderHasEmptySubRowUnderDayLetters() throws Exception {
		try (XSSFWorkbook wb = render(model())) {
			XSSFSheet s = wb.getSheet("Absensi");
			String[] letters = { "S", "M", "S", "S", "R", "K", "J" };
			for (int i = 0; i < 7; i++) {
				assertThat(text(s, 3, DAY + i)).isEqualTo(letters[i]);
				assertThat(text(s, 4, DAY + i)).isEmpty();
			}
			assertThat(text(s, 3, 0)).isEqualTo("No");
			assertThat(text(s, 3, 1)).isEqualTo("NAMA");
			assertThat(text(s, 3, JMLH)).isEqualTo("JMLH\nHARI");
			assertThat(text(s, 3, JMLH + 1)).isEqualTo("UPAH\n(RP)");
			assertThat(text(s, 3, JMLH + 2)).isEqualTo("JUMLAH UPAH\n(RP)");
			assertThat(text(s, 3, JMLH + 3)).isEqualTo("LEMBUR\n(RP)");
			assertThat(text(s, 3, JMLH + 4)).isEqualTo("INPUT PIUTANG");
			assertThat(text(s, 3, JMLH + 5)).isEqualTo("SISA UPAH\n(RP)");
			assertThat(text(s, 3, JMLH + 6)).isEqualTo("INSENTIF");
			assertThat(merged(s, 3, 4, 1, 1)).isTrue();
			assertThat(merged(s, 3, 4, JMLH + 4, JMLH + 4)).isTrue();
		}
	}

	@Test
	void absensiRowsShowVOrPinkCellAndAmounts() throws Exception {
		try (XSSFWorkbook wb = render(model())) {
			XSSFSheet s = wb.getSheet("Absensi");
			int row = ABSENSI_FIRST + 1;
			assertThat(num(s, row, 0)).isEqualTo(2);
			assertThat(text(s, row, 1)).isEqualTo("SUPIR TIGA");
			assertThat(text(s, row, DAY)).isEqualTo("V");
			assertThat(fill(s.getRow(row).getCell(DAY))).isNull();
			assertThat(text(s, row, DAY + 1)).isEmpty();
			assertThat(fill(s.getRow(row).getCell(DAY + 1))).isEqualTo(TakkaFormat.WEEKLY_RED);
			assertThat(num(s, row, JMLH)).isEqualTo(6);
			assertThat(num(s, row, JMLH + 1)).isEqualTo(100000);
			assertThat(num(s, row, JMLH + 2)).isEqualTo(600000);
			assertThat(num(s, row, JMLH + 3)).isEqualTo(112000);
			assertThat(num(s, row, JMLH + 4)).isEqualTo(100000);
			assertThat(num(s, row, JMLH + 5)).isEqualTo(612000);
			assertThat(s.getRow(row).getCell(JMLH + 1).getCellStyle().getDataFormatString()).contains("\"Rp\"");
			// Tanpa piutang: sel kosong, bukan Rp 0. Insentif selalu kosong per baris.
			assertThat(s.getRow(ABSENSI_FIRST).getCell(JMLH + 4).getCellType()).isEqualTo(CellType.BLANK);
			assertThat(s.getRow(ABSENSI_FIRST).getCell(JMLH + 6).getCellType()).isEqualTo(CellType.BLANK);
		}
	}

	@Test
	void absensiTotalsBoxAndSignatureWithoutHandwrittenBranchNotes() throws Exception {
		ReportModel base = model();
		ReportModel withSummaries = new ReportModel(base.type(), "t", "p", List.of(), base.dates(), base.employees(),
				List.of(new ReportDtos.BranchSummary("Cash", new BigDecimal("1"))));
		try (XSSFWorkbook wb = render(withSummaries)) {
			XSSFSheet s = wb.getSheet("Absensi");
			int total = findRow(s, 0, "JUMLAH TOTAL");
			assertThat(total).isEqualTo(ABSENSI_FIRST + 3);
			assertThat(merged(s, total, total, 0, JMLH)).isTrue();
			assertThat(num(s, total, JMLH + 1)).isEqualTo(310000);
			assertThat(num(s, total, JMLH + 2)).isEqualTo(700000 + 600000 + 770000);
			assertThat(num(s, total, JMLH + 3)).isEqualTo(224000 + 112000 + 742000);
			assertThat(num(s, total, JMLH + 4)).isEqualTo(250000);
			assertThat(num(s, total, JMLH + 5)).isEqualTo(2898000);
			assertThat(num(s, total, JMLH + 6)).isZero();
			assertThat(s.getRow(total).getCell(JMLH + 6).getCellStyle().getDataFormatString()).contains("\"-\"");
			int box = findRow(s, JMLH + 4, "TOTAL UPAH");
			assertThat(num(s, box, JMLH + 5)).isEqualTo(2898000);
			assertThat(merged(s, box, box, JMLH + 5, JMLH + 6)).isTrue();
			int approve = findRow(s, 0, "Yang Menyetujui,");
			int director = findRow(s, 0, "Direktur Operasional");
			assertThat(director).isGreaterThan(approve + 2);
			for (Row r : s) {
				for (Cell c : r) {
					if (c.getCellType() == CellType.STRING) {
						assertThat(c.getStringCellValue()).doesNotContain("Cash").doesNotContain("KASBON");
					}
				}
			}
		}
	}

	@Test
	void lemburanMatchesPhoto() throws Exception {
		try (XSSFWorkbook wb = render(model())) {
			XSSFSheet s = wb.getSheet("Lemburan");
			assertThat(text(s, 0, 0)).isEqualTo("LEMBURAN");
			assertThat(merged(s, 0, 0, 0, JMLH + 2)).isTrue();
			assertThat(text(s, 1, 0)).isEqualTo("PEKERJAAN");
			assertThat(text(s, 2, 0)).isEqualTo("LOKASI");
			assertThat(text(s, 3, 1)).isEqualTo("NAMA");
			assertThat(text(s, 3, DAY + 4)).isEqualTo("R");
			assertThat(text(s, 3, JMLH)).isEqualTo("JMLH\nHARI");
			assertThat(text(s, 3, JMLH + 1)).isEqualTo("UANG LEMBUR");
			assertThat(text(s, 3, JMLH + 2)).isEqualTo("JUMLAH LEMBURAN");
			int row = LEMBURAN_FIRST;
			assertThat(num(s, row, DAY)).isEqualTo(2.5);
			assertThat(fontColor(s.getRow(row).getCell(DAY))).isEqualTo(TakkaFormat.WEEKLY_HOURS);
			assertThat(s.getRow(row).getCell(DAY + 3).getCellType()).isEqualTo(CellType.BLANK);
			assertThat(num(s, row, JMLH)).isEqualTo(16);
			assertThat(fontColor(s.getRow(row).getCell(JMLH))).isEqualTo(TakkaFormat.WEEKLY_HOURS);
			assertThat(num(s, row, JMLH + 1)).isEqualTo(14000);
			assertThat(num(s, row, JMLH + 2)).isEqualTo(224000);
			int label = findRow(s, 0, "JUMLAH TOTAL");
			assertThat(label).isEqualTo(LEMBURAN_FIRST + 4);
			assertThat(merged(s, label, label, 0, JMLH + 1)).isTrue();
			assertThat(merged(s, label - 1, label, JMLH + 2, JMLH + 2)).isTrue();
			assertThat(num(s, label - 1, JMLH + 2)).isEqualTo(224000 + 112000 + 742000);
			assertThat(s.getRow(label - 1).getCell(0).getCellType()).isEqualTo(CellType.BLANK);
		}
	}

	@Test
	void printSetupIsLandscapeA4WithoutGridlines() throws Exception {
		try (XSSFWorkbook wb = render(model())) {
			for (XSSFSheet s : List.of(wb.getSheet("Absensi"), wb.getSheet("Lemburan"))) {
				assertThat(s.getPrintSetup().getLandscape()).isTrue();
				assertThat(s.getPrintSetup().getPaperSize()).isEqualTo(PrintSetup.A4_PAPERSIZE);
				assertThat(s.isDisplayGridlines()).isFalse();
			}
		}
	}

	@Test
	void emptyReportKeepsKopAndTotals() throws Exception {
		ReportModel empty = new ReportModel(ReportType.UPAH_MINGGUAN, "t", "p", List.of(), week(), List.of(),
				List.of());
		try (XSSFWorkbook wb = render(empty)) {
			XSSFSheet s = wb.getSheet("Absensi");
			assertThat(text(s, ABSENSI_FIRST, 1)).isEqualTo(TakkaFormat.NO_DATA);
			assertThat(num(s, findRow(s, 0, "JUMLAH TOTAL"), JMLH + 5)).isZero();
		}
	}

	@Test
	void shortRangeKeepsPeriodCellsOutsideMergedKop() throws Exception {
		for (int days = 1; days <= 2; days++) {
			List<LocalDate> dates = SAT.datesUntil(SAT.plusDays(days)).toList();
			ReportModel shortRange = new ReportModel(ReportType.UPAH_MINGGUAN, "t", "p", List.of(), dates, List.of(),
					List.of());
			try (XSSFWorkbook wb = render(shortRange)) {
				XSSFSheet s = wb.getSheet("Absensi");
				int label = DAY + days + 3;
				for (CellRangeAddress m : s.getMergedRegions()) {
					for (int row = 1; row <= 2; row++) {
						assertThat(m.isInRange(row, label)).isFalse();
						assertThat(m.isInRange(row, label + 1)).isFalse();
					}
				}
			}
		}
	}

	@Test
	void periodAndWeekLabels() {
		assertThat(TakkaFormat.weeklyPeriod(week())).isEqualTo("26 SEPTEMBER - 02 OKTOBER 2026");
		assertThat(TakkaFormat.weeklyNumber(week())).isEqualTo("KE - 1");
		List<LocalDate> second = LocalDate.of(2026, 10, 3).datesUntil(LocalDate.of(2026, 10, 10)).toList();
		assertThat(TakkaFormat.weeklyPeriod(second)).isEqualTo("03 OKTOBER - 09 OKTOBER 2026");
		assertThat(TakkaFormat.weeklyNumber(second)).isEqualTo("KE - 2");
		List<LocalDate> newYear = LocalDate.of(2026, 12, 26).datesUntil(LocalDate.of(2027, 1, 2)).toList();
		assertThat(TakkaFormat.weeklyPeriod(newYear)).isEqualTo("26 DESEMBER 2026 - 01 JANUARI 2027");
	}

}
