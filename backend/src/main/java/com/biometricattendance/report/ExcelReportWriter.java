package com.biometricattendance.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.report.ReportDtos.ReportDay;
import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportModel;

/**
 * Merender {@link ReportModel} menjadi berkas .xlsx. Hanya merender: isi sel kisi ({@code mark}), nama hari, dan
 * angka jam sudah dihitung di model. Hari tanpa status (di luar masa kerja) dirender kosong berlatar abu-abu.
 */
@Component
public class ExcelReportWriter {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private static final String NO_DATA = "Tidak ada data pada rentang dan filter ini.";

	private static final int HEADER_ROW = 4;

	private static final int FIXED_COLS = 4;

	private static final int NARROW_WIDTH = 5 * 256;

	private static final int SCAN_WIDTH = 9 * 256;

	private static final byte[] RED = { (byte) 0xFF, (byte) 0x99, (byte) 0x99 };

	private static final byte[] GREY = { (byte) 0xD9, (byte) 0xD9, (byte) 0xD9 };

	private static final byte[] HEADER_FILL = { (byte) 0xDD, (byte) 0xE6, (byte) 0xF0 };

	public byte[] write(ReportModel model) {
		try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Styles st = new Styles(wb);
			switch (model.type()) {
				case REKAP_ABSENSI -> {
					rekapSheet(wb, st, model);
					detailSheet(wb, st, model);
				}
				case REKAP_LEMBUR -> overtimeSheet(wb, st, model);
				case DETAIL_ABSENSI -> scanSheet(wb, st, model);
			}
			wb.write(out);
			return out.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// ---- REKAP_ABSENSI ----

	private void rekapSheet(XSSFWorkbook wb, Styles st, ReportModel m) {
		XSSFSheet s = wb.createSheet("Rekap");
		List<LocalDate> dates = m.dates();
		int firstDate = FIXED_COLS;
		int totalsAt = firstDate + dates.size();
		String[] totals = { "Hari lengkap", "Izin", "Tidak hadir", "Jam kerja normal", "Lembur tervalidasi" };
		titleBlock(s, st, m);
		Row h = s.createRow(HEADER_ROW);
		header(h, st, 0, "No. karyawan");
		header(h, st, 1, "Nama");
		header(h, st, 2, "Kategori");
		header(h, st, 3, "Cabang");
		for (int i = 0; i < dates.size(); i++) {
			header(h, st, firstDate + i, DATE.format(dates.get(i)));
		}
		for (int i = 0; i < totals.length; i++) {
			header(h, st, totalsAt + i, totals[i]);
		}
		int r = HEADER_ROW + 1;
		for (ReportEmployee e : m.employees()) {
			Row row = s.createRow(r++);
			text(row, 0, e.employeeNumber(), st.text);
			text(row, 1, e.fullName(), st.text);
			text(row, 2, e.categoryName(), st.text);
			text(row, 3, e.branchName(), st.text);
			for (int i = 0; i < dates.size(); i++) {
				ReportDay d = i < e.days().size() ? e.days().get(i) : null;
				markCell(row, firstDate + i, d, st);
			}
			var t = e.totals();
			number(row, totalsAt, t.countingDays(), st.integer);
			number(row, totalsAt + 1, t.leaveDays(), st.integer);
			number(row, totalsAt + 2, t.absentDays(), st.integer);
			hours(row, totalsAt + 3, t.countingHours(), st.hours);
			hours(row, totalsAt + 4, t.validatedOvertimeHours(), st.hours);
		}
		if (m.employees().isEmpty()) {
			text(s.createRow(r), 0, NO_DATA, st.text);
		}
		s.setColumnWidth(0, 14 * 256);
		s.setColumnWidth(1, 28 * 256);
		s.setColumnWidth(2, 22 * 256);
		s.setColumnWidth(3, 16 * 256);
		for (int i = 0; i < dates.size(); i++) {
			s.setColumnWidth(firstDate + i, 11 * 256);
		}
		for (int i = 0; i < totals.length; i++) {
			s.setColumnWidth(totalsAt + i, 14 * 256);
		}
		s.createFreezePane(2, HEADER_ROW + 1);
	}

	private void markCell(Row row, int col, ReportDay d, Styles st) {
		XSSFCell c = (XSSFCell) row.createCell(col);
		if (d == null || d.status() == null) {
			c.setCellValue("");
			c.setCellStyle(st.grey);
			return;
		}
		c.setCellValue(d.mark() == null ? "" : d.mark());
		c.setCellStyle(switch (d.status()) {
			case TIDAK_HADIR -> st.red;
			case TIDAK_ADA_SCAN -> st.grey;
			default -> st.mark;
		});
	}

	// ---- Detail (lembar kedua REKAP_ABSENSI) ----

	private void detailSheet(XSSFWorkbook wb, Styles st, ReportModel m) {
		XSSFSheet s = wb.createSheet("Detail");
		titleBlock(s, st, m);
		String[] cols = { "Tanggal", "Hari", "Nama", "Kategori", "Jam masuk", "Jam keluar", "Jam kerja normal",
				"Lembur tervalidasi", "Status" };
		Row h = s.createRow(HEADER_ROW);
		for (int i = 0; i < cols.length; i++) {
			header(h, st, i, cols[i]);
		}
		int r = HEADER_ROW + 1;
		for (ReportEmployee e : m.employees()) {
			for (ReportDay d : e.days()) {
				Row row = s.createRow(r++);
				text(row, 0, DATE.format(d.date()), st.text);
				text(row, 1, d.dayName(), st.text);
				text(row, 2, e.fullName(), st.text);
				text(row, 3, e.categoryName(), st.text);
				text(row, 4, d.checkIn() == null ? "" : TIME.format(d.checkIn()), st.text);
				text(row, 5, d.checkOut() == null ? "" : TIME.format(d.checkOut()), st.text);
				hours(row, 6, d.regularHours(), st.hours);
				hours(row, 7, d.validatedOvertimeHours(), st.hours);
				text(row, 8, statusLabel(d.status()), st.text);
			}
		}
		if (r == HEADER_ROW + 1) {
			text(s.createRow(r), 0, NO_DATA, st.text);
		}
		int[] widths = { 12, 10, 28, 22, 11, 11, 16, 18, 16 };
		for (int i = 0; i < widths.length; i++) {
			s.setColumnWidth(i, widths[i] * 256);
		}
		s.createFreezePane(0, HEADER_ROW + 1);
	}

	private static String statusLabel(AttendanceStatus status) {
		if (status == null) {
			return "";
		}
		return switch (status) {
			case LENGKAP -> "Lengkap";
			case TIDAK_LENGKAP -> "Tidak lengkap";
			case TIDAK_HADIR -> "Tidak hadir";
			case TIDAK_ADA_SCAN -> "Tidak ada scan";
			case IZIN -> "Izin";
		};
	}

	// ---- REKAP_LEMBUR ----

	private void overtimeSheet(XSSFWorkbook wb, Styles st, ReportModel m) {
		XSSFSheet s = wb.createSheet("Rekap Lembur");
		List<LocalDate> dates = m.dates();
		int totalAt = FIXED_COLS + dates.size();
		titleBlock(s, st, m);
		Row h = s.createRow(HEADER_ROW);
		header(h, st, 0, "No. karyawan");
		header(h, st, 1, "Nama");
		header(h, st, 2, "Kategori");
		header(h, st, 3, "Cabang");
		for (int i = 0; i < dates.size(); i++) {
			header(h, st, FIXED_COLS + i, DATE.format(dates.get(i)));
		}
		header(h, st, totalAt, "Total lembur");
		int r = HEADER_ROW + 1;
		for (ReportEmployee e : m.employees()) {
			Row row = s.createRow(r++);
			text(row, 0, e.employeeNumber(), st.text);
			text(row, 1, e.fullName(), st.text);
			text(row, 2, e.categoryName(), st.text);
			text(row, 3, e.branchName(), st.text);
			for (int i = 0; i < dates.size() && i < e.days().size(); i++) {
				BigDecimal ot = e.days().get(i).validatedOvertimeHours();
				if (ot != null && ot.signum() != 0) {
					hours(row, FIXED_COLS + i, ot, st.hours);
				}
			}
			hours(row, totalAt, e.totals().validatedOvertimeHours(), st.hours);
		}
		if (m.employees().isEmpty()) {
			text(s.createRow(r), 0, NO_DATA, st.text);
		}
		s.setColumnWidth(0, 14 * 256);
		s.setColumnWidth(1, 28 * 256);
		s.setColumnWidth(2, 22 * 256);
		s.setColumnWidth(3, 16 * 256);
		for (int i = 0; i < dates.size(); i++) {
			s.setColumnWidth(FIXED_COLS + i, 11 * 256);
		}
		s.setColumnWidth(totalAt, 14 * 256);
		s.createFreezePane(2, HEADER_ROW + 1);
	}

	// ---- DETAIL_ABSENSI (gaya AS-4) ----

	private void scanSheet(XSSFWorkbook wb, Styles st, ReportModel m) {
		XSSFSheet s = wb.createSheet("Detail Absensi");
		List<LocalDate> dates = m.dates();
		titleBlock(s, st, m);
		Row h = s.createRow(HEADER_ROW);
		header(h, st, 0, "No. karyawan");
		header(h, st, 1, "Nama");
		header(h, st, 2, "Kategori");
		header(h, st, 3, "Cabang");
		for (int i = 0; i < dates.size(); i++) {
			header(h, st, FIXED_COLS + i, DATE.format(dates.get(i)));
		}
		int r = HEADER_ROW + 1;
		for (ReportEmployee e : m.employees()) {
			Row row = s.createRow(r++);
			text(row, 0, e.employeeNumber(), st.text);
			text(row, 1, e.fullName(), st.text);
			text(row, 2, e.categoryName(), st.text);
			text(row, 3, e.branchName(), st.text);
			for (int i = 0; i < dates.size() && i < e.days().size(); i++) {
				ReportDay d = e.days().get(i);
				List<LocalTime> scans = d.scanTimes() == null ? List.of() : d.scanTimes();
				XSSFCell c = (XSSFCell) row.createCell(FIXED_COLS + i);
				c.setCellValue(scans.stream().map(TIME::format).collect(Collectors.joining("\n")));
				c.setCellStyle(st.scans);
			}
		}
		if (m.employees().isEmpty()) {
			text(s.createRow(r), 0, NO_DATA, st.text);
		}
		s.setColumnWidth(0, 14 * 256);
		s.setColumnWidth(1, 28 * 256);
		s.setColumnWidth(2, 22 * 256);
		s.setColumnWidth(3, 16 * 256);
		for (int i = 0; i < dates.size(); i++) {
			s.setColumnWidth(FIXED_COLS + i, Math.max(SCAN_WIDTH, NARROW_WIDTH) + 2 * 256);
		}
		s.createFreezePane(2, HEADER_ROW + 1);
	}

	// ---- bantuan ----

	private void titleBlock(XSSFSheet s, Styles st, ReportModel m) {
		text(s.createRow(0), 0, m.title(), st.title);
		text(s.createRow(1), 0, "Periode: " + m.periodLabel(), st.text);
		List<String> filters = m.filterLabels() == null ? List.of() : m.filterLabels();
		text(s.createRow(2), 0, filters.isEmpty() ? "Filter: semua" : "Filter: " + String.join("; ", filters),
				st.text);
	}

	private static void header(Row row, Styles st, int col, String value) {
		XSSFCell c = (XSSFCell) row.createCell(col);
		c.setCellValue(value);
		c.setCellStyle(st.header);
	}

	private static void text(Row row, int col, String value, CellStyle style) {
		XSSFCell c = (XSSFCell) row.createCell(col);
		c.setCellValue(value == null ? "" : value);
		c.setCellStyle(style);
	}

	private static void number(Row row, int col, double value, CellStyle style) {
		XSSFCell c = (XSSFCell) row.createCell(col);
		c.setCellValue(value);
		c.setCellStyle(style);
	}

	private static void hours(Row row, int col, BigDecimal value, CellStyle style) {
		number(row, col, value == null ? 0d : value.doubleValue(), style);
	}

	private static final class Styles {

		final XSSFCellStyle title;

		final XSSFCellStyle text;

		final XSSFCellStyle header;

		final XSSFCellStyle mark;

		final XSSFCellStyle red;

		final XSSFCellStyle grey;

		final XSSFCellStyle hours;

		final XSSFCellStyle integer;

		final XSSFCellStyle scans;

		Styles(XSSFWorkbook wb) {
			XSSFFont bold = wb.createFont();
			bold.setBold(true);
			XSSFFont big = wb.createFont();
			big.setBold(true);
			big.setFontHeightInPoints((short) 14);

			title = wb.createCellStyle();
			title.setFont(big);

			text = wb.createCellStyle();

			header = bordered(wb);
			header.setFont(bold);
			header.setAlignment(HorizontalAlignment.CENTER);
			fill(header, HEADER_FILL);

			mark = bordered(wb);
			mark.setAlignment(HorizontalAlignment.CENTER);

			red = bordered(wb);
			red.setAlignment(HorizontalAlignment.CENTER);
			fill(red, RED);

			grey = bordered(wb);
			grey.setAlignment(HorizontalAlignment.CENTER);
			fill(grey, GREY);

			hours = bordered(wb);
			hours.setDataFormat(wb.createDataFormat().getFormat("0.00"));

			integer = bordered(wb);
			integer.setDataFormat(wb.createDataFormat().getFormat("0"));

			scans = bordered(wb);
			scans.setWrapText(true);
			scans.setVerticalAlignment(VerticalAlignment.TOP);
		}

		private static XSSFCellStyle bordered(XSSFWorkbook wb) {
			XSSFCellStyle style = wb.createCellStyle();
			style.setBorderTop(BorderStyle.THIN);
			style.setBorderBottom(BorderStyle.THIN);
			style.setBorderLeft(BorderStyle.THIN);
			style.setBorderRight(BorderStyle.THIN);
			return style;
		}

		private static void fill(XSSFCellStyle style, byte[] rgb) {
			style.setFillForegroundColor(new XSSFColor(rgb, null));
			style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		}

	}

}
