package com.biometricattendance.report;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportFinancial;
import com.biometricattendance.report.ReportDtos.ReportModel;
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

/**
 * PDF format klien: halaman AS-1 "Absensi" lalu AS-2 "Lemburan", A4 landscape, tanpa kepala/kaki laporan umum
 * (K-143). Memakai Helvetica bawaan PDF karena bentuknya sama dengan Arial di foto dan Arial tidak boleh di-embed.
 */
final class TakkaWeeklyPdf {

	private static final float MARGIN = 20f;

	private static final float FULL_WIDTH = PageSize.A4.getHeight() - 2 * MARGIN;

	/** Lembar Lemburan di foto lebih sempit dari halaman dan berada di tengah. */
	private static final float LEMBURAN_WIDTH = 560f;

	private static final float ABSENSI_ROW = 15f;

	private static final float LEMBURAN_ROW = 20f;

	private final Font body;

	private final Font hours;

	private final Font title;

	private final Font large;

	private TakkaWeeklyPdf(BaseFont bold) {
		body = new Font(bold, 7f, Font.NORMAL, Color.BLACK);
		hours = new Font(bold, 7f, Font.NORMAL, color(TakkaFormat.WEEKLY_HOURS));
		title = new Font(bold, 13f, Font.NORMAL, Color.BLACK);
		large = new Font(bold, 11f, Font.NORMAL, Color.BLACK);
	}

	static byte[] write(ReportModel model) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4.rotate(), MARGIN, MARGIN, MARGIN, MARGIN);
		try {
			TakkaWeeklyPdf pdf = new TakkaWeeklyPdf(
					BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED));
			PdfWriter.getInstance(document, out);
			document.addTitle(model.title());
			document.open();
			pdf.absensi(document, model);
			document.newPage();
			pdf.lemburan(document, model);
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

	private void absensi(Document document, ReportModel m) throws DocumentException {
		List<LocalDate> dates = m.dates();
		int n = dates.size();
		float[] widths = new float[n + 9];
		widths[0] = 24f;
		widths[1] = 130f;
		for (int i = 0; i < n; i++) {
			widths[2 + i] = 31f;
		}
		float[] rest = { 40f, 100f, 105f, 105f, 215f, 105f, 105f };
		System.arraycopy(rest, 0, widths, 2 + n, rest.length);
		int columns = widths.length;
		int h = 2 + n;

		PdfPTable kop = table(widths, FULL_WIDTH);
		int titleFrom = Math.max(2, h - 1);
		blank(kop, titleFrom);
		kop.addCell(free("ABSENSI", title, Element.ALIGN_CENTER, h + 4 - titleFrom));
		blank(kop, columns - (h + 4));
		kopRow(kop, n, columns, "PEKERJAAN", TakkaFormat.WEEKLY_JOB, 3, TakkaFormat.WEEKLY_PERIOD_LABEL,
				TakkaFormat.weeklyPeriod(dates));
		kopRow(kop, n, columns, "LOKASI", TakkaFormat.WEEKLY_LOCATION, 5, TakkaFormat.WEEKLY_WEEK_LABEL,
				TakkaFormat.weeklyNumber(dates));
		kop.setSpacingAfter(3f);
		document.add(kop);

		PdfPTable t = table(widths, FULL_WIDTH);
		t.setHeaderRows(2);
		t.addCell(spanning("No"));
		t.addCell(spanning("NAMA"));
		for (LocalDate d : dates) {
			t.addCell(boxed(TakkaFormat.dayLetter(d), body, Element.ALIGN_CENTER, null));
		}
		for (String head : List.of("JMLH\nHARI", "UPAH\n(RP)", "JUMLAH UPAH\n(RP)", "LEMBUR\n(RP)", "INPUT PIUTANG",
				"SISA UPAH\n(RP)", "INSENTIF")) {
			t.addCell(spanning(head));
		}
		for (int i = 0; i < n; i++) {
			PdfPCell empty = boxed("", body, Element.ALIGN_CENTER, null);
			empty.setMinimumHeight(11f);
			t.addCell(empty);
		}

		int no = 1;
		BigDecimal sumDaily = BigDecimal.ZERO;
		BigDecimal sumGross = BigDecimal.ZERO;
		BigDecimal sumOvertime = BigDecimal.ZERO;
		BigDecimal sumLoan = BigDecimal.ZERO;
		BigDecimal sumNet = BigDecimal.ZERO;
		for (ReportEmployee e : m.employees()) {
			identity(t, no++, e, ABSENSI_ROW);
			for (LocalDate d : dates) {
				String mark = TakkaFormat.mark(e, d);
				t.addCell(boxed(mark, body, Element.ALIGN_CENTER, mark.isEmpty() ? color(TakkaFormat.WEEKLY_RED) : null));
			}
			t.addCell(boxed(String.valueOf(e.totals().countingDays()), body, Element.ALIGN_CENTER, null));
			ReportFinancial f = e.financial();
			BigDecimal daily = TakkaFormat.nz(f == null ? null : f.dailyWage());
			BigDecimal gross = TakkaFormat.nz(f == null ? null : f.grossDailyWage());
			BigDecimal overtime = TakkaFormat.nz(f == null ? null : f.overtimeWage());
			BigDecimal loan = TakkaFormat.nz(f == null ? null : f.loanDeduction());
			BigDecimal net = TakkaFormat.nz(f == null ? null : f.netWage());
			t.addCell(rupiah(amount(daily), body));
			t.addCell(rupiah(amount(gross), body));
			t.addCell(rupiah(dashed(overtime), body));
			t.addCell(rupiah(loan.signum() == 0 ? null : amount(loan), body));
			t.addCell(rupiah(amount(net), body));
			t.addCell(boxed("", body, Element.ALIGN_CENTER, null));
			sumDaily = sumDaily.add(daily);
			sumGross = sumGross.add(gross);
			sumOvertime = sumOvertime.add(overtime);
			sumLoan = sumLoan.add(loan);
			sumNet = sumNet.add(net);
		}
		if (m.employees().isEmpty()) {
			PdfPCell none = boxed(TakkaFormat.NO_DATA, body, Element.ALIGN_LEFT, null);
			none.setColspan(columns);
			t.addCell(none);
		}
		PdfPCell label = boxed("JUMLAH TOTAL", body, Element.ALIGN_CENTER, null);
		label.setColspan(h + 1);
		label.setMinimumHeight(ABSENSI_ROW);
		t.addCell(label);
		t.addCell(rupiah(dashed(sumDaily), body));
		t.addCell(rupiah(dashed(sumGross), body));
		t.addCell(rupiah(dashed(sumOvertime), body));
		t.addCell(rupiah(dashed(sumLoan), body));
		t.addCell(rupiah(dashed(sumNet), body));
		t.addCell(rupiah("-", body));
		document.add(t);

		// Kotak TOTAL UPAH dan pengesahan; catatan per toko tetap ditulis tangan seperti di foto.
		PdfPTable closing = table(widths, FULL_WIDTH);
		closing.setSpacingBefore(4f);
		blank(closing, h + 4);
		PdfPCell boxLabel = boxed("TOTAL UPAH", large, Element.ALIGN_CENTER, null);
		boxLabel.setMinimumHeight(24f);
		closing.addCell(boxLabel);
		PdfPCell boxAmount = rupiah(amount(sumNet), large);
		boxAmount.setColspan(2);
		closing.addCell(boxAmount);
		PdfPCell approve = free("Yang Menyetujui,", body, Element.ALIGN_CENTER, 2);
		approve.setPaddingTop(10f);
		closing.addCell(approve);
		blank(closing, columns - 2);
		PdfPCell director = free("Direktur Operasional", body, Element.ALIGN_CENTER, 2);
		director.setPaddingTop(44f);
		closing.addCell(director);
		blank(closing, columns - 2);
		document.add(closing);
	}

	private void lemburan(Document document, ReportModel m) throws DocumentException {
		List<LocalDate> dates = m.dates();
		int n = dates.size();
		float[] widths = new float[n + 5];
		widths[0] = 24f;
		widths[1] = 168f;
		for (int i = 0; i < n; i++) {
			widths[2 + i] = 42f;
		}
		widths[2 + n] = 40f;
		widths[3 + n] = 122f;
		widths[4 + n] = 130f;
		int columns = widths.length;

		PdfPTable kop = table(widths, LEMBURAN_WIDTH);
		kop.addCell(free("LEMBURAN", title, Element.ALIGN_CENTER, columns));
		kopRow(kop, n, columns, "PEKERJAAN", TakkaFormat.WEEKLY_JOB, 3, null, null);
		kopRow(kop, n, columns, "LOKASI", TakkaFormat.WEEKLY_LOCATION, 5, null, null);
		kop.setSpacingAfter(3f);
		document.add(kop);

		PdfPTable t = table(widths, LEMBURAN_WIDTH);
		t.setHeaderRows(1);
		PdfPCell no = boxed("No", body, Element.ALIGN_CENTER, null);
		no.setMinimumHeight(28f);
		t.addCell(no);
		t.addCell(boxed("NAMA", body, Element.ALIGN_CENTER, null));
		for (LocalDate d : dates) {
			t.addCell(boxed(TakkaFormat.dayLetter(d), body, Element.ALIGN_CENTER, null));
		}
		t.addCell(boxed("JMLH\nHARI", body, Element.ALIGN_CENTER, null));
		t.addCell(boxed("UANG LEMBUR", body, Element.ALIGN_CENTER, null));
		t.addCell(boxed("JUMLAH LEMBURAN", body, Element.ALIGN_CENTER, null));

		int number = 1;
		BigDecimal sum = BigDecimal.ZERO;
		for (ReportEmployee e : m.employees()) {
			identity(t, number++, e, LEMBURAN_ROW);
			for (LocalDate d : dates) {
				t.addCell(boxed(TakkaFormat.hoursText(TakkaFormat.overtime(e, d)), hours, Element.ALIGN_CENTER, null));
			}
			t.addCell(boxed(TakkaFormat.hoursText(e.totals().validatedOvertimeHours()), hours, Element.ALIGN_CENTER,
					null));
			ReportFinancial f = e.financial();
			t.addCell(rupiah(f == null || f.overtimeRate() == null ? null : amount(f.overtimeRate()), body));
			BigDecimal wage = TakkaFormat.nz(f == null ? null : f.overtimeWage());
			t.addCell(rupiah(dashed(wage), body));
			sum = sum.add(wage);
		}
		if (m.employees().isEmpty()) {
			PdfPCell none = boxed(TakkaFormat.NO_DATA, body, Element.ALIGN_LEFT, null);
			none.setColspan(columns);
			t.addCell(none);
		}
		// Baris kosong bergaris lalu baris JUMLAH TOTAL; nominal total menempati kedua baris.
		for (int c = 0; c < columns - 1; c++) {
			PdfPCell empty = boxed("", body, Element.ALIGN_CENTER, null);
			empty.setMinimumHeight(LEMBURAN_ROW);
			t.addCell(empty);
		}
		PdfPCell total = rupiah(dashed(sum), body);
		total.setRowspan(2);
		t.addCell(total);
		PdfPCell label = boxed("JUMLAH TOTAL", body, Element.ALIGN_CENTER, null);
		label.setColspan(columns - 1);
		label.setMinimumHeight(LEMBURAN_ROW);
		t.addCell(label);
		document.add(t);
	}

	/**
	 * Baris kop dengan kisi kolom yang sama seperti tabel: label, titik dua di kolom hari pertama, nilai berpusat di
	 * kolom S keempat ({@code valueFrom} 3) atau R ({@code valueFrom} 5), lalu PERIODE/MINGGU bila ada.
	 */
	private void kopRow(PdfPTable kop, int n, int columns, String label, String value, int valueFrom, String rightLabel,
			String rightValue) {
		int h = 2 + n;
		int lastValue = Math.min(7, rightLabel == null ? columns - 1 : h + 2);
		int from = Math.min(valueFrom, lastValue);
		kop.addCell(free(label, body, Element.ALIGN_LEFT, 2));
		kop.addCell(free(":", body, Element.ALIGN_CENTER, 1));
		blank(kop, from - 3);
		kop.addCell(free(value, body, Element.ALIGN_CENTER, lastValue - from + 1));
		if (rightLabel == null) {
			blank(kop, columns - lastValue - 1);
			return;
		}
		blank(kop, h + 3 - lastValue - 1);
		kop.addCell(free(rightLabel, body, Element.ALIGN_LEFT, 1));
		kop.addCell(free(rightValue, body, Element.ALIGN_CENTER, 1));
		blank(kop, columns - (h + 5));
	}

	private void identity(PdfPTable t, int no, ReportEmployee e, float rowHeight) {
		PdfPCell number = boxed(String.valueOf(no), body, Element.ALIGN_CENTER, null);
		number.setMinimumHeight(rowHeight);
		t.addCell(number);
		t.addCell(boxed(TakkaFormat.upper(e.fullName()), body, Element.ALIGN_CENTER, null));
	}

	private PdfPCell spanning(String value) {
		PdfPCell c = boxed(value, body, Element.ALIGN_CENTER, null);
		c.setRowspan(2);
		return c;
	}

	private static PdfPTable table(float[] widths, float total) throws DocumentException {
		PdfPTable t = new PdfPTable(widths);
		t.setTotalWidth(total);
		t.setLockedWidth(true);
		t.setHorizontalAlignment(Element.ALIGN_CENTER);
		t.setSplitRows(false);
		return t;
	}

	private void blank(PdfPTable t, int colspan) {
		if (colspan > 0) {
			t.addCell(free("", body, Element.ALIGN_LEFT, colspan));
		}
	}

	private static PdfPCell free(String value, Font font, int align, int colspan) {
		PdfPCell c = new PdfPCell(new Phrase(value, font));
		c.setBorder(Rectangle.NO_BORDER);
		c.setHorizontalAlignment(align);
		c.setColspan(colspan);
		c.setNoWrap(true);
		c.setPaddingBottom(5f);
		return c;
	}

	private static PdfPCell boxed(String value, Font font, int align, Color fill) {
		PdfPCell c = new PdfPCell(new Phrase(value, font));
		c.setHorizontalAlignment(align);
		c.setVerticalAlignment(Element.ALIGN_MIDDLE);
		c.setPadding(2f);
		c.setBorderWidth(0.5f);
		c.setBorderColor(Color.BLACK);
		if (fill != null) {
			c.setBackgroundColor(fill);
		}
		return c;
	}

	/** "Rp" rata kiri dan angka rata kanan dalam satu sel; null menjadi sel kosong bergaris. */
	private PdfPCell rupiah(String value, Font font) {
		if (value == null) {
			return boxed("", font, Element.ALIGN_RIGHT, null);
		}
		PdfPTable inner = new PdfPTable(new float[] { 1f, 3f });
		inner.setWidthPercentage(100);
		for (PdfPCell part : List.of(innerCell("Rp", font, Element.ALIGN_LEFT), innerCell(value, font, Element.ALIGN_RIGHT))) {
			inner.addCell(part);
		}
		PdfPCell outer = new PdfPCell(inner);
		outer.setPadding(2f);
		outer.setBorderWidth(0.5f);
		outer.setBorderColor(Color.BLACK);
		outer.setVerticalAlignment(Element.ALIGN_MIDDLE);
		return outer;
	}

	private static PdfPCell innerCell(String value, Font font, int align) {
		PdfPCell c = new PdfPCell(new Phrase(value, font));
		c.setBorder(Rectangle.NO_BORDER);
		c.setHorizontalAlignment(align);
		c.setPadding(0f);
		c.setNoWrap(true);
		return c;
	}

	private static String amount(BigDecimal value) {
		return TakkaFormat.amountText(value);
	}

	private static String dashed(BigDecimal value) {
		return value.signum() == 0 ? "-" : TakkaFormat.amountText(value);
	}

	private static Color color(String hex) {
		return new Color(Integer.parseInt(hex, 16));
	}

}
