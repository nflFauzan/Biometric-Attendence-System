package com.biometricattendance.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.report.ReportDtos.ReportDay;
import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportModel;
import com.biometricattendance.report.ReportDtos.ReportTotals;

/** Penulis Excel: model dibuat langsung, hasil dibaca ulang dengan XSSFWorkbook. */
class ExcelReportWriterTests {

	private static final LocalDate D1 = LocalDate.parse("2026-10-05");

	private final ExcelReportWriter writer = new ExcelReportWriter();

	/** Posisi sel judul kolom: baris dan kolom. */
	private record Pos(int row, int col) {
	}

	private static ReportDay day(int offset, String dayName, AttendanceStatus status, String mark, String hours,
			String ot) {
		LocalDate d = D1.plusDays(offset);
		boolean has = status == AttendanceStatus.LENGKAP;
		return new ReportDay(d, dayName, status, has ? LocalTime.of(8, 0) : null, has ? LocalTime.of(16, 30) : null,
				new BigDecimal(hours), new BigDecimal(ot),
				has ? List.of(LocalTime.of(8, 0), LocalTime.of(16, 30)) : List.of(), mark);
	}

	private static ReportModel model(ReportType type, String name) {
		List<ReportDay> days = List.of(
				day(0, "Senin", AttendanceStatus.LENGKAP, "V", "7.5", "1.25"),
				day(1, "Selasa", AttendanceStatus.TIDAK_HADIR, "", "0", "0"),
				day(2, "Rabu", AttendanceStatus.TIDAK_ADA_SCAN, "", "0", "0"),
				day(3, "Kamis", AttendanceStatus.IZIN, "I", "0", "0"),
				day(4, "Jumat", null, "", "0", "0"));
		ReportEmployee e = new ReportEmployee("EMP-001", name, "Cabang A", "Finance & Accounting", days,
				new ReportTotals(1, 1, 1, new BigDecimal("7.5"), new BigDecimal("1.25")));
		return new ReportModel(type, type.title(), "05-10-2026 s.d. 09-10-2026", List.of("Cabang: Cabang A"),
				days.stream().map(ReportDay::date).toList(), List.of(e));
	}

	private static XSSFWorkbook read(byte[] bytes) throws Exception {
		return new XSSFWorkbook(new ByteArrayInputStream(bytes));
	}

	private static String rgb(Cell c) {
		XSSFColor col = ((XSSFCell) c).getCellStyle().getFillForegroundXSSFColor();
		return col == null ? null : col.getARGBHex();
	}

	private static Pos find(Sheet s, String header) {
		for (int r = 0; r <= Math.min(s.getLastRowNum(), 10); r++) {
			Row row = s.getRow(r);
			if (row == null) {
				continue;
			}
			for (Cell c : row) {
				if (c.getCellType() == CellType.STRING && c.getStringCellValue().equals(header)) {
					return new Pos(r, c.getColumnIndex());
				}
			}
		}
		throw new AssertionError("Judul kolom tidak ditemukan: " + header);
	}

	@Test
	void rekapHasRekapAndDetailSheets() throws Exception {
		try (XSSFWorkbook wb = read(writer.write(model(ReportType.REKAP_ABSENSI, "Budi")))) {
			assertThat(wb.getSheetName(0)).isEqualTo("Rekap");
			assertThat(wb.getSheetName(1)).isEqualTo("Detail");
			XSSFSheet rekap = wb.getSheet("Rekap");
			assertThat(rekap.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Rekap Absensi");
			assertThat(rekap.getPaneInformation()).isNotNull();
			find(rekap, "Hari lengkap");
			find(rekap, "Jam kerja normal");
			XSSFSheet detail = wb.getSheet("Detail");
			Pos hdr = find(detail, "Tanggal");
			for (String h : List.of("Hari", "Nama", "Kategori", "Jam masuk", "Jam keluar", "Jam kerja normal",
					"Lembur tervalidasi", "Status")) {
				find(detail, h);
			}
			assertThat(detail.getLastRowNum() - hdr.row()).isEqualTo(5);
			assertThat(detail.getRow(hdr.row() + 1).getCell(0).getStringCellValue()).isEqualTo("05-10-2026");
			assertThat(detail.getRow(hdr.row() + 1).getCell(1).getStringCellValue()).isEqualTo("Senin");
		}
	}

	@Test
	void markCellsMatchStatus() throws Exception {
		try (XSSFWorkbook wb = read(writer.write(model(ReportType.REKAP_ABSENSI, "Budi")))) {
			XSSFSheet s = wb.getSheet("Rekap");
			Pos p = find(s, "05-10-2026");
			Row row = s.getRow(p.row() + 1);
			int col = p.col();
			assertThat(row.getCell(col).getStringCellValue()).isEqualTo("V");
			assertThat(rgb(row.getCell(col))).isNull();
			assertThat(row.getCell(col + 3).getStringCellValue()).isEqualTo("I");
			Cell redCell = row.getCell(col + 1);
			Cell greyCell = row.getCell(col + 2);
			assertThat(redCell.getStringCellValue()).isEmpty();
			assertThat(redCell.getCellStyle().getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
			assertThat(greyCell.getCellStyle().getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
			String red = rgb(redCell);
			String grey = rgb(greyCell);
			assertThat(red).isNotNull().isNotEqualTo(grey);
			assertThat(grey).isNotNull();
			// merah: R tinggi, G dan B rendah
			int r = Integer.parseInt(red.substring(2, 4), 16);
			int g = Integer.parseInt(red.substring(4, 6), 16);
			assertThat(r).isGreaterThan(g + 60);
			// hari di luar masa kerja (status null) abu-abu dan kosong
			Cell outCell = row.getCell(col + 4);
			assertThat(outCell.getStringCellValue()).isEmpty();
			assertThat(rgb(outCell)).isEqualTo(grey);
		}
	}

	@Test
	void hoursAreNumericCells() throws Exception {
		try (XSSFWorkbook wb = read(writer.write(model(ReportType.REKAP_ABSENSI, "Budi")))) {
			XSSFSheet s = wb.getSheet("Rekap");
			Pos p = find(s, "Jam kerja normal");
			Cell c = s.getRow(p.row() + 1).getCell(p.col());
			assertThat(c.getCellType()).isEqualTo(CellType.NUMERIC);
			assertThat(c.getNumericCellValue()).isEqualTo(7.5);
			assertThat(c.getCellStyle().getDataFormatString()).isEqualTo("0.00");
			Pos ot = find(s, "Lembur tervalidasi");
			assertThat(s.getRow(ot.row() + 1).getCell(ot.col()).getNumericCellValue()).isEqualTo(1.25);
		}
	}

	@Test
	void emptyModelProducesValidWorkbookWithNoDataText() throws Exception {
		for (ReportType type : ReportType.values()) {
			ReportModel m = new ReportModel(type, type.title(), "05-10-2026 s.d. 09-10-2026", List.of(), List.of(),
					List.of());
			try (XSSFWorkbook wb = read(writer.write(m))) {
				assertThat(wb.getNumberOfSheets()).isGreaterThan(0);
				StringBuilder all = new StringBuilder();
				for (Sheet sh : wb) {
					for (Row r : sh) {
						for (Cell c : r) {
							if (c.getCellType() == CellType.STRING) {
								all.append(c.getStringCellValue()).append('|');
							}
						}
					}
				}
				assertThat(all.toString()).as(type.name()).contains("Tidak ada data");
			}
		}
	}

	@Test
	void specialCharactersInNamesSurvive() throws Exception {
		String name = "Siti 'Ani' & Rekan Ä";
		for (ReportType type : ReportType.values()) {
			try (XSSFWorkbook wb = read(writer.write(model(type, name)))) {
				boolean found = false;
				for (Sheet sh : wb) {
					for (Row r : sh) {
						for (Cell c : r) {
							if (c.getCellType() == CellType.STRING && c.getStringCellValue().equals(name)) {
								found = true;
							}
						}
					}
				}
				assertThat(found).as(type.name()).isTrue();
			}
		}
	}

	@Test
	void overtimeReportBlanksZeroAndShowsTotal() throws Exception {
		try (XSSFWorkbook wb = read(writer.write(model(ReportType.REKAP_LEMBUR, "Budi")))) {
			XSSFSheet s = wb.getSheet("Rekap Lembur");
			Pos p = find(s, "05-10-2026");
			Row row = s.getRow(p.row() + 1);
			assertThat(row.getCell(p.col()).getNumericCellValue()).isEqualTo(1.25);
			assertThat(row.getCell(p.col() + 1) == null || row.getCell(p.col() + 1).getCellType() == CellType.BLANK)
					.isTrue();
			Pos tot = find(s, "Total lembur");
			assertThat(row.getCell(tot.col()).getNumericCellValue()).isEqualTo(1.25);
		}
	}

	@Test
	void detailReportListsScanTimesOnSeparateLines() throws Exception {
		try (XSSFWorkbook wb = read(writer.write(model(ReportType.DETAIL_ABSENSI, "Budi")))) {
			XSSFSheet s = wb.getSheet("Detail Absensi");
			Pos p = find(s, "05-10-2026");
			Cell c = s.getRow(p.row() + 1).getCell(p.col());
			assertThat(c.getStringCellValue()).isEqualTo("08:00\n16:30");
			assertThat(c.getCellStyle().getWrapText()).isTrue();
		}
	}

}
