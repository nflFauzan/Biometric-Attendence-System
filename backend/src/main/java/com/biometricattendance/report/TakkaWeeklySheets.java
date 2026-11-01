package com.biometricattendance.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportFinancial;
import com.biometricattendance.report.ReportDtos.ReportModel;

/** Lembar Excel format klien: AS-1 "Absensi" dan AS-2 "Lemburan" supir dan kenek (K-143). */
final class TakkaWeeklySheets {

	private static final String FONT = "Arial";

	private static final String RP = "_(\"Rp\"* #,##0_)";

	private static final String RP_DASH_ZERO = "_(\"Rp\"* #,##0_);;_(\"Rp\"* \"-\"_)";

	private static final int NAME = 1;

	private static final int DAY = 2;

	private static final int KOP_ROW_PEKERJAAN = 1;

	private static final int KOP_ROW_LOKASI = 2;

	private static final int HEADER = 3;

	private final XSSFWorkbook wb;

	private final XSSFFont bold;

	private final XSSFCellStyle plain;

	private final XSSFCellStyle centerPlain;

	private final XSSFCellStyle title;

	private final XSSFCellStyle header;

	private final XSSFCellStyle cell;

	private final XSSFCellStyle pink;

	private final XSSFCellStyle hours;

	private final XSSFCellStyle rp;

	private final XSSFCellStyle rpDash;

	private final XSSFCellStyle total;

	private final XSSFCellStyle boxLabel;

	private final XSSFCellStyle boxAmount;

	private TakkaWeeklySheets(XSSFWorkbook wb) {
		this.wb = wb;
		bold = font(8, null);
		plain = wb.createCellStyle();
		plain.setFont(bold);
		plain.setVerticalAlignment(VerticalAlignment.CENTER);
		centerPlain = wb.createCellStyle();
		centerPlain.cloneStyleFrom(plain);
		centerPlain.setAlignment(HorizontalAlignment.CENTER);
		title = wb.createCellStyle();
		title.setFont(font(14, null));
		title.setAlignment(HorizontalAlignment.CENTER);
		title.setVerticalAlignment(VerticalAlignment.CENTER);
		header = bordered(bold, HorizontalAlignment.CENTER);
		header.setWrapText(true);
		cell = bordered(bold, HorizontalAlignment.CENTER);
		pink = bordered(bold, HorizontalAlignment.CENTER);
		pink.setFillForegroundColor(new XSSFColor(HexFormat.of().parseHex(TakkaFormat.WEEKLY_RED), null));
		pink.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		hours = bordered(font(8, TakkaFormat.WEEKLY_HOURS), HorizontalAlignment.CENTER);
		rp = number(bold, RP);
		rpDash = number(bold, RP_DASH_ZERO);
		total = bordered(bold, HorizontalAlignment.CENTER);
		XSSFFont large = font(12, null);
		boxLabel = bordered(large, HorizontalAlignment.CENTER);
		boxAmount = number(large, RP);
	}

	static void write(XSSFWorkbook wb, ReportModel m) {
		TakkaWeeklySheets sheets = new TakkaWeeklySheets(wb);
		sheets.absensi(m);
		sheets.lemburan(m);
	}

	private void absensi(ReportModel m) {
		XSSFSheet s = wb.createSheet("Absensi");
		List<LocalDate> dates = m.dates();
		int h = DAY + dates.size();
		int upah = h + 1;
		int jumlah = h + 2;
		int lembur = h + 3;
		int piutang = h + 4;
		int sisa = h + 5;
		int insentif = h + 6;

		kop(s, h);
		XSSFRow titleRow = s.createRow(0);
		titleRow.setHeightInPoints(22);
		int titleFrom = Math.max(DAY, h - 1);
		text(titleRow, titleFrom, "ABSENSI", title);
		s.addMergedRegion(new CellRangeAddress(0, 0, titleFrom, lembur));
		text(s.getRow(KOP_ROW_PEKERJAAN), lembur, TakkaFormat.WEEKLY_PERIOD_LABEL, plain);
		text(s.getRow(KOP_ROW_PEKERJAAN), piutang, TakkaFormat.weeklyPeriod(dates), centerPlain);
		text(s.getRow(KOP_ROW_LOKASI), lembur, TakkaFormat.WEEKLY_WEEK_LABEL, plain);
		text(s.getRow(KOP_ROW_LOKASI), piutang, TakkaFormat.weeklyNumber(dates), centerPlain);

		// Judul dua baris: huruf hari di atas, baris kosong di bawahnya; kolom lain digabung.
		XSSFRow top = s.createRow(HEADER);
		XSSFRow sub = s.createRow(HEADER + 1);
		top.setHeightInPoints(20);
		sub.setHeightInPoints(14);
		for (int c = 0; c <= insentif; c++) {
			top.createCell(c).setCellStyle(header);
			sub.createCell(c).setCellStyle(header);
		}
		for (int i = 0; i < dates.size(); i++) {
			top.getCell(DAY + i).setCellValue(TakkaFormat.dayLetter(dates.get(i)));
			sub.getCell(DAY + i).setCellValue("");
		}
		spanHeader(s, top, 0, "No");
		spanHeader(s, top, NAME, "NAMA");
		spanHeader(s, top, h, "JMLH\nHARI");
		spanHeader(s, top, upah, "UPAH\n(RP)");
		spanHeader(s, top, jumlah, "JUMLAH UPAH\n(RP)");
		spanHeader(s, top, lembur, "LEMBUR\n(RP)");
		spanHeader(s, top, piutang, "INPUT PIUTANG");
		spanHeader(s, top, sisa, "SISA UPAH\n(RP)");
		spanHeader(s, top, insentif, "INSENTIF");

		int r = HEADER + 2;
		int no = 1;
		BigDecimal sumDaily = BigDecimal.ZERO;
		BigDecimal sumGross = BigDecimal.ZERO;
		BigDecimal sumOvertime = BigDecimal.ZERO;
		BigDecimal sumLoan = BigDecimal.ZERO;
		BigDecimal sumNet = BigDecimal.ZERO;
		for (ReportEmployee e : m.employees()) {
			XSSFRow row = s.createRow(r++);
			row.setHeightInPoints(15);
			identity(row, no++, e);
			for (int i = 0; i < dates.size(); i++) {
				String mark = TakkaFormat.mark(e, dates.get(i));
				text(row, DAY + i, mark, mark.isEmpty() ? pink : cell);
			}
			row.createCell(h).setCellValue(e.totals().countingDays());
			row.getCell(h).setCellStyle(cell);
			ReportFinancial f = e.financial();
			BigDecimal daily = TakkaFormat.nz(f == null ? null : f.dailyWage());
			BigDecimal gross = TakkaFormat.nz(f == null ? null : f.grossDailyWage());
			BigDecimal overtime = TakkaFormat.nz(f == null ? null : f.overtimeWage());
			BigDecimal loan = TakkaFormat.nz(f == null ? null : f.loanDeduction());
			BigDecimal net = TakkaFormat.nz(f == null ? null : f.netWage());
			money(row, upah, daily, rp);
			money(row, jumlah, gross, rp);
			money(row, lembur, overtime, rpDash);
			money(row, piutang, loan.signum() == 0 ? null : loan, rp);
			money(row, sisa, net, rp);
			row.createCell(insentif).setCellStyle(rp);
			sumDaily = sumDaily.add(daily);
			sumGross = sumGross.add(gross);
			sumOvertime = sumOvertime.add(overtime);
			sumLoan = sumLoan.add(loan);
			sumNet = sumNet.add(net);
		}
		if (m.employees().isEmpty()) {
			text(s.createRow(r++), NAME, TakkaFormat.NO_DATA, plain);
		}

		XSSFRow totals = s.createRow(r++);
		totals.setHeightInPoints(15);
		for (int c = 0; c <= h; c++) {
			totals.createCell(c).setCellStyle(total);
		}
		totals.getCell(0).setCellValue("JUMLAH TOTAL");
		s.addMergedRegion(new CellRangeAddress(totals.getRowNum(), totals.getRowNum(), 0, h));
		money(totals, upah, sumDaily, rpDash);
		money(totals, jumlah, sumGross, rpDash);
		money(totals, lembur, sumOvertime, rpDash);
		money(totals, piutang, sumLoan, rpDash);
		money(totals, sisa, sumNet, rpDash);
		money(totals, insentif, BigDecimal.ZERO, rpDash);

		// Kotak TOTAL UPAH setelah sela tipis; catatan per toko tetap ditulis tangan seperti di foto.
		s.createRow(r++).setHeightInPoints(6);
		XSSFRow box = s.createRow(r++);
		box.setHeightInPoints(26);
		text(box, piutang, "TOTAL UPAH", boxLabel);
		money(box, sisa, sumNet, boxAmount);
		box.createCell(insentif).setCellStyle(boxAmount);
		s.addMergedRegion(new CellRangeAddress(box.getRowNum(), box.getRowNum(), sisa, insentif));

		r++;
		XSSFRow approve = s.createRow(r);
		text(approve, 0, "Yang Menyetujui,", centerPlain);
		s.addMergedRegion(new CellRangeAddress(r, r, 0, NAME));
		r += 4;
		XSSFRow director = s.createRow(r);
		text(director, 0, "Direktur Operasional", centerPlain);
		s.addMergedRegion(new CellRangeAddress(r, r, 0, NAME));

		s.setColumnWidth(0, (int) (3.5 * 256));
		s.setColumnWidth(NAME, 20 * 256);
		for (int i = 0; i < dates.size(); i++) {
			s.setColumnWidth(DAY + i, (int) (4.5 * 256));
		}
		s.setColumnWidth(h, 6 * 256);
		s.setColumnWidth(upah, 14 * 256);
		s.setColumnWidth(jumlah, 14 * 256);
		s.setColumnWidth(lembur, 14 * 256);
		s.setColumnWidth(piutang, 30 * 256);
		s.setColumnWidth(sisa, 14 * 256);
		s.setColumnWidth(insentif, 14 * 256);
		print(s);
	}

	private void lemburan(ReportModel m) {
		XSSFSheet s = wb.createSheet("Lemburan");
		List<LocalDate> dates = m.dates();
		int h = DAY + dates.size();
		int rate = h + 1;
		int wage = h + 2;

		kop(s, h);
		XSSFRow titleRow = s.createRow(0);
		titleRow.setHeightInPoints(22);
		text(titleRow, 0, "LEMBURAN", title);
		s.addMergedRegion(new CellRangeAddress(0, 0, 0, wage));

		XSSFRow top = s.createRow(HEADER);
		top.setHeightInPoints(28);
		text(top, 0, "No", header);
		text(top, NAME, "NAMA", header);
		for (int i = 0; i < dates.size(); i++) {
			text(top, DAY + i, TakkaFormat.dayLetter(dates.get(i)), header);
		}
		text(top, h, "JMLH\nHARI", header);
		text(top, rate, "UANG LEMBUR", header);
		text(top, wage, "JUMLAH LEMBURAN", header);

		int r = HEADER + 1;
		int no = 1;
		BigDecimal sumWage = BigDecimal.ZERO;
		for (ReportEmployee e : m.employees()) {
			XSSFRow row = s.createRow(r++);
			row.setHeightInPoints(20);
			identity(row, no++, e);
			for (int i = 0; i < dates.size(); i++) {
				hoursCell(row, DAY + i, TakkaFormat.overtime(e, dates.get(i)));
			}
			BigDecimal totalHours = TakkaFormat.nz(e.totals().validatedOvertimeHours());
			hoursCell(row, h, totalHours.signum() == 0 ? null : totalHours);
			ReportFinancial f = e.financial();
			money(row, rate, f == null ? null : f.overtimeRate(), rp);
			BigDecimal amount = TakkaFormat.nz(f == null ? null : f.overtimeWage());
			money(row, wage, amount, rpDash);
			sumWage = sumWage.add(amount);
		}
		if (m.employees().isEmpty()) {
			text(s.createRow(r++), NAME, TakkaFormat.NO_DATA, plain);
		}

		// Baris kosong bergaris lalu baris JUMLAH TOTAL; nominal total menempati kedua baris.
		XSSFRow blank = s.createRow(r++);
		blank.setHeightInPoints(20);
		for (int c = 0; c <= wage; c++) {
			blank.createCell(c).setCellStyle(cell);
		}
		XSSFRow label = s.createRow(r++);
		label.setHeightInPoints(20);
		for (int c = 0; c <= wage; c++) {
			label.createCell(c).setCellStyle(total);
		}
		label.getCell(0).setCellValue("JUMLAH TOTAL");
		s.addMergedRegion(new CellRangeAddress(label.getRowNum(), label.getRowNum(), 0, rate));
		money(blank, wage, sumWage, rpDash);
		label.getCell(wage).setCellStyle(rpDash);
		s.addMergedRegion(new CellRangeAddress(blank.getRowNum(), label.getRowNum(), wage, wage));

		s.setColumnWidth(0, (int) (3.5 * 256));
		s.setColumnWidth(NAME, 24 * 256);
		for (int i = 0; i < dates.size(); i++) {
			s.setColumnWidth(DAY + i, 6 * 256);
		}
		s.setColumnWidth(h, 6 * 256);
		s.setColumnWidth(rate, 17 * 256);
		s.setColumnWidth(wage, 18 * 256);
		print(s);
	}

	/** PEKERJAAN dan LOKASI: titik dua di kolom hari pertama, nilainya di tengah kolom S keempat dan R seperti foto. */
	private void kop(XSSFSheet s, int h) {
		XSSFRow job = s.createRow(KOP_ROW_PEKERJAAN);
		XSSFRow location = s.createRow(KOP_ROW_LOKASI);
		job.setHeightInPoints(17);
		location.setHeightInPoints(17);
		text(job, 0, "PEKERJAAN", plain);
		text(location, 0, "LOKASI", plain);
		text(job, DAY, ":", centerPlain);
		text(location, DAY, ":", centerPlain);
		// Berhenti sebelum kolom LEMBUR (h + 3) agar sel PERIODE/MINGGU tidak tertutup pada rentang pendek.
		int last = Math.min(DAY + 5, h + 2);
		mergedValue(s, job, DAY + 1, last, TakkaFormat.WEEKLY_JOB);
		mergedValue(s, location, Math.min(DAY + 3, h), last, TakkaFormat.WEEKLY_LOCATION);
	}

	private void mergedValue(XSSFSheet s, XSSFRow row, int from, int to, String value) {
		text(row, from, value, centerPlain);
		if (to > from) {
			s.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), from, to));
		}
	}

	private static void spanHeader(XSSFSheet s, XSSFRow top, int col, String value) {
		top.getCell(col).setCellValue(value);
		s.addMergedRegion(new CellRangeAddress(top.getRowNum(), top.getRowNum() + 1, col, col));
	}

	private void identity(XSSFRow row, int no, ReportEmployee e) {
		row.createCell(0).setCellValue(no);
		row.getCell(0).setCellStyle(cell);
		text(row, NAME, TakkaFormat.upper(e.fullName()), cell);
	}

	/** Jam tanpa nilai menjadi sel kosong bergaris. */
	private void hoursCell(XSSFRow row, int col, BigDecimal value) {
		XSSFCell c = row.createCell(col);
		c.setCellStyle(hours);
		if (value != null) {
			c.setCellValue(value.doubleValue());
		}
	}

	private static void text(XSSFRow row, int col, String value, XSSFCellStyle style) {
		XSSFCell c = row.createCell(col);
		c.setCellValue(value);
		c.setCellStyle(style);
	}

	/** Nominal null menjadi sel kosong bergaris. */
	private static void money(XSSFRow row, int col, BigDecimal value, XSSFCellStyle style) {
		XSSFCell c = row.getCell(col) == null ? row.createCell(col) : row.getCell(col);
		c.setCellStyle(style);
		if (value != null) {
			c.setCellValue(value.doubleValue());
		}
	}

	private static void print(XSSFSheet s) {
		s.setDisplayGridlines(false);
		s.setPrintGridlines(false);
		s.setAutobreaks(true);
		s.setFitToPage(true);
		s.setHorizontallyCenter(true);
		PrintSetup setup = s.getPrintSetup();
		setup.setLandscape(true);
		setup.setPaperSize(PrintSetup.A4_PAPERSIZE);
		setup.setFitWidth((short) 1);
		setup.setFitHeight((short) 0);
	}

	private XSSFFont font(int size, String colorHex) {
		XSSFFont f = wb.createFont();
		f.setFontName(FONT);
		f.setFontHeightInPoints((short) size);
		f.setBold(true);
		if (colorHex != null) {
			f.setColor(new XSSFColor(HexFormat.of().parseHex(colorHex), null));
		}
		return f;
	}

	private XSSFCellStyle bordered(XSSFFont font, HorizontalAlignment align) {
		XSSFCellStyle st = wb.createCellStyle();
		st.setFont(font);
		st.setAlignment(align);
		st.setVerticalAlignment(VerticalAlignment.CENTER);
		st.setBorderTop(BorderStyle.THIN);
		st.setBorderBottom(BorderStyle.THIN);
		st.setBorderLeft(BorderStyle.THIN);
		st.setBorderRight(BorderStyle.THIN);
		return st;
	}

	private XSSFCellStyle number(XSSFFont font, String format) {
		XSSFCellStyle st = bordered(font, HorizontalAlignment.RIGHT);
		st.setDataFormat(wb.createDataFormat().getFormat(format));
		return st;
	}

}
