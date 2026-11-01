package com.biometricattendance.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportModel;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

/** PDF AS-5 dan AS-6 memakai kop dan kolom format klien, tanpa kepala/kaki laporan umum. */
class TakkaPdfPagesTests {

	private static final YearMonth SEP = YearMonth.of(2026, 9);

	private final PdfReportWriter writer = new PdfReportWriter("Asia/Jakarta");

	private List<String> pages(ReportModel model) throws Exception {
		PdfReader reader = new PdfReader(writer.write(model));
		PdfTextExtractor extractor = new PdfTextExtractor(reader);
		List<String> out = new ArrayList<>();
		for (int i = 1; i <= reader.getNumberOfPages(); i++) {
			out.add(extractor.getTextFromPage(i));
		}
		reader.close();
		return out;
	}

	private ReportEmployee staff(String name) {
		LocalDate first = LocalDate.of(2026, 9, 1);
		return TakkaExcelSheetsTests.employee(name, SEP, TakkaExcelSheetsTests.financial("4040000"),
				d -> d.date().equals(first) ? TakkaExcelSheetsTests.day(first, AttendanceStatus.LENGKAP, "1.50") : d);
	}

	@Test
	void salaryPageMatchesPhoto() throws Exception {
		String page = pages(TakkaExcelSheetsTests.model(SEP, List.of(staff("STAF SATU")))).get(0);
		assertThat(page).contains("TAKKA STEEL", "Manager Name:", "MANAGER SAMARAN", "Department:", "Month:",
				"September 2026", "Employee Name", "Total", "Days", "UPAH", "LEMBUR", "INPUT", "PIUTANG", "Wages", "Rp",
				"4.040.000", "TOTAL", "Director Signature:", "Manager Signature:");
		assertThat(page).doesNotContain("Filter:", "Dicetak:", "Periode:");
	}

	@Test
	void overtimePageMatchesPhoto() throws Exception {
		List<String> pages = pages(TakkaExcelSheetsTests.model(SEP, List.of(staff("STAF SATU"))));
		String page = pages.get(pages.size() - 1);
		assertThat(page).contains("REKAP LEMBUR TAKKA STEEL", "NAMA KARYAWAN", "TOTAL LEMBUR", "UANG LEMBUR",
				"JUMLAH UPAH", "1,5", "Director Signature:");
		assertThat(page).doesNotContain("Manager Signature:");
	}

	@Test
	void manyLongNamesRepeatHeaderOnEveryPage() throws Exception {
		List<ReportEmployee> rows = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			rows.add(staff("KARYAWAN DENGAN NAMA YANG SANGAT PANJANG SEKALI NOMOR " + i));
		}
		List<String> pages = pages(TakkaExcelSheetsTests.model(SEP, rows));
		assertThat(pages.size()).isGreaterThan(2);
		long salaryPages = pages.stream().filter(p -> p.contains("Employee Name")).count();
		long overtimePages = pages.stream().filter(p -> p.contains("NAMA KARYAWAN")).count();
		assertThat(salaryPages + overtimePages).isEqualTo(pages.size());
		assertThat(salaryPages).isGreaterThan(1);
	}

	@Test
	void signatureIsNeverAloneOnAPage() throws Exception {
		for (int count = 15; count <= 40; count++) {
			List<ReportEmployee> rows = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				rows.add(staff("STAF SAMARAN " + i));
			}
			for (String page : pages(TakkaExcelSheetsTests.model(SEP, rows))) {
				if (page.contains("Signature:")) {
					assertThat(page).as("%d karyawan", count).contains("STAF SAMARAN");
				}
			}
		}
	}

	@Test
	void emptyReportKeepsKop() throws Exception {
		String page = pages(TakkaExcelSheetsTests.model(SEP, List.of())).get(0);
		assertThat(page).contains("TAKKA STEEL", "Tidak ada data pada rentang dan filter ini.");
	}

	@Test
	void zeroOvertimeWageShowsDash() throws Exception {
		ReportEmployee none = TakkaExcelSheetsTests.employee("STAF NOL", SEP, TakkaExcelSheetsTests.financial("1"),
				d -> d);
		List<String> pages = pages(TakkaExcelSheetsTests.model(SEP, List.of(none)));
		assertThat(pages.get(pages.size() - 1)).containsPattern("Rp\\s*-");
	}

}
