package com.biometricattendance.employee;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.biometricattendance.common.ApiException;

/**
 * Membaca template "Data Karyawan" dari client (K-130) dan merapikan isinya: spasi, nomor HP, serial tanggal Excel,
 * dan catatan yang terselip di alamat. Tidak menyentuh database; validasi terhadap data yang ada ada di
 * {@link EmployeeImportService}.
 */
public final class EmployeeImportParser {

	public static final String USERNAME_DOMAIN = "@takka.id";

	static final int MAX_ROWS = 500;

	static final Set<String> UNITS = Set.of("Office", "Supir", "Kenek");

	static final Set<String> ACCOUNT_STATUSES = Set.of("Aktif", "Nonaktif", "Belum dibuat");

	/** Awalan nama yang dilewati saat mengusulkan username ("Muhammad Dafa" → dafa, "Siti Balqis" → balqis). */
	private static final Set<String> NAME_PREFIXES = Set.of("muhammad", "muhamad", "mohammad", "mohamad", "moh", "muh",
			"m", "siti");

	private static final String ID = "ID Karyawan";
	private static final String NAME = "Nama Lengkap";
	private static final String UNIT = "Unit/Departemen";
	private static final String POSITION = "Jabatan";
	private static final String BRANCH = "Lokasi/Cabang Kerja";
	private static final String PHONE = "No HP";
	private static final String EMAIL = "Email";
	private static final String GENDER = "Jenis Kelamin";
	private static final String BIRTH_DATE = "Tanggal Lahir";
	private static final String ADDRESS = "Alamat Domisili";
	private static final String STATUS = "Status Akun";
	private static final String NOTES = "Catatan";

	private static final List<String> REQUIRED_HEADERS = List.of(ID, NAME, UNIT, BRANCH, STATUS);

	private static final Map<String, Gender> GENDERS = Map.of("laki-laki", Gender.LAKI_LAKI, "perempuan",
			Gender.PEREMPUAN, "tidak ingin menyebutkan", Gender.TIDAK_DISEBUTKAN);

	private static final List<DateTimeFormatter> DATE_FORMATS = List.of(DateTimeFormatter.ofPattern("d/M/uuuu"),
			DateTimeFormatter.ISO_LOCAL_DATE);

	private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

	private EmployeeImportParser() {
	}

	public record ParsedRow(int rowNumber, String employeeNumber, String fullName, String unit, String position,
			String branchName, String phone, String email, Gender gender, LocalDate birthDate, String address,
			String notes, String accountStatus, List<String> errors) {
	}

	public static List<ParsedRow> parse(InputStream xlsx) {
		try (Workbook workbook = new XSSFWorkbook(xlsx)) {
			Sheet sheet = workbook.getSheet("Data Karyawan");
			if (sheet == null) {
				sheet = workbook.getSheetAt(0);
			}
			return parse(sheet);
		}
		catch (ApiException e) {
			throw e;
		}
		catch (IOException | RuntimeException e) {
			throw ApiException.badRequest("File bukan Excel .xlsx yang valid");
		}
	}

	private static List<ParsedRow> parse(Sheet sheet) {
		DataFormatter formatter = new DataFormatter(Locale.ROOT);
		Map<String, Integer> columns = headerColumns(sheet.getRow(sheet.getFirstRowNum()), formatter);
		List<ParsedRow> rows = new ArrayList<>();
		for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
			Row row = sheet.getRow(r);
			if (row == null || isEmpty(row, columns, formatter)) {
				continue;
			}
			if (rows.size() == MAX_ROWS) {
				throw ApiException.badRequest("Maksimal " + MAX_ROWS + " karyawan per impor");
			}
			rows.add(parseRow(row, columns, formatter));
		}
		return rows;
	}

	private static Map<String, Integer> headerColumns(Row header, DataFormatter formatter) {
		Map<String, Integer> columns = new HashMap<>();
		if (header != null) {
			for (Cell cell : header) {
				String name = clean(formatter.formatCellValue(cell));
				if (name != null) {
					columns.put(name.replace("[OPSIONAL]", "").trim().toLowerCase(Locale.ROOT), cell.getColumnIndex());
				}
			}
		}
		for (String required : REQUIRED_HEADERS) {
			if (!columns.containsKey(required.toLowerCase(Locale.ROOT))) {
				throw ApiException.badRequest("Kolom '" + required + "' tidak ditemukan di baris judul");
			}
		}
		return columns;
	}

	private static boolean isEmpty(Row row, Map<String, Integer> columns, DataFormatter formatter) {
		return columns.values().stream().allMatch(c -> clean(formatter.formatCellValue(row.getCell(c))) == null);
	}

	private static ParsedRow parseRow(Row row, Map<String, Integer> columns, DataFormatter formatter) {
		List<String> errors = new ArrayList<>();
		String employeeNumber = employeeNumber(cell(row, columns, ID), formatter);
		String fullName = text(row, columns, NAME, formatter);
		String unit = text(row, columns, UNIT, formatter);
		String branchName = text(row, columns, BRANCH, formatter);
		String status = text(row, columns, STATUS, formatter);
		if (employeeNumber == null) {
			errors.add("ID karyawan wajib diisi");
		}
		if (fullName == null) {
			errors.add("Nama wajib diisi");
		}
		if (unit == null) {
			errors.add("Unit wajib diisi");
		}
		else if (!UNITS.contains(unit)) {
			errors.add("Unit '" + unit + "' tidak dikenal");
		}
		if (branchName == null) {
			errors.add("Cabang wajib diisi");
		}
		if (status == null) {
			errors.add("Status akun wajib diisi");
		}
		else if (!ACCOUNT_STATUSES.contains(status)) {
			errors.add("Status akun '" + status + "' tidak dikenal");
		}

		Gender gender = null;
		String genderText = text(row, columns, GENDER, formatter);
		if (genderText != null) {
			gender = GENDERS.get(genderText.toLowerCase(Locale.ROOT));
			if (gender == null) {
				errors.add("Jenis kelamin '" + genderText + "' tidak dikenal");
			}
		}

		LocalDate birthDate = null;
		Cell birthCell = cell(row, columns, BIRTH_DATE);
		if (birthCell != null && clean(formatter.formatCellValue(birthCell)) != null) {
			birthDate = date(birthCell, formatter);
			if (birthDate == null) {
				errors.add("Tanggal lahir tidak terbaca");
			}
		}

		String address = null;
		String notes = text(row, columns, NOTES, formatter);
		Cell addressCell = cell(row, columns, ADDRESS);
		if (addressCell != null) {
			String[] lines = formatter.formatCellValue(addressCell).split("\\R", 2);
			address = clean(lines[0]);
			String extra = lines.length > 1 ? clean(lines[1]) : null;
			if (extra != null) {
				notes = notes == null ? extra : extra + "; " + notes;
			}
		}

		return new ParsedRow(row.getRowNum() + 1, employeeNumber, fullName, unit,
				text(row, columns, POSITION, formatter), branchName, phone(cell(row, columns, PHONE), formatter),
				text(row, columns, EMAIL, formatter), gender, birthDate, address, notes, status, List.copyOf(errors));
	}

	/** Usulan bagian depan username: kata pertama nama setelah awalan umum, huruf kecil a-z0-9. */
	public static String suggestUsername(String fullName) {
		String[] words = fullName.toLowerCase(Locale.ROOT).trim().split("\\s+");
		for (String word : words) {
			String plain = word.replaceAll("[^a-z0-9]", "");
			if (!plain.isEmpty() && !NAME_PREFIXES.contains(plain)) {
				return plain;
			}
		}
		return words[0].replaceAll("[^a-z0-9]", "");
	}

	private static Cell cell(Row row, Map<String, Integer> columns, String header) {
		Integer column = columns.get(header.toLowerCase(Locale.ROOT));
		return column == null ? null : row.getCell(column);
	}

	private static String text(Row row, Map<String, Integer> columns, String header, DataFormatter formatter) {
		Cell cell = cell(row, columns, header);
		return cell == null ? null : clean(formatter.formatCellValue(cell));
	}

	/** ID berupa angka (Excel menghapus nol di depan) dikembalikan ke tiga digit: 1 → 001. */
	private static String employeeNumber(Cell cell, DataFormatter formatter) {
		if (cell == null) {
			return null;
		}
		if (cell.getCellType() == CellType.NUMERIC) {
			return String.format("%03d", (long) cell.getNumericCellValue());
		}
		return clean(formatter.formatCellValue(cell));
	}

	/** Hanya digit; awalan 62 menjadi 0, dan 0 yang hilang karena sel angka dikembalikan (8xx → 08xx). */
	private static String phone(Cell cell, DataFormatter formatter) {
		if (cell == null) {
			return null;
		}
		String digits = cell.getCellType() == CellType.NUMERIC ? String.valueOf((long) cell.getNumericCellValue())
				: formatter.formatCellValue(cell).replaceAll("\\D", "");
		if (digits.isEmpty()) {
			return null;
		}
		if (digits.startsWith("62")) {
			digits = "0" + digits.substring(2);
		}
		else if (digits.startsWith("8")) {
			digits = "0" + digits;
		}
		return digits;
	}

	private static LocalDate date(Cell cell, DataFormatter formatter) {
		if (cell.getCellType() == CellType.NUMERIC) {
			if (DateUtil.isCellDateFormatted(cell)) {
				return cell.getLocalDateTimeCellValue().toLocalDate();
			}
			return EXCEL_EPOCH.plusDays((long) cell.getNumericCellValue());
		}
		String value = clean(formatter.formatCellValue(cell));
		for (DateTimeFormatter format : DATE_FORMATS) {
			try {
				return LocalDate.parse(value, format);
			}
			catch (DateTimeParseException e) {
				// coba format berikutnya
			}
		}
		return null;
	}

	/** Trim dan ringkas spasi berderet; string kosong menjadi null. */
	static String clean(String value) {
		if (value == null) {
			return null;
		}
		String cleaned = value.replaceAll("[\\s\\u00A0]+", " ").trim();
		return cleaned.isEmpty() ? null : cleaned;
	}

}
