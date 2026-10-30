package com.biometricattendance.overtime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.account.UserAccount;
import com.biometricattendance.account.UserAccountRepository;
import com.biometricattendance.attendance.AttendanceDtos.DayRecord;
import com.biometricattendance.attendance.AttendanceDtos.EmployeeDays;
import com.biometricattendance.attendance.AttendanceService;
import com.biometricattendance.audit.AuditService;
import com.biometricattendance.calculation.CalculationDtos.DailyResultResponse;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;
import com.biometricattendance.overtime.OvertimeDtos.OvertimeRow;
import com.biometricattendance.overtime.OvertimeDtos.OvertimeStatus;
import com.biometricattendance.overtime.OvertimeDtos.ValidationItem;
import com.biometricattendance.overtime.OvertimeDtos.ValidationRequest;

/**
 * Validasi lembur (FR-REV-003, K-140). Kandidat lembur SELALU diambil dari rekap harian
 * ({@code DailyResultResponse.overtimeCandidateSeconds}); di sini tidak ada perhitungan lembur. Owner atau Finance &
 * Accounting menyetujui seluruhnya, sebagian, atau menolak (0 menit) dalam satuan menit tanpa pembulatan (Q-008).
 * Catatan wajib bila dikurangi atau ditolak. Setiap keputusan diaudit di transaksi yang sama.
 */
@Service
public class OvertimeService {

	static final String ENTITY = "OVERTIME_VALIDATION";
	static final String ACTION = "OVERTIME_VALIDATE";
	static final String CONFLICT = "Lembur ini baru saja divalidasi pengguna lain; muat ulang halaman";
	static final String STALE = "Kandidat lembur sudah berubah sejak halaman dimuat; muat ulang halaman";
	static final String TOO_MUCH = "Lembur disetujui tidak boleh melebihi kandidat";
	static final String NOTE_REQUIRED = "Catatan wajib bila lembur dikurangi atau ditolak";
	static final String FUTURE = "Tanggal lembur tidak boleh di masa depan";
	static final String NO_CANDIDATE = "Tidak ada kandidat lembur pada tanggal ini";
	static final String DUPLICATE = "Satu hari karyawan hanya boleh muncul sekali dalam satu validasi";

	private final AttendanceService attendance;
	private final OvertimeValidationRepository validations;
	private final EmployeeRepository employees;
	private final UserAccountRepository accounts;
	private final AuditService audit;

	public OvertimeService(AttendanceService attendance, OvertimeValidationRepository validations,
			EmployeeRepository employees, UserAccountRepository accounts, AuditService audit) {
		this.attendance = attendance;
		this.validations = validations;
		this.employees = employees;
		this.accounts = accounts;
		this.audit = audit;
	}

	/** Isi keputusan yang dicatat di audit (nilai sebelum dan sesudah). */
	record AuditView(Long employeeId, LocalDate workDate, long candidateSeconds, long approvedSeconds, String note,
			String validatedBy, Instant validatedAt) {

		static AuditView of(OvertimeValidation v) {
			return v == null ? null
					: new AuditView(v.getEmployeeId(), v.getWorkDate(), v.getCandidateSeconds(), v.getApprovedSeconds(),
							v.getNote(), v.getValidatedBy(), v.getValidatedAt());
		}

	}

	/**
	 * Hari dengan kandidat lembur, ditambah hari yang sudah punya validasi (mis. kandidatnya hilang setelah koreksi
	 * sehingga perlu divalidasi ulang). Terbaru lebih dulu, lalu nama karyawan.
	 */
	@Transactional(readOnly = true)
	public List<OvertimeRow> list(LocalDate from, LocalDate to, Long branchId, Long categoryId) {
		List<EmployeeDays> data = attendance.range(from, to, branchId, categoryId, null);
		Map<String, OvertimeValidation> existing = validationsOf(data);
		Function<String, String> nameOf = nameResolver(existing.values());
		List<OvertimeRow> rows = new ArrayList<>();
		for (EmployeeDays employee : data) {
			for (DayRecord day : employee.days()) {
				// Di luar masa kerja (sebelum tanggal masuk / setelah tanggal keluar) tidak ada lembur untuk divalidasi.
				if (!employee.employed(day.date())) {
					continue;
				}
				long candidate = candidateOf(day);
				OvertimeValidation validation = existing.get(key(employee.employeeId(), day.date()));
				if (candidate > 0 || validation != null) {
					rows.add(row(employee.employeeId(), employee.fullName(), employee.branchName(),
							employee.categoryName(), day, validation, nameOf));
				}
			}
		}
		rows.sort(Comparator.comparing(OvertimeRow::date).reversed().thenComparing(OvertimeRow::fullName));
		return rows;
	}

	/**
	 * Lembur yang sah untuk laporan: hanya hari berstatus {@link OvertimeStatus#TERVALIDASI}, berisi
	 * {@code approvedSeconds} (0 bila ditolak). Hari belum divalidasi atau perlu validasi ulang tidak dimuat.
	 */
	@Transactional(readOnly = true)
	public Map<Long, Map<LocalDate, Long>> validatedSeconds(List<EmployeeDays> data) {
		Map<String, OvertimeValidation> existing = validationsOf(data);
		Map<Long, Map<LocalDate, Long>> result = new LinkedHashMap<>();
		for (EmployeeDays employee : data) {
			for (DayRecord day : employee.days()) {
				if (!employee.employed(day.date())) {
					continue;
				}
				long candidate = candidateOf(day);
				OvertimeValidation validation = existing.get(key(employee.employeeId(), day.date()));
				if (validation != null) {
					if (statusOf(validation, candidate) == OvertimeStatus.TERVALIDASI) {
						result.computeIfAbsent(employee.employeeId(), id -> new LinkedHashMap<>())
							.put(day.date(), validation.getApprovedSeconds());
					}
				} else if (candidate > 0) {
					result.computeIfAbsent(employee.employeeId(), id -> new LinkedHashMap<>())
						.put(day.date(), candidate);
				}
			}
		}
		return result;
	}

	/**
	 * Menyimpan semua keputusan, atau tidak sama sekali: seluruh item diperiksa dulu. Data ditulis dengan upsert per
	 * (karyawan, tanggal); dua validator pada hari yang sama menghasilkan satu sukses dan satu 409.
	 */
	@Transactional
	public List<OvertimeRow> validate(ValidationRequest request) {
		LocalDate today = attendance.today();
		Set<String> seen = new HashSet<>();
		for (ValidationItem item : request.items()) {
			if (item.workDate().isAfter(today)) {
				throw ApiException.badRequest(FUTURE);
			}
			if (!seen.add(key(item.employeeId(), item.workDate()))) {
				throw ApiException.badRequest(DUPLICATE);
			}
		}
		Map<Long, Employee> byId = new HashMap<>();
		employees.findAllById(request.items().stream().map(ValidationItem::employeeId).distinct().toList())
			.forEach(e -> byId.put(e.getId(), e));
		for (ValidationItem item : request.items()) {
			if (!byId.containsKey(item.employeeId())) {
				throw ApiException.notFound("Karyawan");
			}
		}

		// Validasi yang ada dibaca sebelum rekap dihitung, agar dua permintaan bersamaan sama-sama melihat
		// "belum ada" dan bertabrakan di unique (employee_id, work_date), bukan saling menimpa.
		Map<String, OvertimeValidation> existing = existingFor(request.items());
		List<DayRecord> days = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		List<Long> approved = new ArrayList<>();
		for (ValidationItem item : request.items()) {
			DayRecord day = attendance.day(byId.get(item.employeeId()), item.workDate());
			long candidate = candidateOf(day);
			if (candidate != item.expectedCandidateSeconds()) {
				throw ApiException.conflict(STALE);
			}
			// Bilangan bulat dijamin @Digits(fraction = 0) dan maksimal 9 digit, jadi aman dari overflow.
			long minutes = item.approvedMinutes().longValueExact();
			long wholeMinutes = candidate / 60;
			if (minutes > wholeMinutes) {
				throw ApiException.badRequest(TOO_MUCH);
			}
			// Menit penuh kandidat = setuju seluruhnya: disimpan sebesar kandidat apa adanya (sisa detik ikut),
			// bukan pembulatan. Kurang dari itu adalah pengurangan dan wajib bercatatan.
			boolean full = minutes == wholeMinutes;
			String note = item.note() == null || item.note().isBlank() ? null : item.note().trim();
			if (!full && note == null) {
				throw ApiException.badRequest(NOTE_REQUIRED);
			}
			if (candidate == 0 && !existing.containsKey(key(item.employeeId(), item.workDate()))) {
				throw ApiException.badRequest(NO_CANDIDATE);
			}
			days.add(day);
			notes.add(note);
			approved.add(full ? candidate : minutes * 60);
		}

		String actor = CurrentUser.username();
		Instant now = Instant.now();
		List<AuditView> before = new ArrayList<>();
		List<OvertimeValidation> saved = new ArrayList<>();
		try {
			for (int i = 0; i < request.items().size(); i++) {
				ValidationItem item = request.items().get(i);
				OvertimeValidation current = existing.get(key(item.employeeId(), item.workDate()));
				before.add(AuditView.of(current));
				long candidate = candidateOf(days.get(i));
				if (current == null) {
					current = new OvertimeValidation(item.employeeId(), item.workDate());
					current.validate(candidate, approved.get(i), notes.get(i), actor, now);
					current = validations.save(current);
				}
				else {
					current.validate(candidate, approved.get(i), notes.get(i), actor, now);
				}
				saved.add(current);
			}
			// Flush di sini agar konflik (@Version atau unique) muncul di dalam request dan sebelum audit.
			validations.flush();
		}
		catch (OptimisticLockingFailureException | DataIntegrityViolationException ex) {
			throw ApiException.conflict(CONFLICT);
		}

		List<OvertimeRow> rows = new ArrayList<>();
		Function<String, String> nameOf = nameResolver(saved);
		for (int i = 0; i < saved.size(); i++) {
			OvertimeValidation v = saved.get(i);
			audit.record(actor, ACTION, ENTITY, v.getId(), before.get(i), AuditView.of(v), v.getNote());
			Employee e = byId.get(v.getEmployeeId());
			rows.add(row(e.getId(), e.getFullName(), e.getBranch().getName(),
					e.getCategory() == null ? null : e.getCategory().getName(), days.get(i), v, nameOf));
		}
		return rows;
	}

	static OvertimeStatus statusOf(OvertimeValidation validation, long candidate) {
		if (validation == null) {
			return OvertimeStatus.BELUM_DIVALIDASI;
		}
		return validation.getCandidateSeconds() == candidate ? OvertimeStatus.TERVALIDASI
				: OvertimeStatus.PERLU_VALIDASI_ULANG;
	}

	/** Kandidat dari kalkulator yang sudah ada; 0 bila tidak ada hasil (mis. aturan belum diatur). */
	static long candidateOf(DayRecord day) {
		DailyResultResponse result = day.result();
		return result == null || result.overtimeCandidateSeconds() == null ? 0 : result.overtimeCandidateSeconds();
	}

	private static OvertimeRow row(Long employeeId, String fullName, String branchName, String categoryName,
			DayRecord day, OvertimeValidation validation, Function<String, String> nameOf) {
		long candidate = candidateOf(day);
		DailyResultResponse result = day.result();
		LocalTime checkOut = result == null || result.checkOut() == null ? null : result.checkOut().time();
		return new OvertimeRow(employeeId, fullName, branchName, categoryName, day.date(), checkOut, candidate,
				validation == null ? null : validation.getApprovedSeconds(),
				validation == null ? null : validation.getNote(),
				validation == null ? null : nameOf.apply(validation.getValidatedBy()),
				validation == null ? null : validation.getValidatedAt(), statusOf(validation, candidate));
	}

	private Map<String, OvertimeValidation> validationsOf(List<EmployeeDays> data) {
		List<Long> ids = data.stream().map(EmployeeDays::employeeId).toList();
		LocalDate from = data.stream()
			.flatMap(e -> e.days().stream())
			.map(DayRecord::date)
			.min(Comparator.naturalOrder())
			.orElse(null);
		LocalDate to = data.stream()
			.flatMap(e -> e.days().stream())
			.map(DayRecord::date)
			.max(Comparator.naturalOrder())
			.orElse(null);
		if (ids.isEmpty() || from == null) {
			return Map.of();
		}
		return validations.findByEmployeeIdInAndWorkDateBetween(ids, from, to)
			.stream()
			.collect(Collectors.toMap(v -> key(v.getEmployeeId(), v.getWorkDate()), v -> v));
	}

	private Map<String, OvertimeValidation> existingFor(List<ValidationItem> items) {
		LocalDate from = items.stream().map(ValidationItem::workDate).min(Comparator.naturalOrder()).orElseThrow();
		LocalDate to = items.stream().map(ValidationItem::workDate).max(Comparator.naturalOrder()).orElseThrow();
		Map<String, OvertimeValidation> map = new HashMap<>();
		validations
			.findByEmployeeIdInAndWorkDateBetween(items.stream().map(ValidationItem::employeeId).distinct().toList(),
					from, to)
			.forEach(v -> map.put(key(v.getEmployeeId(), v.getWorkDate()), v));
		return map;
	}

	/** Nama karyawan yang terhubung ke akun validator, atau username bila akun tidak terhubung ke karyawan. */
	private Function<String, String> nameResolver(java.util.Collection<OvertimeValidation> list) {
		Set<String> usernames = list.stream().map(OvertimeValidation::getValidatedBy).collect(Collectors.toSet());
		if (usernames.isEmpty()) {
			return u -> u;
		}
		Map<String, String> names = new HashMap<>();
		for (UserAccount account : accounts.findByUsernameInWithEmployee(usernames)) {
			if (account.getEmployee() != null) {
				names.put(account.getUsername(), account.getEmployee().getFullName());
			}
		}
		return u -> names.getOrDefault(u, u);
	}

	private static String key(Long employeeId, LocalDate date) {
		return employeeId + "/" + date;
	}

}
