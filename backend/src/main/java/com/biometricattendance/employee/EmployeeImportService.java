package com.biometricattendance.employee;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.account.Role;
import com.biometricattendance.account.UserAccountDtos;
import com.biometricattendance.account.UserAccountDtos.CreateUserRequest;
import com.biometricattendance.account.UserAccountDtos.UpdateUserRequest;
import com.biometricattendance.account.UserAccountDtos.UserResponse;
import com.biometricattendance.account.UserAccountRepository;
import com.biometricattendance.account.UserAccountService;
import com.biometricattendance.audit.AuditService;
import com.biometricattendance.branch.Branch;
import com.biometricattendance.branch.BranchRepository;
import com.biometricattendance.category.EmployeeCategory;
import com.biometricattendance.category.EmployeeCategoryRepository;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.employee.EmployeeDtos.EmployeeRequest;
import com.biometricattendance.employee.EmployeeDtos.EmployeeResponse;
import com.biometricattendance.employee.EmployeeImportDtos.CommitRequest;
import com.biometricattendance.employee.EmployeeImportDtos.CommitResponse;
import com.biometricattendance.employee.EmployeeImportDtos.CreatedAccount;
import com.biometricattendance.employee.EmployeeImportDtos.PreviewResponse;
import com.biometricattendance.employee.EmployeeImportDtos.PreviewRow;
import com.biometricattendance.employee.EmployeeImportParser.ParsedRow;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Impor karyawan dari template client (K-130). Pratinjau tidak menyimpan apa pun; simpan memvalidasi ulang semua
 * baris lalu membuat karyawan dan akun dalam satu transaksi lewat {@link EmployeeService} dan
 * {@link UserAccountService}, sehingga audit dan aturan yang sama tetap berlaku.
 */
@Service
public class EmployeeImportService {

	static final String ENTITY = "EMPLOYEE_IMPORT";

	private static final ZoneId ZONE = ZoneId.of("Asia/Jakarta");

	private static final String STATUS_ACTIVE = "Aktif";

	private static final String STATUS_NO_ACCOUNT = "Belum dibuat";

	/** Unit di template client → kode kategori karyawan (V1). Staf Gudang ber-unit Office, jadi bulanan. */
	private static final Map<String, String> UNIT_CATEGORY_CODES = Map.of("Office", "KANTOR", "Supir", "SUPIR_KENEK",
			"Kenek", "SUPIR_KENEK");

	private final EmployeeService employees;
	private final EmployeeRepository employeeRepository;
	private final UserAccountService accounts;
	private final UserAccountRepository accountRepository;
	private final BranchRepository branches;
	private final EmployeeCategoryRepository categories;
	private final AuditService audit;
	private final Validator validator;

	public EmployeeImportService(EmployeeService employees, EmployeeRepository employeeRepository,
			UserAccountService accounts, UserAccountRepository accountRepository, BranchRepository branches,
			EmployeeCategoryRepository categories, AuditService audit, Validator validator) {
		this.employees = employees;
		this.employeeRepository = employeeRepository;
		this.accounts = accounts;
		this.accountRepository = accountRepository;
		this.branches = branches;
		this.categories = categories;
		this.audit = audit;
		this.validator = validator;
	}

	@Transactional(readOnly = true)
	public PreviewResponse preview(InputStream xlsx, LocalDate startDate) {
		LocalDate start = startDate != null ? startDate : LocalDate.now(ZONE).withDayOfMonth(1);
		List<PreviewRow> rows = EmployeeImportParser.parse(xlsx).stream().map(EmployeeImportService::toPreviewRow)
			.toList();
		rows = validateAll(rows, start, lookup(), true);
		return new PreviewResponse(start, rows, errorCount(rows));
	}

	@Transactional
	public CommitResponse commit(CommitRequest request) {
		Lookup lookup = lookup();
		List<PreviewRow> rows = validateAll(request.rows(), request.startDate(), lookup, false);
		int errors = errorCount(rows);
		if (errors > 0) {
			throw ApiException.conflict("Ada " + errors + " baris bermasalah; periksa ulang di pratinjau")
				.withProperty("rows", rows);
		}
		List<CreatedAccount> created = new ArrayList<>();
		for (PreviewRow row : rows) {
			EmployeeResponse employee = employees
				.create(employeeRequest(row, request.startDate(), lookup.branch(row), lookup.category(row)));
			if (!needsAccount(row)) {
				continue;
			}
			String username = normalizeUsername(row.username());
			String password = initialPassword(username, row.employeeNumber().trim());
			UserResponse account = accounts.create(new CreateUserRequest(username, password, row.role(), employee.id()));
			if (!STATUS_ACTIVE.equals(row.accountStatus())) {
				accounts.update(account.id(), new UpdateUserRequest(row.role(), employee.id(), false));
			}
			created.add(new CreatedAccount(employee.employeeNumber(), employee.fullName(), username, row.role(),
					password));
		}
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("startDate", request.startDate());
		summary.put("employeesCreated", rows.size());
		summary.put("accountsCreated", created.size());
		summary.put("employeeNumbers", rows.stream().map(r -> r.employeeNumber().trim()).toList());
		audit.record("IMPORT", ENTITY, null, null, summary);
		return new CommitResponse(rows.size(), created.size(), created);
	}

	/** Password awal sesuai K-131: bagian depan username + "-" + ID karyawan, mis. harfianti-002. */
	static String initialPassword(String username, String employeeNumber) {
		return username.split("@", 2)[0] + "-" + employeeNumber;
	}

	private static PreviewRow toPreviewRow(ParsedRow p) {
		String username = p.fullName() == null ? null
				: EmployeeImportParser.suggestUsername(p.fullName()) + EmployeeImportParser.USERNAME_DOMAIN;
		return new PreviewRow(p.rowNumber(), p.employeeNumber(), p.fullName(), p.unit(), null, null, p.position(),
				null, p.branchName(), p.phone(), p.email(), p.gender(), p.birthDate(), p.address(), p.notes(),
				p.accountStatus(), username, Role.EMPLOYEE, p.errors());
	}

	/**
	 * Mengisi cabang/kategori dari nama dan menghitung ulang error setiap baris. Pada pratinjau error parser ikut
	 * ditampilkan; pada simpan, error yang dikirim klien diabaikan dan dihitung ulang dari isi baris.
	 */
	private List<PreviewRow> validateAll(List<PreviewRow> rows, LocalDate startDate, Lookup lookup,
			boolean keepParserErrors) {
		Map<String, Long> numberCounts = rows.stream()
			.filter(r -> !isBlank(r.employeeNumber()))
			.collect(Collectors.groupingBy(r -> r.employeeNumber().trim().toLowerCase(Locale.ROOT),
					Collectors.counting()));
		Map<String, Long> usernameCounts = rows.stream()
			.filter(r -> needsAccount(r) && !isBlank(r.username()))
			.collect(Collectors.groupingBy(r -> normalizeUsername(r.username()), Collectors.counting()));
		List<PreviewRow> result = new ArrayList<>();
		for (PreviewRow row : rows) {
			List<String> errors = new ArrayList<>();
			if (keepParserErrors && row.errors() != null) {
				errors.addAll(row.errors());
			}
			Branch branch = lookup.branch(row);
			EmployeeCategory category = lookup.category(row);
			errors.addAll(errors(row, branch, category, startDate, numberCounts));
			if (needsAccount(row)) {
				errors.addAll(accountErrors(row, usernameCounts));
			}
			result.add(new PreviewRow(row.rowNumber(), row.employeeNumber(), row.fullName(), row.unit(),
					category == null ? null : category.getId(), category == null ? null : category.getName(),
					row.position(), branch == null ? null : branch.getId(),
					branch == null ? row.branchName() : branch.getName(), row.phone(), row.email(), row.gender(),
					row.birthDate(), row.address(), row.notes(), row.accountStatus(), row.username(), row.role(),
					errors.stream().distinct().toList()));
		}
		return result;
	}

	private List<String> errors(PreviewRow row, Branch branch, EmployeeCategory category, LocalDate startDate,
			Map<String, Long> numberCounts) {
		List<String> errors = new ArrayList<>();
		String number = isBlank(row.employeeNumber()) ? null : row.employeeNumber().trim();
		if (number == null) {
			errors.add("ID karyawan wajib diisi");
		}
		else if (employeeRepository.existsByEmployeeNumberIgnoreCase(number)) {
			errors.add("ID karyawan " + number + " sudah dipakai");
		}
		else if (numberCounts.getOrDefault(number.toLowerCase(Locale.ROOT), 0L) > 1) {
			errors.add("ID karyawan " + number + " kembar di file ini");
		}
		else if (employeeRepository.existsByDevicePin(number)) {
			errors.add("PIN mesin " + number + " sudah dipakai karyawan lain");
		}
		if (isBlank(row.fullName())) {
			errors.add("Nama wajib diisi");
		}
		if (isBlank(row.branchName())) {
			errors.add("Cabang wajib diisi");
		}
		else if (branch == null) {
			errors.add("Cabang '" + row.branchName() + "' tidak ditemukan");
		}
		if (isBlank(row.unit())) {
			errors.add("Unit wajib diisi");
		}
		else if (!EmployeeImportParser.UNITS.contains(row.unit().trim())) {
			errors.add("Unit '" + row.unit() + "' tidak dikenal");
		}
		else if (category == null) {
			errors.add("Kategori untuk unit '" + row.unit() + "' belum ada");
		}
		if (isBlank(row.accountStatus())) {
			errors.add("Status akun wajib diisi");
		}
		else if (!EmployeeImportParser.ACCOUNT_STATUSES.contains(row.accountStatus())) {
			errors.add("Status akun '" + row.accountStatus() + "' tidak dikenal");
		}
		if (number != null && !isBlank(row.fullName()) && branch != null && category != null) {
			// Aturan field yang sama dengan form karyawan: email, tanggal lahir, panjang teks, pola PIN.
			errors.addAll(messages(validator.validate(employeeRequest(row, startDate, branch, category))));
		}
		return errors;
	}

	private List<String> accountErrors(PreviewRow row, Map<String, Long> usernameCounts) {
		List<String> errors = new ArrayList<>();
		if (row.role() == null) {
			errors.add("Role wajib dipilih");
		}
		if (isBlank(row.username())) {
			errors.add("Username wajib diisi");
			return errors;
		}
		String username = normalizeUsername(row.username());
		if (usernameCounts.getOrDefault(username, 0L) > 1) {
			errors.add("Username " + username + " kembar di file ini");
		}
		else if (accountRepository.existsByUsername(username)) {
			errors.add("Username " + username + " sudah dipakai");
		}
		if (!username.endsWith(EmployeeImportParser.USERNAME_DOMAIN)) {
			errors.add("Username harus berakhiran " + EmployeeImportParser.USERNAME_DOMAIN);
		}
		if (!username.matches(UserAccountDtos.USERNAME_PATTERN)) {
			errors.add(UserAccountDtos.USERNAME_MESSAGE);
		}
		String number = isBlank(row.employeeNumber()) ? "" : row.employeeNumber().trim();
		String password = initialPassword(username, number);
		if (password.length() < UserAccountDtos.MIN_PASSWORD_LENGTH) {
			errors.add("Password awal " + password + " kurang dari " + UserAccountDtos.MIN_PASSWORD_LENGTH
					+ " karakter; perpanjang username");
		}
		return errors;
	}

	/** Tanggal masuk karyawan impor = tanggal mulai pemakaian sistem; PIN mesin = ID karyawan (K-130). */
	private static EmployeeRequest employeeRequest(PreviewRow row, LocalDate startDate, Branch branch,
			EmployeeCategory category) {
		String number = row.employeeNumber().trim();
		return new EmployeeRequest(number, row.fullName().trim(), row.phone(), branch.getId(), category.getId(),
				row.position(), startDate, null, STATUS_ACTIVE.equals(row.accountStatus()), number, row.email(),
				row.gender(), row.birthDate(), row.address(), row.notes());
	}

	private static List<String> messages(Set<ConstraintViolation<EmployeeRequest>> violations) {
		return violations.stream()
			.sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
			.map(ConstraintViolation::getMessage)
			.toList();
	}

	private static boolean needsAccount(PreviewRow row) {
		return !STATUS_NO_ACCOUNT.equals(row.accountStatus());
	}

	private static String normalizeUsername(String username) {
		return username.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static int errorCount(List<PreviewRow> rows) {
		return (int) rows.stream().filter(r -> !r.errors().isEmpty()).count();
	}

	private Lookup lookup() {
		Map<String, Branch> branchByName = new HashMap<>();
		branches.findAllByOrderByNameAsc()
			.stream()
			.filter(Branch::isActive)
			.forEach(b -> branchByName.putIfAbsent(b.getName().trim().toLowerCase(Locale.ROOT), b));
		Map<String, EmployeeCategory> byCode = categories.findAll()
			.stream()
			.filter(EmployeeCategory::isActive)
			.collect(Collectors.toMap(EmployeeCategory::getCode, Function.identity()));
		Map<String, EmployeeCategory> byUnit = new HashMap<>();
		UNIT_CATEGORY_CODES.forEach((unit, code) -> {
			if (byCode.containsKey(code)) {
				byUnit.put(unit, byCode.get(code));
			}
		});
		return new Lookup(branchByName, byUnit);
	}

	/** Cabang aktif per nama (huruf kecil) dan kategori per unit template. */
	private record Lookup(Map<String, Branch> branches, Map<String, EmployeeCategory> categories) {

		Branch branch(PreviewRow row) {
			return isBlank(row.branchName()) ? null : branches.get(row.branchName().trim().toLowerCase(Locale.ROOT));
		}

		EmployeeCategory category(PreviewRow row) {
			return isBlank(row.unit()) ? null : categories.get(row.unit().trim());
		}

	}

}
