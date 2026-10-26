package com.biometricattendance.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.biometricattendance.common.ApiException;
import com.biometricattendance.employee.EmployeeImportParser.ParsedRow;

/** Parser template data karyawan client (K-130). Semua data di sini sintetis. */
class EmployeeImportParserTests {

	static final String[] HEADERS = { "ID Karyawan ", "Nama Lengkap ", "Unit/Departemen ", "Jabatan ",
			"Lokasi/Cabang Kerja ", "No HP", "Email [OPSIONAL]", "Jenis Kelamin [OPSIONAL]",
			"Tanggal Lahir [OPSIONAL]", "Alamat Domisili [OPSIONAL]", "Status Akun ", "Catatan [OPSIONAL]" };

	@Test
	void readsHeadersByNameNotPosition() throws IOException {
		Object[][] rows = { { "Status Akun", "Nama Lengkap", "ID Karyawan", "Lokasi/Cabang Kerja", "Unit/Departemen" },
				{ "Aktif", "Budi Contoh", "007", "Cabang A", "Supir" } };
		ParsedRow row = parse(rows).get(0);
		assertThat(row.employeeNumber()).isEqualTo("007");
		assertThat(row.fullName()).isEqualTo("Budi Contoh");
		assertThat(row.unit()).isEqualTo("Supir");
		assertThat(row.branchName()).isEqualTo("Cabang A");
		assertThat(row.accountStatus()).isEqualTo("Aktif");
		assertThat(row.errors()).isEmpty();
	}

	@Test
	void parsesNumericCellsAsText() throws IOException {
		ParsedRow row = parse(template(row(1, "Budi Contoh"))).get(0);
		assertThat(row.employeeNumber()).isEqualTo("001");
		assertThat(row.rowNumber()).isEqualTo(2);
	}

	@Test
	void normalizesPhoneAndBirthDate() throws IOException {
		Object[] a = row("001", "Budi Contoh");
		a[5] = 81234567890.0;
		a[8] = 32888.0;
		Object[] b = row("002", "Sari Contoh");
		b[5] = "+62 812-3456";
		b[8] = LocalDate.of(1995, 7, 17);
		Object[] c = row("003", "Tono Contoh");
		c[8] = "17/08/1990";
		List<ParsedRow> parsed = parse(template(a, b, c));
		assertThat(parsed.get(0).phone()).isEqualTo("081234567890");
		assertThat(parsed.get(0).birthDate()).isEqualTo(LocalDate.of(1990, 1, 15));
		assertThat(parsed.get(1).phone()).isEqualTo("08123456");
		assertThat(parsed.get(1).birthDate()).isEqualTo(LocalDate.of(1995, 7, 17));
		assertThat(parsed.get(2).birthDate()).isEqualTo(LocalDate.of(1990, 8, 17));
	}

	@Test
	void flagsUnreadableBirthDate() throws IOException {
		Object[] a = row("001", "Budi Contoh");
		a[8] = "kemarin";
		assertThat(parse(template(a)).get(0).errors()).containsExactly("Tanggal lahir tidak terbaca");
	}

	@Test
	void trimsAndCollapsesSpaces() throws IOException {
		ParsedRow row = parse(template(row("001", "  M   Rochmat  "))).get(0);
		assertThat(row.fullName()).isEqualTo("M Rochmat");
	}

	@Test
	void movesExtraAddressLinesToNotes() throws IOException {
		Object[] a = row("001", "Budi Contoh");
		a[9] = "Jl. A\nbeda dengan KTP";
		Object[] b = row("002", "Sari Contoh");
		b[9] = "Jl. B\nalamat lama";
		b[11] = "pindahan";
		List<ParsedRow> parsed = parse(template(a, b));
		assertThat(parsed.get(0).address()).isEqualTo("Jl. A");
		assertThat(parsed.get(0).notes()).isEqualTo("beda dengan KTP");
		assertThat(parsed.get(1).notes()).isEqualTo("alamat lama; pindahan");
	}

	@Test
	void flagsUnknownUnitAndGender() throws IOException {
		Object[] a = row("001", "Budi Contoh");
		a[2] = "Satpam";
		a[7] = "L";
		Object[] b = row("002", "Sari Contoh");
		b[7] = "Perempuan";
		List<ParsedRow> parsed = parse(template(a, b));
		assertThat(parsed.get(0).errors()).containsExactlyInAnyOrder("Unit 'Satpam' tidak dikenal",
				"Jenis kelamin 'L' tidak dikenal");
		assertThat(parsed.get(1).gender()).isEqualTo(Gender.PEREMPUAN);
	}

	@Test
	void flagsUnknownAccountStatusAndMissingRequiredValues() throws IOException {
		Object[] a = row("001", "");
		a[10] = "Mungkin";
		assertThat(parse(template(a)).get(0).errors()).containsExactlyInAnyOrder("Nama wajib diisi",
				"Status akun 'Mungkin' tidak dikenal");
	}

	@Test
	void skipsEmptyRows() throws IOException {
		assertThat(parse(template(row("001", "Budi Contoh"), new Object[12], row("002", "Sari Contoh"))))
			.extracting(ParsedRow::rowNumber)
			.containsExactly(2, 4);
	}

	@Test
	void suggestsUsernameSkippingMuhammadPrefix() {
		assertThat(EmployeeImportParser.suggestUsername("Muhammad Dafa Fakhrika")).isEqualTo("dafa");
		assertThat(EmployeeImportParser.suggestUsername("M Rochmat")).isEqualTo("rochmat");
		assertThat(EmployeeImportParser.suggestUsername("Harfianti Nur Wafa")).isEqualTo("harfianti");
		assertThat(EmployeeImportParser.suggestUsername("Siti Balqis Khaerani")).isEqualTo("balqis");
		assertThat(EmployeeImportParser.suggestUsername("Muhamad Fadil Abdulah")).isEqualTo("fadil");
		assertThat(EmployeeImportParser.suggestUsername("Muhammad")).isEqualTo("muhammad");
	}

	@Test
	void rejectsMissingRequiredHeader() {
		Object[][] rows = { { "Nama Lengkap", "Unit/Departemen", "Lokasi/Cabang Kerja", "Status Akun" },
				{ "Budi", "Supir", "Cabang A", "Aktif" } };
		assertThatThrownBy(() -> parse(rows)).isInstanceOf(ApiException.class)
			.hasMessage("Kolom 'ID Karyawan' tidak ditemukan di baris judul");
	}

	@Test
	void rejectsNonXlsxBytes() {
		assertThatThrownBy(() -> EmployeeImportParser
			.parse(new ByteArrayInputStream("bukan excel".getBytes(StandardCharsets.UTF_8))))
			.isInstanceOf(ApiException.class)
			.hasMessage("File bukan Excel .xlsx yang valid");
	}

	/** Satu baris template lengkap dan valid; ubah elemen array untuk kasus tertentu. */
	static Object[] row(Object id, String name) {
		return new Object[] { id, name, "Office", "Staf", "Cabang A", "081200000000", null, "Laki-laki", null,
				"Jl. Contoh", "Aktif", null };
	}

	static Object[][] template(Object[]... rows) {
		Object[][] all = new Object[rows.length + 1][];
		all[0] = HEADERS;
		System.arraycopy(rows, 0, all, 1, rows.length);
		return all;
	}

	static List<ParsedRow> parse(Object[][] rows) throws IOException {
		return EmployeeImportParser.parse(new ByteArrayInputStream(xlsx(rows)));
	}

	/** Membuat xlsx di memori: String → teks, Number → angka, LocalDate → sel tanggal berformat. */
	static byte[] xlsx(Object[][] rows) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("Data Karyawan");
			CellStyle dateStyle = workbook.createCellStyle();
			dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
			for (int r = 0; r < rows.length; r++) {
				Row row = sheet.createRow(r);
				for (int c = 0; c < rows[r].length; c++) {
					Object value = rows[r][c];
					if (value instanceof String s) {
						row.createCell(c).setCellValue(s);
					}
					else if (value instanceof Number n) {
						row.createCell(c).setCellValue(n.doubleValue());
					}
					else if (value instanceof LocalDate d) {
						row.createCell(c).setCellValue(d);
						row.getCell(c).setCellStyle(dateStyle);
					}
				}
			}
			workbook.write(out);
			return out.toByteArray();
		}
	}

}
