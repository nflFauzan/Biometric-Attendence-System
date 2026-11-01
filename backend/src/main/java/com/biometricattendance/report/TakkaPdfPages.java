package com.biometricattendance.report;

import static com.biometricattendance.report.TakkaFormat.DATE_COLS;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;

import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportFinancial;
import com.biometricattendance.report.ReportDtos.ReportModel;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/** PDF format klien: halaman AS-5 lalu AS-6, A4 landscape, tanpa kepala/kaki laporan umum (K-142). */
final class TakkaPdfPages {

	private static final String REGULAR = "fonts/DejaVuSans.ttf";

	private static final String BOLD = "fonts/DejaVuSans-Bold.ttf";

	private static final float MARGIN = 20f;

	private static final float WIDTH = PageSize.A4.getHeight() - 2 * MARGIN;

	private static final float NO_WIDTH = 12f;

	private static final float SALARY_NAME = 120f;

	private static final float OVERTIME_NAME = 160f;

	private static final float SALARY_ROW = 14f;

	private static final float OVERTIME_ROW = 24f;

	/** Lebar kop mengikuti foto: "Month:" di tengah pada AS-5, dekat kotak bulan pada AS-6. */
	private static final float[] SALARY_KOP = { 60f, 6f, 24f, 10f };

	private static final float[] OVERTIME_KOP = { 80f, 6f, 4f, 10f };

	private static final float SEPARATOR_WIDTH = 4f;

	private static final float[] SALARY_TOTALS = { 26f, 36f, 38f, 44f, 54f, 54f, 58f };

	private static final float[] OVERTIME_TOTALS = { 46f, 58f, 67f };

	private static final float SALARY_DATE = 11.5f;

	private static final float OVERTIME_DATE = 14.5f;

	private static final int ROWS_KEPT_WITH_CLOSING = 3;

	private static final String SIGNATURE_LINE = "______________________________";

	private final Font text;

	private final Font strong;

	private final Font heading;

	private final Font kop;

	private final Font kopStrong;

	private final Font band;

	private TakkaPdfPages(BaseFont regular, BaseFont bold) {
		text = new Font(regular, 5.5f, Font.NORMAL, Color.BLACK);
		strong = new Font(bold, 5.5f, Font.NORMAL, Color.BLACK);
		heading = new Font(bold, 5f, Font.NORMAL, Color.BLACK);
		kop = new Font(regular, 8f, Font.NORMAL, Color.BLACK);
		kopStrong = new Font(bold, 8f, Font.NORMAL, Color.BLACK);
		band = new Font(bold, 10f, Font.NORMAL, Color.WHITE);
	}

	static byte[] write(ReportModel model) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4.rotate(), MARGIN, MARGIN, MARGIN, MARGIN);
		try {
			// cached=false: tiap render punya instance sendiri (sama seperti PdfReportWriter).
			TakkaPdfPages pages = new TakkaPdfPages(font(REGULAR), font(BOLD));
			PdfWriter writer = PdfWriter.getInstance(document, out);
			document.addTitle(model.title());
			document.open();
			pages.kop(document, "TAKKA STEEL", model, SALARY_KOP, false);
			pages.place(document, writer, model.employees().size(),
					(from, to, closing) -> pages.salaryTable(model, from, to, closing));
			document.newPage();
			pages.kop(document, "REKAP LEMBUR TAKKA STEEL", model, OVERTIME_KOP, true);
			pages.place(document, writer, TakkaFormat.overtimeRows(model.employees()).size(),
					(from, to, closing) -> pages.overtimeTable(model, from, to, closing));
			document.close();
			return out.toByteArray();
		}
		catch (DocumentException ex) {
			throw new IllegalStateException("Gagal menyusun PDF laporan", ex);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** Membuat tabel untuk baris karyawan [from, to); {@code closing} menambahkan baris total dan tanda tangan. */
	@FunctionalInterface
	private interface TablePart {

		PdfPTable build(int from, int to, boolean closing) throws DocumentException;

	}

	/**
	 * Menempatkan tabel; bila penutup tidak muat setelah baris karyawan terakhir, beberapa baris terakhir ikut pindah
	 * ke halaman berikutnya agar total dan tanda tangan tidak tertinggal sendirian.
	 */
	private void place(Document document, PdfWriter writer, int rows, TablePart part) throws DocumentException {
		PdfPTable full = part.build(0, rows, true);
		float pageRoom = document.getPageSize().getHeight() - 2 * MARGIN;
		float room = writer.getVerticalPosition(true) - MARGIN;
		float header = full.getRowHeight(0);
		float used = header;
		for (int i = 1; i <= rows; i++) {
			float height = full.getRowHeight(i);
			if (used + height > room) {
				room = pageRoom;
				used = header;
			}
			used += height;
		}
		float closing = 0f;
		for (int i = rows + 1; i < full.size(); i++) {
			closing += full.getRowHeight(i);
		}
		if (rows == 0 || used + closing <= room) {
			document.add(full);
			return;
		}
		int split = Math.max(0, rows - ROWS_KEPT_WITH_CLOSING);
		if (split > 0) {
			document.add(part.build(0, split, false));
			document.newPage();
		}
		document.add(part.build(split, rows, true));
	}

	private static BaseFont font(String path) throws IOException {
		try (InputStream in = TakkaPdfPages.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				throw new IOException("Font laporan tidak ditemukan: " + path);
			}
			return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, false, in.readAllBytes(), null);
		}
	}

	private static Color color(String hex) {
		return new Color(Integer.parseInt(hex, 16));
	}

	private void kop(Document document, String title, ReportModel m, float[] widths, boolean monthOnDepartmentRow)
			throws DocumentException {
		PdfPTable bandTable = new PdfPTable(1);
		bandTable.setWidthPercentage(100);
		PdfPCell bandCell = new PdfPCell(new Phrase(title, band));
		bandCell.setBackgroundColor(color(TakkaFormat.BAND));
		bandCell.setBorder(Rectangle.NO_BORDER);
		bandCell.setHorizontalAlignment(Element.ALIGN_CENTER);
		bandCell.setPaddingBottom(4f);
		bandTable.addCell(bandCell);
		document.add(bandTable);

		PdfPTable head = new PdfPTable(widths);
		head.setWidthPercentage(100);
		head.setSpacingBefore(6f);
		head.setSpacingAfter(6f);
		kopRow(head, labelled("Manager Name:  ", m.managerName()), monthOnDepartmentRow ? null : m);
		kopRow(head, labelled("Department:  ", m.departmentName()), monthOnDepartmentRow ? m : null);
		document.add(head);
	}

	private void kopRow(PdfPTable head, Phrase left, ReportModel monthOf) {
		head.addCell(plain(left, Element.ALIGN_LEFT, null));
		head.addCell(plain(new Phrase(monthOf == null ? "" : "Month:", kopStrong), Element.ALIGN_LEFT, null));
		head.addCell(plain(new Phrase("", kop), Element.ALIGN_LEFT, null));
		head.addCell(monthOf == null ? plain(new Phrase("", kop), Element.ALIGN_CENTER, null)
				: plain(new Phrase(TakkaFormat.nz(monthOf.monthLabel()), kopStrong), Element.ALIGN_CENTER,
						color(TakkaFormat.MONTH_FILL)));
	}

	private Phrase labelled(String label, String value) {
		Phrase phrase = new Phrase();
		phrase.add(new Chunk(label, kopStrong));
		phrase.add(new Chunk(TakkaFormat.nz(value), kop));
		return phrase;
	}

	private static PdfPCell plain(Phrase phrase, int align, Color fill) {
		PdfPCell c = new PdfPCell(phrase);
		c.setBorder(Rectangle.NO_BORDER);
		c.setHorizontalAlignment(align);
		c.setPaddingBottom(3f);
		if (fill != null) {
			c.setBackgroundColor(fill);
		}
		return c;
	}

	private PdfPTable salaryTable(ReportModel m, int from, int to, boolean closing) throws DocumentException {
		String[] totals = TakkaFormat.SALARY_TOTALS;
		PdfPTable t = table(SALARY_NAME, SALARY_DATE, SALARY_TOTALS);
		headerRow(t, m, "Employee Name", totals);
		int no = from + 1;
		int sumDays = 0;
		BigDecimal sumWages = BigDecimal.ZERO;
		for (ReportEmployee e : m.employees()) {
			ReportFinancial sumOf = e.financial();
			sumDays += e.totals().countingDays();
			sumWages = sumWages.add(TakkaFormat.nz(sumOf == null ? null : sumOf.netWage()));
		}
		for (ReportEmployee e : m.employees().subList(from, to)) {
			identity(t, no++, e, SALARY_ROW);
			for (int d = 1; d <= DATE_COLS; d++) {
				t.addCell(cell(TakkaFormat.mark(e, d), text, Element.ALIGN_CENTER,
						TakkaFormat.redSalaryCell(e, d) ? color(TakkaFormat.RED) : null));
			}
			ReportFinancial f = e.financial();
			int days = e.totals().countingDays();
			t.addCell(cell(String.valueOf(days), text, Element.ALIGN_CENTER, null));
			t.addCell(amountCell(f == null ? null : f.dailyWage()));
			t.addCell(amountCell(TakkaFormat.nz(f == null ? null : f.overtimeWage())));
			BigDecimal loan = f == null ? null : f.loanDeduction();
			t.addCell(rupiahCell(loan == null || loan.signum() == 0 ? null : TakkaFormat.amountText(loan)));
			boolean hasBase = f != null && f.baseSalary() != null;
			t.addCell(rupiahCell(hasBase ? TakkaFormat.amountText(f.onsiteWage()) : null));
			t.addCell(rupiahCell(hasBase ? TakkaFormat.amountText(f.baseSalary()) : null));
			BigDecimal net = TakkaFormat.nz(f == null ? null : f.netWage());
			t.addCell(rupiahCell(TakkaFormat.amountText(net)));
		}
		int columns = 3 + DATE_COLS + totals.length;
		if (m.employees().isEmpty()) {
			noData(t, columns);
		}
		if (!closing) {
			return t;
		}
		// Baris kosong berisi total Total Wages, lalu baris TOTAL dengan kotak jumlah hari.
		PdfPCell blank = cell("", text, Element.ALIGN_LEFT, null);
		blank.setColspan(columns - 1);
		t.addCell(blank);
		t.addCell(rupiahCell(TakkaFormat.amountText(sumWages)));
		PdfPCell label = cell("TOTAL", strong, Element.ALIGN_CENTER, null);
		label.setColspan(3 + DATE_COLS);
		label.setMinimumHeight(22f);
		t.addCell(label);
		t.addCell(cell(String.valueOf(sumDays), strong, Element.ALIGN_CENTER, color(TakkaFormat.TOTAL_DAYS_FILL)));
		PdfPCell rest = cell("", text, Element.ALIGN_LEFT, null);
		rest.setColspan(totals.length - 1);
		t.addCell(rest);
		signature(t, columns, "Director Signature:");
		signature(t, columns, "Manager Signature:");
		return t;
	}

	private PdfPTable overtimeTable(ReportModel m, int from, int to, boolean closing) throws DocumentException {
		String[] totals = TakkaFormat.OVERTIME_TOTALS;
		PdfPTable t = table(OVERTIME_NAME, OVERTIME_DATE, OVERTIME_TOTALS);
		headerRow(t, m, "NAMA KARYAWAN", totals);
		List<ReportEmployee> rows = TakkaFormat.overtimeRows(m.employees());
		int no = from + 1;
		for (ReportEmployee e : rows.subList(from, to)) {
			identity(t, no++, e, OVERTIME_ROW);
			for (int d = 1; d <= DATE_COLS; d++) {
				t.addCell(cell(TakkaFormat.hoursText(TakkaFormat.overtime(e, d)), text, Element.ALIGN_CENTER,
						TakkaFormat.redColumn(m, d) ? color(TakkaFormat.RED) : null));
			}
			BigDecimal totalHours = TakkaFormat.nz(e.totals().validatedOvertimeHours());
			t.addCell(cell(totalHours.signum() == 0 ? "0" : TakkaFormat.hoursText(totalHours), text,
					Element.ALIGN_CENTER, null));
			t.addCell(amountCell(e.financial().overtimeRate()));
			BigDecimal wage = TakkaFormat.nz(e.financial().overtimeWage());
			t.addCell(rupiahCell(wage.signum() == 0 ? "-" : TakkaFormat.amountText(wage)));
		}
		int columns = 3 + DATE_COLS + totals.length;
		if (rows.isEmpty()) {
			noData(t, columns);
		}
		if (closing) {
			signature(t, columns, "Director Signature:");
		}
		return t;
	}

	private PdfPTable table(float nameWidth, float dateWidth, float[] totalWidths) throws DocumentException {
		float[] widths = new float[3 + DATE_COLS + totalWidths.length];
		widths[0] = NO_WIDTH;
		widths[1] = nameWidth;
		widths[2] = SEPARATOR_WIDTH;
		for (int d = 0; d < DATE_COLS; d++) {
			widths[3 + d] = dateWidth;
		}
		System.arraycopy(totalWidths, 0, widths, 3 + DATE_COLS, totalWidths.length);
		PdfPTable t = new PdfPTable(widths);
		t.setTotalWidth(WIDTH);
		t.setLockedWidth(true);
		t.setHorizontalAlignment(Element.ALIGN_LEFT);
		t.setHeaderRows(1);
		t.setSplitRows(false);
		t.setSplitLate(true);
		return t;
	}

	private void headerRow(PdfPTable t, ReportModel m, String nameHeader, String[] totals) {
		t.addCell(cell("", heading, Element.ALIGN_CENTER, null));
		t.addCell(cell(nameHeader, heading, Element.ALIGN_CENTER, null));
		t.addCell(cell("", heading, Element.ALIGN_CENTER, color(TakkaFormat.SEPARATOR)));
		for (int d = 1; d <= DATE_COLS; d++) {
			t.addCell(cell(String.valueOf(d), heading, Element.ALIGN_CENTER,
					TakkaFormat.redColumn(m, d) ? color(TakkaFormat.RED) : null));
		}
		for (String total : totals) {
			t.addCell(cell(total, heading, Element.ALIGN_CENTER, null));
		}
	}

	private void identity(PdfPTable t, int no, ReportEmployee e, float rowHeight) {
		PdfPCell number = cell(String.valueOf(no), text, Element.ALIGN_CENTER, null);
		number.setMinimumHeight(rowHeight);
		t.addCell(number);
		t.addCell(cell(TakkaFormat.upper(e.fullName()), text, Element.ALIGN_LEFT, null));
		t.addCell(cell("", text, Element.ALIGN_CENTER, color(TakkaFormat.SEPARATOR)));
	}

	private void noData(PdfPTable t, int columns) {
		PdfPCell c = cell(TakkaFormat.NO_DATA, text, Element.ALIGN_LEFT, null);
		c.setColspan(columns);
		t.addCell(c);
	}

	/** Tanda tangan menjadi baris tabel, sehingga bila pindah halaman tetap ditemani judul kolom yang berulang. */
	private void signature(PdfPTable t, int columns, String label) {
		Phrase phrase = new Phrase();
		phrase.add(new Chunk(label, kopStrong));
		phrase.add(new Chunk(SIGNATURE_LINE, kop));
		PdfPCell c = plain(phrase, Element.ALIGN_LEFT, null);
		c.setColspan(columns);
		c.setPaddingTop(26f);
		t.addCell(c);
	}

	private PdfPCell amountCell(BigDecimal value) {
		return cell(value == null ? "" : TakkaFormat.amountText(value), text, Element.ALIGN_RIGHT, null);
	}

	/** "Rp" rata kiri dan angka rata kanan dalam satu sel; null menjadi sel kosong bergaris. */
	private PdfPCell rupiahCell(String amount) {
		if (amount == null) {
			return cell("", text, Element.ALIGN_RIGHT, null);
		}
		PdfPTable inner = new PdfPTable(new float[] { 1f, 3f });
		inner.setWidthPercentage(100);
		PdfPCell left = plain(new Phrase("Rp", text), Element.ALIGN_LEFT, null);
		PdfPCell right = plain(new Phrase(amount, text), Element.ALIGN_RIGHT, null);
		for (PdfPCell part : List.of(left, right)) {
			part.setPadding(0f);
			part.setNoWrap(true);
			inner.addCell(part);
		}
		PdfPCell outer = new PdfPCell(inner);
		outer.setPadding(1.5f);
		outer.setBorderWidth(0.4f);
		outer.setBorderColor(Color.BLACK);
		outer.setVerticalAlignment(Element.ALIGN_MIDDLE);
		return outer;
	}

	private static PdfPCell cell(String value, Font font, int align, Color fill) {
		PdfPCell c = new PdfPCell(new Phrase(value, font));
		c.setHorizontalAlignment(align);
		c.setVerticalAlignment(Element.ALIGN_MIDDLE);
		c.setPaddingLeft(1f);
		c.setPaddingRight(1f);
		c.setPaddingTop(2f);
		c.setPaddingBottom(3f);
		c.setBorderWidth(0.4f);
		c.setBorderColor(Color.BLACK);
		if (fill != null) {
			c.setBackgroundColor(fill);
		}
		return c;
	}

}
