package com.biometricattendance.report;

import static com.biometricattendance.report.TakkaFormat.DATE_COL;
import static com.biometricattendance.report.TakkaFormat.DATE_COLS;
import static com.biometricattendance.report.TakkaFormat.FIRST_DATA_ROW;
import static com.biometricattendance.report.TakkaFormat.HEADER_ROW;

import java.math.BigDecimal;
import java.util.HexFormat;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PaperSize;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportFinancial;
import com.biometricattendance.report.ReportDtos.ReportModel;

/** Lembar Excel format klien: AS-5 "Gaji Staf" dan AS-6 "Lembur Staf" (K-142). */
final class TakkaExcelSheets {

	private static final String FONT = "Verdana";

	private static final String RP = "_(\"Rp\"* #,##0_)";

	private static final String RP_BLANK_ZERO = "_(\"Rp\"* #,##0_);;;";

	private static final String RP_DASH_ZERO = "_(\"Rp\"* #,##0_);;_(\"Rp\"* \"-\"_)";

	private static final String AMOUNT = "#,##0";

	private static final String SIGNATURE_LINE = "______________________________";

	private final XSSFWorkbook wb;

	private final XSSFFont regular;

	private final XSSFFont bold;

	private final XSSFCellStyle plain;

	private final XSSFCellStyle header;

	private final XSSFCellStyle headerRed;

	private final XSSFCellStyle separator;

	private final XSSFCellStyle mark;

	private final XSSFCellStyle red;

	private final XSSFCellStyle name;

	private final XSSFCellStyle general;

	private final XSSFCellStyle generalRed;

	private final XSSFCellStyle amount;

	private final XSSFCellStyle rp;

	private final XSSFCellStyle rpBlankZero;

	private final XSSFCellStyle rpDashZero;

	private final XSSFCellStyle totalLabel;

	private final XSSFCellStyle totalDays;

	private final XSSFCellStyle band;

	private final XSSFCellStyle monthValue;

	private final XSSFCellStyle monthLabel;

	private TakkaExcelSheets(XSSFWorkbook wb) {
		this.wb = wb;
		regular = font(8, false);
		bold = font(8, true);
		plain = wb.createCellStyle();
		plain.setFont(regular);
		header = bordered(bold, HorizontalAlignment.CENTER);
		header.setWrapText(true);
		headerRed = wb.createCellStyle();
		headerRed.cloneStyleFrom(header);
		fill(headerRed, TakkaFormat.RED);
		separator = bordered(regular, HorizontalAlignment.CENTER);
		fill(separator, TakkaFormat.SEPARATOR);
		mark = bordered(regular, HorizontalAlignment.CENTER);
		red = bordered(regular, HorizontalAlignment.CENTER);
		fill(red, TakkaFormat.RED);
		name = bordered(regular, HorizontalAlignment.LEFT);
		general = bordered(regular, HorizontalAlignment.CENTER);
		generalRed = wb.createCellStyle();
		generalRed.cloneStyleFrom(general);
		fill(generalRed, TakkaFormat.RED);
		amount = number(AMOUNT);
		rp = number(RP);
		rpBlankZero = number(RP_BLANK_ZERO);
		rpDashZero = number(RP_DASH_ZERO);
		totalLabel = bordered(bold, HorizontalAlignment.CENTER);
		totalDays = bordered(bold, HorizontalAlignment.CENTER);
		fill(totalDays, TakkaFormat.TOTAL_DAYS_FILL);
		band = wb.createCellStyle();
		XSSFFont white = font(10, true);
		white.setColor(new XSSFColor(new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF }, null));
		band.setFont(white);
		band.setAlignment(HorizontalAlignment.CENTER);
		band.setVerticalAlignment(VerticalAlignment.CENTER);
		fill(band, TakkaFormat.BAND);
		monthValue = wb.createCellStyle();
		monthValue.setFont(bold);
		monthValue.setAlignment(HorizontalAlignment.CENTER);
		fill(monthValue, TakkaFormat.MONTH_FILL);
		monthLabel = wb.createCellStyle();
		monthLabel.setFont(bold);
		monthLabel.setAlignment(HorizontalAlignment.RIGHT);
	}

	static void salary(XSSFWorkbook wb, ReportModel m) {
		new TakkaExcelSheets(wb).salarySheet(m);
	}

	static void overtime(XSSFWorkbook wb, ReportModel m) {
		new TakkaExcelSheets(wb).overtimeSheet(m);
	}

	private void salarySheet(ReportModel m) {
		XSSFSheet s = wb.createSheet("Gaji Staf");
		String[] totals = TakkaFormat.SALARY_TOTALS;
		int lastCol = DATE_COL + DATE_COLS + totals.length - 1;
		kop(s, "TAKKA STEEL", m, lastCol, TakkaFormat.SALARY_MONTH_ROW, DATE_COL + 20);
		headerRow(s, m, "Employee Name", totals);
		int t = DATE_COL + DATE_COLS;
		int r = FIRST_DATA_ROW;
		int no = 1;
		int sumDays = 0;
		BigDecimal sumWages = BigDecimal.ZERO;
		for (ReportEmployee e : m.employees()) {
			XSSFRow row = s.createRow(r++);
			identity(row, no++, e);
			for (int d = 1; d <= DATE_COLS; d++) {
				XSSFCell c = row.createCell(DATE_COL + d - 1);
				c.setCellValue(TakkaFormat.mark(e, d));
				c.setCellStyle(TakkaFormat.redSalaryCell(e, d) ? red : mark);
			}
			ReportFinancial f = e.financial();
			int days = e.totals().countingDays();
			sumDays += days;
			row.createCell(t).setCellValue(days);
			row.getCell(t).setCellStyle(general);
			money(row, t + 1, f == null ? null : f.dailyWage(), amount);
			money(row, t + 2, TakkaFormat.nz(f == null ? null : f.overtimeWage()), amount);
			BigDecimal loan = f == null ? null : f.loanDeduction();
			money(row, t + 3, loan == null || loan.signum() == 0 ? null : loan, rpBlankZero);
			boolean hasBase = f != null && f.baseSalary() != null;
			money(row, t + 4, hasBase ? f.onsiteWage() : null, rp);
			money(row, t + 5, hasBase ? f.baseSalary() : null, rp);
			BigDecimal net = TakkaFormat.nz(f == null ? null : f.netWage());
			money(row, t + 6, net, rp);
			sumWages = sumWages.add(net);
		}
		if (m.employees().isEmpty()) {
			text(s.createRow(r++), 1, TakkaFormat.NO_DATA, plain);
		}
		// Baris kosong berisi total Total Wages, lalu baris TOTAL dengan kotak jumlah hari.
		XSSFRow grand = s.createRow(r++);
		for (int c = 0; c <= lastCol; c++) {
			grand.createCell(c).setCellStyle(mark);
		}
		money(grand, t + 6, sumWages, rp);
		XSSFRow total = s.createRow(r++);
		total.setHeightInPoints(30);
		for (int c = 0; c <= lastCol; c++) {
			total.createCell(c).setCellStyle(totalLabel);
		}
		total.getCell(0).setCellValue("TOTAL");
		s.addMergedRegion(new CellRangeAddress(total.getRowNum(), total.getRowNum(), 0, t - 1));
		total.getCell(t).setCellValue(sumDays);
		total.getCell(t).setCellStyle(totalDays);
		r = signature(s, r + 2, "Director Signature:");
		signature(s, r + 2, "Manager Signature:");
		finish(s, lastCol, 28, new int[] { 11, 11, 11, 13, 14, 14, 15 });
	}

	private void overtimeSheet(ReportModel m) {
		XSSFSheet s = wb.createSheet("Lembur Staf");
		String[] totals = TakkaFormat.OVERTIME_TOTALS;
		int lastCol = DATE_COL + DATE_COLS + totals.length - 1;
		kop(s, "REKAP LEMBUR TAKKA STEEL", m, lastCol, TakkaFormat.OVERTIME_MONTH_ROW, DATE_COL + 28);
		headerRow(s, m, "NAMA KARYAWAN", totals);
		int t = DATE_COL + DATE_COLS;
		int r = FIRST_DATA_ROW;
		int no = 1;
		List<ReportEmployee> rows = TakkaFormat.overtimeRows(m.employees());
		for (ReportEmployee e : rows) {
			XSSFRow row = s.createRow(r++);
			identity(row, no++, e);
			row.setHeightInPoints(24);
			for (int d = 1; d <= DATE_COLS; d++) {
				XSSFCell c = row.createCell(DATE_COL + d - 1);
				c.setCellStyle(TakkaFormat.redColumn(m, d) ? generalRed : general);
				BigDecimal ot = TakkaFormat.overtime(e, d);
				if (ot != null) {
					c.setCellValue(ot.doubleValue());
				}
			}
			row.createCell(t).setCellValue(TakkaFormat.nz(e.totals().validatedOvertimeHours()).doubleValue());
			row.getCell(t).setCellStyle(general);
			money(row, t + 1, e.financial().overtimeRate(), amount);
			money(row, t + 2, TakkaFormat.nz(e.financial().overtimeWage()), rpDashZero);
		}
		if (rows.isEmpty()) {
			text(s.createRow(r++), 1, TakkaFormat.NO_DATA, plain);
		}
		signature(s, r + 2, "Director Signature:");
		finish(s, lastCol, 38, new int[] { 11, 12, 15 });
	}

	private void kop(XSSFSheet s, String title, ReportModel m, int lastCol, int monthRow, int monthLabelCol) {
		XSSFRow bandRow = s.createRow(0);
		bandRow.setHeightInPoints(16);
		for (int c = 0; c <= lastCol; c++) {
			bandRow.createCell(c).setCellStyle(band);
		}
		bandRow.getCell(0).setCellValue(title);
		s.addMergedRegion(new CellRangeAddress(0, 0, 0, lastCol));
		labelled(s.createRow(1), "Manager Name:", TakkaFormat.nz(m.managerName()));
		labelled(s.createRow(2), "Department:", TakkaFormat.nz(m.departmentName()));
		XSSFRow row = s.getRow(monthRow);
		text(row, monthLabelCol, "Month:", monthLabel);
		s.addMergedRegion(new CellRangeAddress(monthRow, monthRow, monthLabelCol, monthLabelCol + 2));
		text(row, lastCol, TakkaFormat.nz(m.monthLabel()), monthValue);
	}

	private void labelled(XSSFRow row, String label, String value) {
		XSSFRichTextString rich = new XSSFRichTextString(label + "  " + value);
		rich.applyFont(0, label.length(), bold);
		rich.applyFont(label.length(), rich.length(), regular);
		XSSFCell c = row.createCell(0);
		c.setCellValue(rich);
		row.getSheet().addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, DATE_COL + 12));
	}

	private void headerRow(XSSFSheet s, ReportModel m, String nameHeader, String[] totals) {
		XSSFRow h = s.createRow(HEADER_ROW);
		h.setHeightInPoints(26);
		text(h, 0, "", header);
		text(h, 1, nameHeader, header);
		h.createCell(2).setCellStyle(separator);
		for (int d = 1; d <= DATE_COLS; d++) {
			text(h, DATE_COL + d - 1, String.valueOf(d), TakkaFormat.redColumn(m, d) ? headerRed : header);
		}
		for (int i = 0; i < totals.length; i++) {
			text(h, DATE_COL + DATE_COLS + i, totals[i], header);
		}
	}

	private void identity(XSSFRow row, int no, ReportEmployee e) {
		row.setHeightInPoints(15);
		row.createCell(0).setCellValue(no);
		row.getCell(0).setCellStyle(general);
		text(row, 1, TakkaFormat.upper(e.fullName()), name);
		row.createCell(2).setCellStyle(separator);
	}

	private int signature(XSSFSheet s, int rowIndex, String label) {
		XSSFRow row = s.createRow(rowIndex);
		XSSFRichTextString rich = new XSSFRichTextString(label + SIGNATURE_LINE);
		rich.applyFont(0, label.length(), bold);
		rich.applyFont(label.length(), rich.length(), regular);
		row.createCell(0).setCellValue(rich);
		s.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, DATE_COL + 12));
		return rowIndex;
	}

	private void finish(XSSFSheet s, int lastCol, int nameWidth, int[] totalWidths) {
		s.setColumnWidth(0, 3 * 256);
		s.setColumnWidth(1, nameWidth * 256);
		s.setColumnWidth(2, 256);
		for (int d = 0; d < DATE_COLS; d++) {
			s.setColumnWidth(DATE_COL + d, 4 * 256);
		}
		for (int i = 0; i < totalWidths.length; i++) {
			s.setColumnWidth(DATE_COL + DATE_COLS + i, totalWidths[i] * 256);
		}
		s.setDisplayGridlines(false);
		s.setPrintGridlines(false);
		s.setAutobreaks(true);
		s.setFitToPage(true);
		s.getPrintSetup().setLandscape(true);
		s.getPrintSetup().setPaperSize(PaperSize.A4_PAPER);
		s.getPrintSetup().setFitWidth((short) 1);
		s.getPrintSetup().setFitHeight((short) 0);
		s.setRepeatingRows(new CellRangeAddress(HEADER_ROW, HEADER_ROW, 0, lastCol));
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

	private XSSFFont font(int size, boolean isBold) {
		XSSFFont f = wb.createFont();
		f.setFontName(FONT);
		f.setFontHeightInPoints((short) size);
		f.setBold(isBold);
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

	private XSSFCellStyle number(String format) {
		XSSFCellStyle st = bordered(regular, HorizontalAlignment.RIGHT);
		st.setDataFormat(wb.createDataFormat().getFormat(format));
		return st;
	}

	private static void fill(XSSFCellStyle style, String hex) {
		style.setFillForegroundColor(new XSSFColor(HexFormat.of().parseHex(hex), null));
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
	}

}
