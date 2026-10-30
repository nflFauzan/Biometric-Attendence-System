package com.biometricattendance.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.report.ReportDtos.ReportDay;
import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportModel;
import com.biometricattendance.report.ReportDtos.ReportTotals;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

/** Penulis PDF: model dibuat langsung, teks diekstrak ulang dengan PdfTextExtractor. */
class PdfReportWriterTests {

	private static final LocalDate D1 = LocalDate.parse("2026-10-05");

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private static final String[] DAY_NAMES = { "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu" };

	private final PdfReportWriter writer = new PdfReportWriter("Asia/Jakarta");

	private static ReportDay day(LocalDate d, AttendanceStatus status, String mark) {
		boolean has = status == AttendanceStatus.LENGKAP;
		return new ReportDay(d, DAY_NAMES[d.getDayOfWeek().getValue() - 1], status, has ? LocalTime.of(8, 0) : null,
				has ? LocalTime.of(16, 30) : null, new BigDecimal(has ? "7.5" : "0"),
				new BigDecimal(has ? "1.25" : "0"), has ? List.of(LocalTime.of(8, 0), LocalTime.of(16, 30)) : List.of(),
				mark);
	}

	private static ReportModel model(ReportType type, String name, int dayCount) {
		List<ReportDay> days = new ArrayList<>();
		for (int i = 0; i < dayCount; i++) {
			days.add(i % 2 == 0 ? day(D1.plusDays(i), AttendanceStatus.LENGKAP, "V")
					: day(D1.plusDays(i), AttendanceStatus.TIDAK_HADIR, ""));
		}
		ReportEmployee e = new ReportEmployee("EMP-001", name, "Cabang A", "Finance & Accounting", days,
				new ReportTotals(1, 0, 1, new BigDecimal("7.5"), new BigDecimal("1.25")));
		LocalDate last = D1.plusDays(dayCount - 1L);
		return new ReportModel(type, type.title(), DATE.format(D1) + " s.d. " + DATE.format(last),
				List.of("Cabang: Cabang A"), days.stream().map(ReportDay::date).toList(), List.of(e));
	}

	private static String text(byte[] pdf) throws Exception {
		PdfReader reader = new PdfReader(pdf);
		try {
			PdfTextExtractor extractor = new PdfTextExtractor(reader);
			StringBuilder all = new StringBuilder();
			for (int page = 1; page <= reader.getNumberOfPages(); page++) {
				all.append(extractor.getTextFromPage(page)).append(' ');
			}
			return all.toString().replaceAll("\\s+", " ");
		}
		finally {
			reader.close();
		}
	}

	private static int pages(byte[] pdf) throws Exception {
		PdfReader reader = new PdfReader(pdf);
		try {
			return reader.getNumberOfPages();
		}
		finally {
			reader.close();
		}
	}

	@Test
	void pdfContainsTitlePeriodAndEmployeeNames() throws Exception {
		byte[] pdf = writer.write(model(ReportType.REKAP_ABSENSI, "Budi Santoso", 5));
		assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
		String t = text(pdf);
		assertThat(t).contains("Rekap Absensi").contains("05-10-2026 s.d. 09-10-2026").contains("Cabang: Cabang A")
				.contains("Budi Santoso").contains("EMP-001").contains("Halaman 1").contains("Dicetak");
	}

	@Test
	void emptyModelProducesPdfWithNoDataText() throws Exception {
		ReportModel empty = new ReportModel(ReportType.REKAP_LEMBUR, "Rekap Lembur", "05-10-2026 s.d. 09-10-2026",
				List.of("Cabang: Cabang A"), List.of(D1), List.of());
		String t = text(writer.write(empty));
		assertThat(t).contains("Rekap Lembur").contains("Filter: Cabang: Cabang A").contains("Tidak ada data pada rentang dan filter ini.");
	}

	@Test
	void specialCharactersRenderWithEmbeddedFont() throws Exception {
		String name = "Siti N'Diaye & Sons Jos\u00e9 \u00c9mile M\u00fcller";
		for (ReportType type : ReportType.values()) {
			String t = text(writer.write(model(type, name, 3)));
			// nama panjang bisa terlipat di sel sempit, jadi tiap kata diperiksa sendiri
			for (String token : new String[] { "N'Diaye", "Sons", "José", "Émile", "Müller" }) {
				assertThat(t).as(type.name() + " " + token).contains(token);
			}
			assertThat(t).as(type.name()).contains("Siti").contains("&").contains("Finance");
		}
		byte[] pdf = writer.write(model(ReportType.REKAP_ABSENSI, name, 3));
		assertThat(new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1)).contains("/FontFile2");
	}

	@Test
	void sixtyTwoDayRangeSplitsAcrossPages() throws Exception {
		for (ReportType type : ReportType.values()) {
			byte[] pdf = writer.write(model(type, "Budi Santoso", 62));
			String t = text(pdf);
			assertThat(pages(pdf)).as(type.name()).isGreaterThan(1);
			// tanggal pertama dan terakhir tetap muncul (keterangan rentang tiap kisi memuat tanggal lengkap)
			assertThat(t).contains("05-10-2026").contains(DATE.format(D1.plusDays(61)));
			// setiap tanggal ada di salah satu kisi
			for (int i = 0; i < 62; i++) {
				String dd = String.format(java.util.Locale.ROOT, "%02d", D1.plusDays(i).getDayOfMonth());
				assertThat(t).contains(dd);
			}
		}
	}

	private static String pageText(PdfReader reader, int page) throws Exception {
		return new PdfTextExtractor(reader).getTextFromPage(page).replaceAll("\\s+", " ");
	}

	@Test
	void tallScanRowsAreNotSplitAcrossPages() throws Exception {
		List<ReportEmployee> employees = new ArrayList<>();
		List<LocalDate> dates = new ArrayList<>();
		for (int i = 0; i < 15; i++) {
			dates.add(D1.plusDays(i));
		}
		for (int e = 0; e < 30; e++) {
			List<ReportDay> days = new ArrayList<>();
			for (int i = 0; i < 15; i++) {
				// jam unik per karyawan: menit = e, jam 6..9
				List<LocalTime> scans = List.of(LocalTime.of(6, e), LocalTime.of(7, e), LocalTime.of(8, e),
						LocalTime.of(9, e));
				days.add(new ReportDay(dates.get(i), "Senin", AttendanceStatus.LENGKAP, scans.get(0), scans.get(3),
						BigDecimal.ZERO, BigDecimal.ZERO, scans, "V"));
			}
			employees.add(new ReportEmployee(String.format(java.util.Locale.ROOT, "EMP-%03d", e),
					String.format(java.util.Locale.ROOT, "Karyawan Zq%02d", e), "Cabang A", "Operator", days,
					new ReportTotals(15, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO)));
		}
		ReportModel m = new ReportModel(ReportType.DETAIL_ABSENSI, "Detail Absensi", "05-10-2026 s.d. 19-10-2026",
				List.of(), dates, employees);
		byte[] pdf = writer.write(m);
		PdfReader reader = new PdfReader(pdf);
		try {
			assertThat(reader.getNumberOfPages()).isGreaterThan(1);
			for (int e = 0; e < 30; e++) {
				String name = String.format(java.util.Locale.ROOT, "Karyawan Zq%02d", e);
				String lastScan = String.format(java.util.Locale.ROOT, "09:%02d", e);
				int namePage = -1;
				int scanPages = 0;
				for (int p = 1; p <= reader.getNumberOfPages(); p++) {
					String t = pageText(reader, p);
					if (t.contains(name)) {
						namePage = p;
					}
					// jam keluar karyawan ini (menit unik) harus ada di halaman yang sama dengan namanya
					if (t.contains(lastScan) && t.contains(name)) {
						scanPages++;
					}
				}
				assertThat(namePage).as(name).isPositive();
				assertThat(scanPages).as("scan " + name + " satu halaman dengan nama").isEqualTo(1);
				String samePage = pageText(reader, namePage);
				for (int h = 6; h <= 9; h++) {
					assertThat(samePage).contains(String.format(java.util.Locale.ROOT, "%02d:%02d", h, e));
				}
			}
		}
		finally {
			reader.close();
		}
	}

	@Test
	void overtimeValuesFitOnOneLineInThirtyOneColumnChunk() throws Exception {
		List<ReportDay> days = new ArrayList<>();
		for (int i = 0; i < 31; i++) {
			days.add(new ReportDay(D1.plusDays(i), "Senin", AttendanceStatus.LENGKAP, null, null, BigDecimal.ZERO,
					new BigDecimal("99.99"), List.of(), "V"));
		}
		ReportEmployee e = new ReportEmployee("E1", "Ani", "Cabang A", "Operator", days,
				new ReportTotals(0, 0, 0, BigDecimal.ZERO, new BigDecimal("99.99")));
		ReportModel m = new ReportModel(ReportType.REKAP_LEMBUR, "Rekap Lembur", "p", List.of(),
				days.stream().map(ReportDay::date).toList(), List.of(e));
		String t = text(writer.write(m));
		int count = (t.length() - t.replace("99,99", "").length()) / 5;
		assertThat(count).isEqualTo(32);
	}

	@Test
	void nullStatusAndNullFieldsDoNotBreakRendering() throws Exception {
		ReportDay outside = new ReportDay(D1, "Senin", null, null, null, null, null, null, null);
		ReportEmployee e = new ReportEmployee("E2", "Ani", null, null, List.of(outside),
				new ReportTotals(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO));
		for (ReportType type : ReportType.values()) {
			ReportModel m = new ReportModel(type, type.title(), "05-10-2026 s.d. 05-10-2026", null, List.of(D1),
					List.of(e));
			assertThat(text(writer.write(m))).contains("Ani");
		}
	}

}
