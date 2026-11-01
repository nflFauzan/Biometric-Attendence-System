package com.biometricattendance.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportModel;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

/** PDF AS-1 dan AS-2 harus sesuai foto format klien. Semua nama di sini samaran. */
class TakkaWeeklyPdfTests {

	private final PdfReportWriter writer = new PdfReportWriter("Asia/Jakarta");

	private List<String> pages(ReportModel model) throws Exception {
		PdfReader reader = new PdfReader(writer.write(model));
		PdfTextExtractor extractor = new PdfTextExtractor(reader);
		List<String> result = new ArrayList<>();
		for (int i = 1; i <= reader.getNumberOfPages(); i++) {
			result.add(extractor.getTextFromPage(i));
		}
		reader.close();
		return result;
	}

	@Test
	void absensiPageMatchesPhoto() throws Exception {
		String page = pages(TakkaWeeklySheetsTests.model()).get(0);
		assertThat(page).contains("ABSENSI", "PEKERJAAN", "Upah Supir dan Kenek", "LOKASI", "TOKO", "PERIODE",
				"26 SEPTEMBER - 02 OKTOBER 2026", "MINGGU", "KE - 1", "NAMA", "JMLH", "INPUT PIUTANG", "SISA UPAH",
				"INSENTIF", "SUPIR TIGA", "612.000", "JUMLAH TOTAL", "TOTAL UPAH", "2.898.000", "Yang Menyetujui,",
				"Direktur Operasional");
		assertThat(page).doesNotContain("Periode:", "Filter", "Dicetak", "Halaman", "Kasbon");
	}

	@Test
	void lemburanPageMatchesPhoto() throws Exception {
		List<String> pages = pages(TakkaWeeklySheetsTests.model());
		assertThat(pages).hasSize(2);
		assertThat(pages.get(1)).contains("LEMBURAN", "PEKERJAAN", "LOKASI", "UANG LEMBUR", "JUMLAH LEMBURAN", "2,5",
				"14.000", "224.000", "JUMLAH TOTAL", "1.078.000");
		assertThat(pages.get(1)).doesNotContain("PERIODE", "Direktur");
	}

	@Test
	void emptyReportKeepsBothPages() throws Exception {
		ReportModel empty = new ReportModel(ReportType.UPAH_MINGGUAN, "t", "p", List.of(),
				TakkaWeeklySheetsTests.week(), List.<ReportEmployee>of(), List.of());
		List<String> pages = pages(empty);
		assertThat(pages).hasSize(2);
		assertThat(pages.get(0)).contains("ABSENSI", TakkaFormat.NO_DATA);
	}

}
