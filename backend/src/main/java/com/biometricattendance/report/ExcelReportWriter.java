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
import com.biometricattendance.report.ReportDtos.BranchSummary;
import com.biometricattendance.report.ReportDtos.ReportDay;
import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportModel;

/**
 * Merender {@link ReportModel} menjadi berkas .xlsx multi-sheet. Mendukung format standar presensi
 * serta format dokumen fisik klien TAKKA STEEL (AS-1 sampai AS-7).
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

	private static final byte[] TOTAL_FILL = { (byte) 0xEB, (byte) 0xF1, (byte) 0xE5 };

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
				case UPAH_MINGGUAN -> {
					weeklyWageSheet(wb, st, model);
					weeklyOvertimeSheet(wb, st, model);
				}
				case GAJI_BULANAN -> {
					monthlySalarySheet(wb, st, model);
					monthlyOvertimeSheet(wb, st, model);
					storeSubmissionSheet(wb, st, model);
				}
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

	private static String statusLabel(AttendanceStatus s) {
		if (s == null) {
			return "";
		}
		return switch (s) {
			case LENGKAP -> "Lengkap";
			case TIDAK_LENGKAP -> "Tidak lengkap";
			case IZIN -> "Izin";
			case TIDAK_HADIR -> "Tidak hadir";
			case TIDAK_ADA_SCAN -> "Tidak ada scan";
		};
	}

	// ---- REKAP_LEMBUR ----

	private void overtimeSheet(XSSFWorkbook wb, Styles st, ReportModel m) {
		XSSFSheet s = wb.createSheet("Rekap Lembur");
		List<LocalDate> dates = m.dates();
		int firstDate = FIXED_COLS;
		int totalCol = firstDate + dates.size();
		titleBlock(s, st, m);
		Row h = s.createRow(HEADER_ROW);
		header(h, st, 0, "No. karyawan");
		header(h, st, 1, "Nama");
		header(h, st, 2, "Kategori");
		header(h, st, 3, "Cabang");
		for (int i = 0; i < dates.size(); i++) {
			header(h, st, firstDate + i, DATE.format(dates.get(i)));
		}
		header(h, st, totalCol, "Total lembur");
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
					hours(row, firstDate + i, ot, st.hours);
				}
			}
			hours(row, totalCol, e.totals().validatedOvertimeHours(), st.hours);
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
		s.setColumnWidth(totalCol, 18 * 256);
		s.createFreezePane(2, HEADER_ROW + 1);
	}

	// ---- DETAIL_ABSENSI ----

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

	// ---- UPAH_MINGGUAN (Format AS-1 & AS-2) ----

	private void weeklyWageSheet(XSSFWorkbook wb, Styles st, ReportModel m) {
		XSSFSheet s = wb.createSheet("Rekap Upah");
		List<LocalDate> dates = m.dates();
		int firstDate = 4;
		int totalsAt = firstDate + dates.size();

		titleBlockCustom(s, st, "DAFTAR UPAH KERJA MINGGUAN", m);

		Row h = s.createRow(HEADER_ROW);
		header(h, st, 0, "NO");
		header(h, st, 1, "NAMA");
		header(h, st, 2, "JABATAN");
		header(h, st, 3, "CABANG");
		for (int i = 0; i < dates.size(); i++) {
			LocalDate d = dates.get(i);
			String shortDay = switch (d.getDayOfWeek()) {
				case SATURDAY -> "Sab";
				case SUNDAY -> "Min";
				case MONDAY -> "Sen";
				case TUESDAY -> "Sel";
				case WEDNESDAY -> "Rab";
				case THURSDAY -> "Kam";
				case FRIDAY -> "Jum";
			};
			header(h, st, firstDate + i, shortDay + "\n" + d.getDayOfMonth());
		}
		header(h, st, totalsAt, "HARI");
		header(h, st, totalsAt + 1, "UPAH HARIAN");
		header(h, st, totalsAt + 2, "TOTAL UPAH");
		header(h, st, totalsAt + 3, "LEMBUR");
		header(h, st, totalsAt + 4, "KASBON");
		header(h, st, totalsAt + 5, "SISA UPAH");
		header(h, st, totalsAt + 6, "KETERANGAN");

		int r = HEADER_ROW + 1;
		int idx = 1;
		int sumDays = 0;
		BigDecimal sumGrossDaily = BigDecimal.ZERO;
		BigDecimal sumOvertime = BigDecimal.ZERO;
		BigDecimal sumLoan = BigDecimal.ZERO;
		BigDecimal sumNetWage = BigDecimal.ZERO;

		for (ReportEmployee e : m.employees()) {
			Row row = s.createRow(r++);
			number(row, 0, idx++, st.integer);
			text(row, 1, e.fullName(), st.text);
			text(row, 2, e.categoryName(), st.text);
			text(row, 3, e.branchName(), st.text);
			for (int i = 0; i < dates.size(); i++) {
				ReportDay d = i < e.days().size() ? e.days().get(i) : null;
				markCell(row, firstDate + i, d, st);
			}

			int cDays = e.totals().countingDays();
			sumDays += cDays;
			number(row, totalsAt, cDays, st.integer);

			var fin = e.financial();
			BigDecimal dailyWage = fin != null && fin.dailyWage() != null ? fin.dailyWage() : BigDecimal.ZERO;
			BigDecimal grossDaily = fin != null && fin.grossDailyWage() != null ? fin.grossDailyWage() : BigDecimal.ZERO;
			BigDecimal overtimeWage = fin != null && fin.overtimeWage() != null ? fin.overtimeWage() : BigDecimal.ZERO;
			BigDecimal loanDeduction = fin != null && fin.loanDeduction() != null ? fin.loanDeduction() : BigDecimal.ZERO;
			BigDecimal netWage = fin != null && fin.netWage() != null ? fin.netWage() : BigDecimal.ZERO;

			sumGrossDaily = sumGrossDaily.add(grossDaily);
			sumOvertime = sumOvertime.add(overtimeWage);
			sumLoan = sumLoan.add(loanDeduction);
			sumNetWage = sumNetWage.add(netWage);

			rupiah(row, totalsAt + 1, dailyWage, st.currency);
			rupiah(row, totalsAt + 2, grossDaily, st.currency);
			rupiah(row, totalsAt + 3, overtimeWage, st.currency);
			rupiah(row, totalsAt + 4, loanDeduction, st.currency);
			rupiah(row, totalsAt + 5, netWage, st.boldCurrency);
			text(row, totalsAt + 6, "", st.text);
		}

		if (m.employees().isEmpty()) {
			text(s.createRow(r++), 0, NO_DATA, st.text);
		}

		// Baris Grand Total
		Row totRow = s.createRow(r++);
		text(totRow, 0, "TOTAL", st.totalLabel);
		text(totRow, 1, "", st.totalLabel);
		text(totRow, 2, "", st.totalLabel);
		text(totRow, 3, "", st.totalLabel);
		for (int i = 0; i < dates.size(); i++) {
			text(totRow, firstDate + i, "", st.totalLabel);
		}
		number(totRow, totalsAt, sumDays, st.boldInteger);
		text(totRow, totalsAt + 1, "", st.totalLabel);
		rupiah(totRow, totalsAt + 2, sumGrossDaily, st.boldCurrency);
		rupiah(totRow, totalsAt + 3, sumOvertime, st.boldCurrency);
		rupiah(totRow, totalsAt + 4, sumLoan, st.boldCurrency);
		rupiah(totRow, totalsAt + 5, sumNetWage, st.boldCurrency);
		text(totRow, totalsAt + 6, "", st.totalLabel);

		// Ringkasan Cabang (Format AS-1)
		r += 2;
		if (m.branchSummaries() != null && !m.branchSummaries().isEmpty()) {
			Row bHead = s.createRow(r++);
			header(bHead, st, 1, "CABANG");
			header(bHead, st, 2, "TOTAL UPAH");
			for (BranchSummary bs : m.branchSummaries()) {
				Row bRow = s.createRow(r++);
				text(bRow, 1, bs.branchName(), st.boldText);
				rupiah(bRow, 2, bs.totalAmount(), st.boldCurrency);
			}
		}

		// Kolom Tanda Tangan
		r += 2;
		Row s1 = s.createRow(r++);
		text(s1, 1, "Mengetahui,", st.text);
		Row s2 = s.createRow(r++);
		text(s2, 1, "Direktur Operasional", st.boldText);
		r += 3;
		Row s3 = s.createRow(r++);
		text(s3, 1, "( ................................ )", st.text);

		s.setColumnWidth(0, 6 * 256);
		s.setColumnWidth(1, 26 * 256);
		s.setColumnWidth(2, 20 * 256);
		s.setColumnWidth(3, 16 * 256);
		for (int i = 0; i < dates.size(); i++) {
			s.setColumnWidth(firstDate + i, 8 * 256);
		}
		s.setColumnWidth(totalsAt, 8 * 256);
		s.setColumnWidth(totalsAt + 1, 14 * 256);
		s.setColumnWidth(totalsAt + 2, 16 * 256);
		s.setColumnWidth(totalsAt + 3, 14 * 256);
		s.setColumnWidth(totalsAt + 4, 14 * 256);
		s.setColumnWidth(totalsAt + 5, 16 * 256);
		s.setColumnWidth(totalsAt + 6, 16 * 256);
		s.createFreezePane(2, HEADER_ROW + 1);
	}

	private void weeklyOvertimeSheet(XSSFWorkbook wb, Styles st, ReportModel m) {
		XSSFSheet s = wb.createSheet("Rekap Lemburan");
		List<LocalDate> dates = m.dates();
		int firstDate = 3;
		int totalsAt = firstDate + dates.size();

		titleBlockCustom(s, st, "REKAP LEMBURAN SUPIR & KENEK", m);

		Row h = s.createRow(HEADER_ROW);
		header(h, st, 0, "NO");
		header(h, st, 1, "NAMA");
		header(h, st, 2, "CABANG");
		for (int i = 0; i < dates.size(); i++) {
			LocalDate d = dates.get(i);
			header(h, st, firstDate + i, DATE.format(d));
		}
		header(h, st, totalsAt, "TOTAL JAM");
		header(h, st, totalsAt + 1, "TARIF / JAM");
		header(h, st, totalsAt + 2, "TOTAL LEMBUR");
		header(h, st, totalsAt + 3, "TTD");

		int r = HEADER_ROW + 1;
		int idx = 1;
		BigDecimal sumHours = BigDecimal.ZERO;
		BigDecimal sumOvertimeWage = BigDecimal.ZERO;

		for (ReportEmployee e : m.employees()) {
			Row row = s.createRow(r++);
			number(row, 0, idx++, st.integer);
			text(row, 1, e.fullName(), st.text);
			text(row, 2, e.branchName(), st.text);
			for (int i = 0; i < dates.size(); i++) {
				ReportDay d = i < e.days().size() ? e.days().get(i) : null;
				BigDecimal ot = d == null ? null : d.validatedOvertimeHours();
				if (ot == null || ot.compareTo(BigDecimal.ZERO) == 0) {
					text(row, firstDate + i, "", st.text);
				}
				else {
					hours(row, firstDate + i, ot, st.hours);
				}
			}
			BigDecimal empHours = e.totals().validatedOvertimeHours();
			sumHours = sumHours.add(empHours);
			hours(row, totalsAt, empHours, st.hours);

			var fin = e.financial();
			BigDecimal rate = fin != null && fin.overtimeRate() != null ? fin.overtimeRate() : BigDecimal.valueOf(14000);
			BigDecimal wage = fin != null && fin.overtimeWage() != null ? fin.overtimeWage() : BigDecimal.ZERO;
			sumOvertimeWage = sumOvertimeWage.add(wage);

			rupiah(row, totalsAt + 1, rate, st.currency);
			rupiah(row, totalsAt + 2, wage, st.currency);
			text(row, totalsAt + 3, "", st.text);
		}

		if (m.employees().isEmpty()) {
			text(s.createRow(r++), 0, NO_DATA, st.text);
		}

		// Total Row
		Row totRow = s.createRow(r++);
		text(totRow, 0, "TOTAL", st.totalLabel);
		text(totRow, 1, "", st.totalLabel);
		text(totRow, 2, "", st.totalLabel);
		for (int i = 0; i < dates.size(); i++) {
			text(totRow, firstDate + i, "", st.totalLabel);
		}
		hours(totRow, totalsAt, sumHours, st.hours);
		text(totRow, totalsAt + 1, "", st.totalLabel);
		rupiah(totRow, totalsAt + 2, sumOvertimeWage, st.boldCurrency);
		text(totRow, totalsAt + 3, "", st.totalLabel);

		s.setColumnWidth(0, 6 * 256);
		s.setColumnWidth(1, 26 * 256);
		s.setColumnWidth(2, 18 * 256);
		for (int i = 0; i < dates.size(); i++) {
			s.setColumnWidth(firstDate + i, 11 * 256);
		}
		s.setColumnWidth(totalsAt, 14 * 256);
		s.setColumnWidth(totalsAt + 1, 14 * 256);
		s.setColumnWidth(totalsAt + 2, 16 * 256);
		s.setColumnWidth(totalsAt + 3, 14 * 256);
		s.createFreezePane(2, HEADER_ROW + 1);
	}

	// ---- GAJI_BULANAN (Format AS-5, AS-6 & AS-7) ----

	private void monthlySalarySheet(XSSFWorkbook wb, Styles st, ReportModel m) {
		XSSFSheet s = wb.createSheet("Gaji Staf");
		titleBlockTakka(s, st, "DAFTAR GAJI STAF KANTOR", m);

		Row h = s.createRow(HEADER_ROW);
		header(h, st, 0, "NO");
		header(h, st, 1, "NAMA");
		header(h, st, 2, "JABATAN");
		header(h, st, 3, "CABANG");
		header(h, st, 4, "TOTAL DAYS");
		header(h, st, 5, "WAGES");
		header(h, st, 6, "ONSITE");
		header(h, st, 7, "GAJI POKOK");
		header(h, st, 8, "LEMBUR");
		header(h, st, 9, "PIUTANG");
		header(h, st, 10, "TOTAL GAJI");
		header(h, st, 11, "TTD / KETERANGAN");

		int r = HEADER_ROW + 1;
		int idx = 1;
		int sumDays = 0;
		BigDecimal sumOnsite = BigDecimal.ZERO;
		BigDecimal sumBase = BigDecimal.ZERO;
		BigDecimal sumOvertime = BigDecimal.ZERO;
		BigDecimal sumLoan = BigDecimal.ZERO;
		BigDecimal sumNetWage = BigDecimal.ZERO;

		for (ReportEmployee e : m.employees()) {
			Row row = s.createRow(r++);
			number(row, 0, idx++, st.integer);
			text(row, 1, e.fullName(), st.text);
			text(row, 2, e.categoryName(), st.text);
			text(row, 3, e.branchName(), st.text);

			int days = e.totals().countingDays();
			sumDays += days;
			number(row, 4, days, st.integer);

			var fin = e.financial();
			BigDecimal wages = fin != null && fin.dailyWage() != null ? fin.dailyWage() : BigDecimal.valueOf(40000);
			BigDecimal onsite = fin != null && fin.onsiteWage() != null ? fin.onsiteWage() : BigDecimal.ZERO;
			BigDecimal base = fin != null && fin.baseSalary() != null ? fin.baseSalary() : BigDecimal.ZERO;
			BigDecimal overtime = fin != null && fin.overtimeWage() != null ? fin.overtimeWage() : BigDecimal.ZERO;
			BigDecimal loan = fin != null && fin.loanDeduction() != null ? fin.loanDeduction() : BigDecimal.ZERO;
			BigDecimal netWage = fin != null && fin.netWage() != null ? fin.netWage() : BigDecimal.ZERO;

			sumOnsite = sumOnsite.add(onsite);
			sumBase = sumBase.add(base);
			sumOvertime = sumOvertime.add(overtime);
			sumLoan = sumLoan.add(loan);
			sumNetWage = sumNetWage.add(netWage);

			rupiah(row, 5, wages, st.currency);
			rupiah(row, 6, onsite, st.currency);
			rupiah(row, 7, base, st.currency);
			rupiah(row, 8, overtime, st.currency);
			rupiah(row, 9, loan, st.currency);
			rupiah(row, 10, netWage, st.boldCurrency);
			text(row, 11, "", st.text);
		}

		if (m.employees().isEmpty()) {
			text(s.createRow(r++), 0, NO_DATA, st.text);
		}

		// Total Row
		Row totRow = s.createRow(r++);
		text(totRow, 0, "TOTAL", st.totalLabel);
		text(totRow, 1, "", st.totalLabel);
		text(totRow, 2, "", st.totalLabel);
		text(totRow, 3, "", st.totalLabel);
		number(totRow, 4, sumDays, st.boldInteger);
		text(totRow, 5, "", st.totalLabel);
		rupiah(totRow, 6, sumOnsite, st.boldCurrency);
		rupiah(totRow, 7, sumBase, st.boldCurrency);
		rupiah(totRow, 8, sumOvertime, st.boldCurrency);
		rupiah(totRow, 9, sumLoan, st.boldCurrency);
		rupiah(totRow, 10, sumNetWage, st.boldCurrency);
		text(totRow, 11, "", st.totalLabel);

		// Tanda Tangan Dua Kolom (Accounting & Finance, Director)
		r += 3;
		Row sigHead = s.createRow(r++);
		text(sigHead, 1, "Dibuat Oleh,", st.text);
		text(sigHead, 7, "Disetujui Oleh,", st.text);

		Row sigRole = s.createRow(r++);
		text(sigRole, 1, "Accounting & Finance", st.boldText);
		text(sigRole, 7, "Director", st.boldText);

		r += 3;
		Row sigName = s.createRow(r++);
		text(sigName, 1, "( ................................ )", st.text);
		text(sigName, 7, "( ................................ )", st.text);

		s.setColumnWidth(0, 6 * 256);
		s.setColumnWidth(1, 26 * 256);
		s.setColumnWidth(2, 20 * 256);
		s.setColumnWidth(3, 18 * 256);
		s.setColumnWidth(4, 12 * 256);
		s.setColumnWidth(5, 14 * 256);
		s.setColumnWidth(6, 16 * 256);
		s.setColumnWidth(7, 16 * 256);
		s.setColumnWidth(8, 14 * 256);
		s.setColumnWidth(9, 14 * 256);
		s.setColumnWidth(10, 18 * 256);
		s.setColumnWidth(11, 18 * 256);
		s.createFreezePane(2, HEADER_ROW + 1);
	}

	private void monthlyOvertimeSheet(XSSFWorkbook wb, Styles st, ReportModel m) {
		XSSFSheet s = wb.createSheet("Lembur Staf");
		List<LocalDate> dates = m.dates();
		int firstDate = 4;
		int totalsAt = firstDate + dates.size();

		titleBlockTakka(s, st, "REKAP LEMBUR STAF KANTOR", m);

		Row h = s.createRow(HEADER_ROW);
		header(h, st, 0, "NO");
		header(h, st, 1, "NAMA");
		header(h, st, 2, "JABATAN");
		header(h, st, 3, "CABANG");
		for (int i = 0; i < dates.size(); i++) {
			LocalDate d = dates.get(i);
			header(h, st, firstDate + i, String.valueOf(d.getDayOfMonth()));
		}
		header(h, st, totalsAt, "TOTAL JAM");
		header(h, st, totalsAt + 1, "TARIF / JAM");
		header(h, st, totalsAt + 2, "TOTAL LEMBUR");
		header(h, st, totalsAt + 3, "TTD");

		int r = HEADER_ROW + 1;
		int idx = 1;
		BigDecimal sumHours = BigDecimal.ZERO;
		BigDecimal sumOvertimeWage = BigDecimal.ZERO;

		for (ReportEmployee e : m.employees()) {
			Row row = s.createRow(r++);
			number(row, 0, idx++, st.integer);
			text(row, 1, e.fullName(), st.text);
			text(row, 2, e.categoryName(), st.text);
			text(row, 3, e.branchName(), st.text);
			for (int i = 0; i < dates.size(); i++) {
				ReportDay d = i < e.days().size() ? e.days().get(i) : null;
				BigDecimal ot = d == null ? null : d.validatedOvertimeHours();
				if (ot == null || ot.compareTo(BigDecimal.ZERO) == 0) {
					text(row, firstDate + i, "", st.text);
				}
				else {
					hours(row, firstDate + i, ot, st.hours);
				}
			}
			BigDecimal empHours = e.totals().validatedOvertimeHours();
			sumHours = sumHours.add(empHours);
			hours(row, totalsAt, empHours, st.hours);

			var fin = e.financial();
			BigDecimal rate = fin != null && fin.overtimeRate() != null ? fin.overtimeRate() : BigDecimal.valueOf(14000);
			BigDecimal wage = fin != null && fin.overtimeWage() != null ? fin.overtimeWage() : BigDecimal.ZERO;
			sumOvertimeWage = sumOvertimeWage.add(wage);

			rupiah(row, totalsAt + 1, rate, st.currency);
			rupiah(row, totalsAt + 2, wage, st.currency);
			text(row, totalsAt + 3, "", st.text);
		}

		if (m.employees().isEmpty()) {
			text(s.createRow(r++), 0, NO_DATA, st.text);
		}

		// Total Row
		Row totRow = s.createRow(r++);
		text(totRow, 0, "TOTAL", st.totalLabel);
		text(totRow, 1, "", st.totalLabel);
		text(totRow, 2, "", st.totalLabel);
		text(totRow, 3, "", st.totalLabel);
		for (int i = 0; i < dates.size(); i++) {
			text(totRow, firstDate + i, "", st.totalLabel);
		}
		hours(totRow, totalsAt, sumHours, st.hours);
		text(totRow, totalsAt + 1, "", st.totalLabel);
		rupiah(totRow, totalsAt + 2, sumOvertimeWage, st.boldCurrency);
		text(totRow, totalsAt + 3, "", st.totalLabel);

		s.setColumnWidth(0, 6 * 256);
		s.setColumnWidth(1, 26 * 256);
		s.setColumnWidth(2, 20 * 256);
		s.setColumnWidth(3, 16 * 256);
		for (int i = 0; i < dates.size(); i++) {
			s.setColumnWidth(firstDate + i, 5 * 256);
		}
		s.setColumnWidth(totalsAt, 14 * 256);
		s.setColumnWidth(totalsAt + 1, 14 * 256);
		s.setColumnWidth(totalsAt + 2, 16 * 256);
		s.setColumnWidth(totalsAt + 3, 14 * 256);
		s.createFreezePane(2, HEADER_ROW + 1);
	}

	private void storeSubmissionSheet(XSSFWorkbook wb, Styles st, ReportModel m) {
		XSSFSheet s = wb.createSheet("Pengajuan Kas Toko");
		titleBlockTakka(s, st, "PENGAJUAN KAS TOKO / TRANSFER GAJI STAF", m);

		Row h = s.createRow(HEADER_ROW);
		header(h, st, 0, "NO");
		header(h, st, 1, "NAMA KARYAWAN");
		header(h, st, 2, "CABANG");
		header(h, st, 3, "NOMINAL GAJI");
		header(h, st, 4, "BIAYA TRANSFER");
		header(h, st, 5, "TOTAL TRANSFER");
		header(h, st, 6, "KETERANGAN");

		int r = HEADER_ROW + 1;
		int idx = 1;
		BigDecimal sumWage = BigDecimal.ZERO;
		BigDecimal sumAdmin = BigDecimal.ZERO;
		BigDecimal sumTotal = BigDecimal.ZERO;
		BigDecimal adminFee = BigDecimal.valueOf(2500);

		for (ReportEmployee e : m.employees()) {
			Row row = s.createRow(r++);
			number(row, 0, idx++, st.integer);
			text(row, 1, e.fullName(), st.text);
			text(row, 2, e.branchName(), st.text);

			var fin = e.financial();
			BigDecimal net = fin != null && fin.netWage() != null ? fin.netWage() : BigDecimal.ZERO;
			BigDecimal transfer = net.add(adminFee);

			sumWage = sumWage.add(net);
			sumAdmin = sumAdmin.add(adminFee);
			sumTotal = sumTotal.add(transfer);

			rupiah(row, 3, net, st.currency);
			rupiah(row, 4, adminFee, st.currency);
			rupiah(row, 5, transfer, st.boldCurrency);
			text(row, 6, "BCA/Transfer", st.text);
		}

		if (m.employees().isEmpty()) {
			text(s.createRow(r++), 0, NO_DATA, st.text);
		}

		// Total Row
		Row totRow = s.createRow(r++);
		text(totRow, 0, "TOTAL", st.totalLabel);
		text(totRow, 1, "", st.totalLabel);
		text(totRow, 2, "", st.totalLabel);
		rupiah(totRow, 3, sumWage, st.boldCurrency);
		rupiah(totRow, 4, sumAdmin, st.boldCurrency);
		rupiah(totRow, 5, sumTotal, st.boldCurrency);
		text(totRow, 6, "", st.totalLabel);

		// Tanda Tangan
		r += 3;
		Row sigHead = s.createRow(r++);
		text(sigHead, 1, "Diajukan Oleh,", st.text);
		text(sigHead, 4, "Disetujui Oleh,", st.text);

		Row sigRole = s.createRow(r++);
		text(sigRole, 1, "Kasir / Toko", st.boldText);
		text(sigRole, 4, "Finance & Director", st.boldText);

		r += 3;
		Row sigName = s.createRow(r++);
		text(sigName, 1, "( ................................ )", st.text);
		text(sigName, 4, "( ................................ )", st.text);

		s.setColumnWidth(0, 6 * 256);
		s.setColumnWidth(1, 28 * 256);
		s.setColumnWidth(2, 20 * 256);
		s.setColumnWidth(3, 18 * 256);
		s.setColumnWidth(4, 16 * 256);
		s.setColumnWidth(5, 18 * 256);
		s.setColumnWidth(6, 20 * 256);
	}

	// ---- bantuan ----

	private void titleBlock(XSSFSheet s, Styles st, ReportModel m) {
		text(s.createRow(0), 0, m.title(), st.title);
		text(s.createRow(1), 0, "Periode: " + m.periodLabel(), st.text);
		List<String> filters = m.filterLabels() == null ? List.of() : m.filterLabels();
		text(s.createRow(2), 0, filters.isEmpty() ? "Filter: semua" : "Filter: " + String.join("; ", filters),
				st.text);
	}

	private void titleBlockCustom(XSSFSheet s, Styles st, String title, ReportModel m) {
		text(s.createRow(0), 0, title, st.title);
		text(s.createRow(1), 0, "Periode: " + m.periodLabel(), st.text);
		List<String> filters = m.filterLabels() == null ? List.of() : m.filterLabels();
		text(s.createRow(2), 0, filters.isEmpty() ? "Filter: semua" : "Filter: " + String.join("; ", filters),
				st.text);
	}

	private void titleBlockTakka(XSSFSheet s, Styles st, String title, ReportModel m) {
		text(s.createRow(0), 0, "TAKKA STEEL", st.kopTitle);
		text(s.createRow(1), 0, title, st.title);
		text(s.createRow(2), 0, "Periode: " + m.periodLabel(), st.text);
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

	private static void rupiah(Row row, int col, BigDecimal value, CellStyle style) {
		number(row, col, value == null ? 0d : value.doubleValue(), style);
	}

	private static final class Styles {

		final XSSFCellStyle kopTitle;

		final XSSFCellStyle title;

		final XSSFCellStyle text;

		final XSSFCellStyle boldText;

		final XSSFCellStyle header;

		final XSSFCellStyle totalLabel;

		final XSSFCellStyle mark;

		final XSSFCellStyle red;

		final XSSFCellStyle grey;

		final XSSFCellStyle hours;

		final XSSFCellStyle integer;

		final XSSFCellStyle boldInteger;

		final XSSFCellStyle currency;

		final XSSFCellStyle boldCurrency;

		final XSSFCellStyle scans;

		Styles(XSSFWorkbook wb) {
			XSSFFont bold = wb.createFont();
			bold.setBold(true);

			XSSFFont big = wb.createFont();
			big.setBold(true);
			big.setFontHeightInPoints((short) 14);

			XSSFFont kopFont = wb.createFont();
			kopFont.setBold(true);
			kopFont.setFontHeightInPoints((short) 16);

			kopTitle = wb.createCellStyle();
			kopTitle.setFont(kopFont);

			title = wb.createCellStyle();
			title.setFont(big);

			text = wb.createCellStyle();

			boldText = bordered(wb);
			boldText.setFont(bold);

			header = bordered(wb);
			header.setFont(bold);
			header.setAlignment(HorizontalAlignment.CENTER);
			header.setVerticalAlignment(VerticalAlignment.CENTER);
			fill(header, HEADER_FILL);

			totalLabel = bordered(wb);
			totalLabel.setFont(bold);
			totalLabel.setAlignment(HorizontalAlignment.CENTER);
			fill(totalLabel, TOTAL_FILL);

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

			boldInteger = bordered(wb);
			boldInteger.setFont(bold);
			boldInteger.setAlignment(HorizontalAlignment.CENTER);
			boldInteger.setDataFormat(wb.createDataFormat().getFormat("0"));
			fill(boldInteger, TOTAL_FILL);

			currency = bordered(wb);
			currency.setDataFormat(wb.createDataFormat().getFormat("#,##0"));

			boldCurrency = bordered(wb);
			boldCurrency.setFont(bold);
			boldCurrency.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
			fill(boldCurrency, TOTAL_FILL);

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
