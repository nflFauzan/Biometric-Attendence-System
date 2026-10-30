package com.biometricattendance.report;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.biometricattendance.report.ReportDtos.ReportDay;
import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportModel;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Merender {@link ReportModel} menjadi PDF A4 landscape dengan font yang di-embed (Open Sans, sama dengan surat
 * izin). Hanya merender: isi sel kisi, nama hari, dan angka jam sudah dihitung di model. Kisi tanggal dipecah
 * per paling banyak 31 kolom (15 untuk detail scan agar jam muat), kolom identitas diulang di tiap pecahan, dan
 * kolom total hanya di pecahan terakhir.
 */
@Component
public class PdfReportWriter {

	private static final Logger log = LoggerFactory.getLogger(PdfReportWriter.class);

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private static final DateTimeFormatter PRINTED = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

	private static final String NO_DATA = "Tidak ada data pada rentang dan filter ini.";

	private static final int MAX_DATE_COLS = 31;

	private static final int MAX_SCAN_COLS = 15;

	private static final float MARGIN_SIDE = 20f;

	private static final float MARGIN_TOP = 62f;

	private static final float MARGIN_BOTTOM = 36f;

	private static final float[] FIXED_WIDTHS = { 40f, 92f, 56f, 40f };

	private static final String[] FIXED_HEADERS = { "No. karyawan", "Nama", "Kategori", "Cabang" };

	private static final Color RED = new Color(0xFF, 0x99, 0x99);

	private static final Color GREY = new Color(0xD9, 0xD9, 0xD9);

	private static final Color HEADER_FILL = new Color(0xDD, 0xE6, 0xF0);

	private static final Color BORDER = new Color(0x88, 0x88, 0x88);

	private static final String REGULAR = "fonts/OpenSans-Regular.ttf";

	private static final String SEMIBOLD = "fonts/OpenSans-SemiBold.ttf";

	static {
		System.setProperty("java.awt.headless", "true");
	}

	private final ZoneId zone;

	private final byte[] regularTtf;

	private final byte[] semiboldTtf;

	public PdfReportWriter(@Value("${app.company-timezone}") String companyZone) {
		this.zone = ZoneId.of(companyZone);
		this.regularTtf = read(REGULAR);
		this.semiboldTtf = read(SEMIBOLD);
		if (regularTtf == null || semiboldTtf == null) {
			log.warn("Font laporan tidak lengkap di classpath; memakai Helvetica bawaan");
		}
	}

	private static byte[] read(String path) {
		try (InputStream in = PdfReportWriter.class.getClassLoader().getResourceAsStream(path)) {
			return in == null ? null : in.readAllBytes();
		}
		catch (IOException ex) {
			log.warn("Font {} tidak bisa dibaca: {}", path, ex.getMessage());
			return null;
		}
	}

	private BaseFont create(String path, byte[] ttf, boolean bold) throws DocumentException, IOException {
		if (ttf != null) {
			// cached=false: tiap render punya instance sendiri sehingga render bersamaan tidak berbagi status glyph.
			return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, false, ttf, null);
		}
		return BaseFont.createFont(bold ? BaseFont.HELVETICA_BOLD : BaseFont.HELVETICA, BaseFont.WINANSI,
				BaseFont.NOT_EMBEDDED);
	}

	private record Ctx(BaseFont regular, BaseFont strong) {

		Font font(BaseFont base, float size) {
			return new Font(base, size, Font.NORMAL, Color.BLACK);
		}

	}

	public byte[] write(ReportModel model) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Document document = new Document(PageSize.A4.rotate(), MARGIN_SIDE, MARGIN_SIDE, MARGIN_TOP, MARGIN_BOTTOM);
		try {
			Ctx ctx = new Ctx(create(REGULAR, regularTtf, false), create(SEMIBOLD, semiboldTtf, true));
			PdfWriter writer = PdfWriter.getInstance(document, out);
			writer.setPageEvent(new PageDecor(model, ctx, ZonedDateTime.now(zone)));
			document.addTitle(model.title());
			document.open();
			List<ReportEmployee> employees = model.employees() == null ? List.of() : model.employees();
			if (employees.isEmpty()) {
				document.add(new Paragraph(NO_DATA, ctx.font(ctx.regular(), 9f)));
			}
			else {
				grid(document, ctx, model, employees);
			}
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

	private void grid(Document document, Ctx ctx, ReportModel m, List<ReportEmployee> employees)
			throws DocumentException {
		boolean scan = m.type() == ReportType.DETAIL_ABSENSI;
		int chunk = scan ? MAX_SCAN_COLS : MAX_DATE_COLS;
		List<LocalDate> dates = m.dates() == null ? List.of() : m.dates();
		int total = dates.size();
		int chunks = Math.max(1, (total + chunk - 1) / chunk);
		for (int c = 0; c < chunks; c++) {
			int from = c * chunk;
			int to = Math.min(total, from + chunk);
			boolean last = c == chunks - 1;
			if (c > 0) {
				document.newPage();
			}
			if (total > 0) {
				String part = chunks > 1 ? " (bagian " + (c + 1) + " dari " + chunks + ")" : "";
				document.add(new Paragraph("Tanggal " + DATE.format(dates.get(from)) + " s.d. "
						+ DATE.format(dates.get(to - 1)) + part, ctx.font(ctx.strong(), 8f)));
			}
			document.add(table(ctx, m, employees, from, to, last));
		}
	}

	private PdfPTable table(Ctx ctx, ReportModel m, List<ReportEmployee> employees, int from, int to,
			boolean last) throws DocumentException {
		ReportType type = m.type();
		String[] totalHeaders = totalHeaders(type);
		int n = to - from;
		int nTotals = last ? totalHeaders.length : 0;
		float pageWidth = PageSize.A4.getHeight() - 2 * MARGIN_SIDE;
		float fixed = sum(FIXED_WIDTHS);
		float totalsEach = type == ReportType.REKAP_ABSENSI ? 38f : 40f;
		float dateW = n == 0 ? 0f : Math.min((pageWidth - fixed - nTotals * totalsEach) / n, 48f);
		float[] widths = new float[FIXED_WIDTHS.length + n + nTotals];
		System.arraycopy(FIXED_WIDTHS, 0, widths, 0, FIXED_WIDTHS.length);
		for (int i = 0; i < n; i++) {
			widths[FIXED_WIDTHS.length + i] = dateW;
		}
		for (int i = 0; i < nTotals; i++) {
			widths[FIXED_WIDTHS.length + n + i] = totalsEach;
		}
		PdfPTable t = new PdfPTable(widths);
		t.setTotalWidth(sum(widths));
		t.setLockedWidth(true);
		t.setHorizontalAlignment(Element.ALIGN_LEFT);
		t.setHeaderRows(1);
		// baris tidak boleh terbelah antar halaman; baris yang tidak muat pindah utuh ke halaman berikutnya
		t.setSplitRows(false);
		t.setSplitLate(true);

		Font hf = ctx.font(ctx.strong(), 5.5f);
		Font bf = ctx.font(ctx.regular(), 6f);
		Font df = ctx.font(ctx.regular(), type == ReportType.REKAP_ABSENSI ? 6.5f : type == ReportType.REKAP_LEMBUR ? 5f : 5.5f);

		for (String h : FIXED_HEADERS) {
			t.addCell(cell(h, hf, Element.ALIGN_CENTER, HEADER_FILL));
		}
		for (int i = from; i < to; i++) {
			LocalDate d = m.dates().get(i);
			t.addCell(cell(String.format(Locale.ROOT, "%02d", d.getDayOfMonth()) + "\n" + String.format(Locale.ROOT, "%02d", d.getMonthValue()),
					hf, Element.ALIGN_CENTER, HEADER_FILL));
		}
		if (last) {
			for (String h : totalHeaders) {
				t.addCell(cell(h, hf, Element.ALIGN_CENTER, HEADER_FILL));
			}
		}

		for (ReportEmployee e : employees) {
			t.addCell(cell(e.employeeNumber(), bf, Element.ALIGN_LEFT, null));
			t.addCell(cell(e.fullName(), bf, Element.ALIGN_LEFT, null));
			t.addCell(cell(e.categoryName(), bf, Element.ALIGN_LEFT, null));
			t.addCell(cell(e.branchName(), bf, Element.ALIGN_LEFT, null));
			List<ReportDay> days = e.days() == null ? List.of() : e.days();
			for (int i = from; i < to; i++) {
				ReportDay d = i < days.size() ? days.get(i) : null;
				t.addCell(dayCell(type, d, df));
			}
			if (last) {
				var tot = e.totals();
				switch (type) {
					case REKAP_ABSENSI -> {
						t.addCell(cell(String.valueOf(tot.countingDays()), bf, Element.ALIGN_CENTER, null));
						t.addCell(cell(String.valueOf(tot.leaveDays()), bf, Element.ALIGN_CENTER, null));
						t.addCell(cell(String.valueOf(tot.absentDays()), bf, Element.ALIGN_CENTER, null));
						t.addCell(cell(hours(tot.countingHours()), bf, Element.ALIGN_RIGHT, null));
						t.addCell(cell(hours(tot.validatedOvertimeHours()), bf, Element.ALIGN_RIGHT, null));
					}
					case REKAP_LEMBUR ->
						t.addCell(cell(hours(tot.validatedOvertimeHours()), bf, Element.ALIGN_RIGHT, null));
					case DETAIL_ABSENSI -> {
					}
				}
			}
		}
		return t;
	}

	private PdfPCell dayCell(ReportType type, ReportDay d, Font font) {
		switch (type) {
			case REKAP_ABSENSI -> {
				if (d == null || d.status() == null) {
					return cell("", font, Element.ALIGN_CENTER, GREY);
				}
				Color fill = switch (d.status()) {
					case TIDAK_HADIR -> RED;
					case TIDAK_ADA_SCAN -> GREY;
					default -> null;
				};
				return cell(d.mark() == null ? "" : d.mark(), font, Element.ALIGN_CENTER, fill);
			}
			case REKAP_LEMBUR -> {
				BigDecimal ot = d == null ? null : d.validatedOvertimeHours();
				PdfPCell oc = cell(ot != null && ot.signum() != 0 ? hours(ot) : "", font, Element.ALIGN_CENTER, null);
				oc.setNoWrap(true);
				return oc;
			}
			default -> {
				List<LocalTime> scans = d == null || d.scanTimes() == null ? List.of() : d.scanTimes();
				return cell(scans.stream().map(TIME::format).collect(Collectors.joining("\n")), font,
						Element.ALIGN_CENTER, null);
			}
		}
	}

	private static String[] totalHeaders(ReportType type) {
		return switch (type) {
			case REKAP_ABSENSI -> new String[] { "Hari lengkap", "Izin", "Tidak hadir", "Jam kerja normal",
					"Lembur tervalidasi" };
			case REKAP_LEMBUR -> new String[] { "Total lembur" };
			case DETAIL_ABSENSI -> new String[0];
		};
	}

	/** Jam desimal 2 angka dengan koma (7,50); hanya format tampilan. */
	private static String hours(BigDecimal v) {
		return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
	}

	private static PdfPCell cell(String text, Font font, int align, Color fill) {
		PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
		c.setHorizontalAlignment(align);
		c.setVerticalAlignment(Element.ALIGN_MIDDLE);
		c.setPaddingLeft(0.8f);
		c.setPaddingRight(0.8f);
		c.setPaddingTop(1.5f);
		c.setPaddingBottom(1.5f);
		c.setBorderWidth(0.4f);
		c.setBorderColor(BORDER);
		if (fill != null) {
			c.setBackgroundColor(fill);
		}
		return c;
	}

	private static float sum(float[] w) {
		float s = 0f;
		for (float f : w) {
			s += f;
		}
		return s;
	}

	/** Kepala (judul, periode, filter) dan kaki (nomor halaman, waktu cetak) di setiap halaman. */
	private static final class PageDecor extends PdfPageEventHelper {

		private final ReportModel model;

		private final Ctx ctx;

		private final String printed;

		PageDecor(ReportModel model, Ctx ctx, ZonedDateTime now) {
			this.model = model;
			this.ctx = ctx;
			this.printed = "Dicetak: " + PRINTED.format(now) + " " + now.getZone().getId();
		}

		@Override
		public void onEndPage(PdfWriter writer, Document document) {
			PdfContentByte cb = writer.getDirectContent();
			Rectangle page = document.getPageSize();
			float left = MARGIN_SIDE;
			float right = page.getWidth() - MARGIN_SIDE;
			float top = page.getHeight() - 24f;
			List<String> filters = model.filterLabels() == null ? List.of() : new ArrayList<>(model.filterLabels());
			String filterLine = filters.isEmpty() ? "Filter: semua" : "Filter: " + String.join("; ", filters);
			cb.beginText();
			cb.setFontAndSize(ctx.strong(), 13f);
			cb.showTextAligned(PdfContentByte.ALIGN_LEFT, nz(model.title()), left, top, 0);
			cb.setFontAndSize(ctx.regular(), 8f);
			cb.showTextAligned(PdfContentByte.ALIGN_LEFT, "Periode: " + nz(model.periodLabel()), left, top - 13f, 0);
			cb.showTextAligned(PdfContentByte.ALIGN_LEFT, filterLine, left, top - 24f, 0);
			float footY = 20f;
			cb.showTextAligned(PdfContentByte.ALIGN_LEFT, printed, left, footY, 0);
			cb.showTextAligned(PdfContentByte.ALIGN_RIGHT, "Halaman " + writer.getPageNumber(), right, footY, 0);
			cb.endText();
		}

		private static String nz(String s) {
			return s == null ? "" : s;
		}

	}

}
