package com.biometricattendance.workrule;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.audit.AuditService;
import com.biometricattendance.calculation.EffectiveRule;
import com.biometricattendance.calculation.RuleLevel;
import com.biometricattendance.calculation.RuleResolver;
import com.biometricattendance.calculation.RuleValues;
import com.biometricattendance.calculation.RuleVersion;
import com.biometricattendance.category.EmployeeCategory;
import com.biometricattendance.category.EmployeeCategoryRepository;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;
import com.biometricattendance.workrule.WorkRuleDtos.EffectiveRuleResponse;
import com.biometricattendance.workrule.WorkRuleDtos.WorkRuleRequest;
import com.biometricattendance.workrule.WorkRuleDtos.WorkRuleResponse;

/**
 * Versi aturan kerja. Aturan versi (K-050): versi baru tidak boleh berlaku surut, dan versi yang tanggal
 * berlakunya sudah tiba tidak bisa diubah atau dihapus. "Hari ini" mengikuti zona waktu perusahaan.
 */
@Service
public class WorkRuleService {

	static final String ENTITY = "WORK_RULE";

	private final WorkRuleRepository repository;
	private final EmployeeRepository employees;
	private final EmployeeCategoryRepository categories;
	private final AuditService audit;
	private final ZoneId companyZone;

	public WorkRuleService(WorkRuleRepository repository, EmployeeRepository employees,
			EmployeeCategoryRepository categories, AuditService audit,
			@Value("${app.company-timezone}") String companyZone) {
		this.repository = repository;
		this.employees = employees;
		this.categories = categories;
		this.audit = audit;
		this.companyZone = ZoneId.of(companyZone);
	}

	public LocalDate today() {
		return LocalDate.now(companyZone);
	}

	@Transactional(readOnly = true)
	public List<WorkRuleResponse> list(RuleLevel scope, Long categoryId, Long employeeId) {
		LocalDate today = today();
		return versionsOf(scope, categoryId, employeeId).stream()
			.map(rule -> WorkRuleResponse.from(rule, today))
			.toList();
	}

	@Transactional
	public WorkRuleResponse create(WorkRuleRequest request) {
		requireNoTargetMismatch(request);
		WorkRule rule = new WorkRule();
		rule.setScope(request.scope());
		rule.setCategory(request.scope() == RuleLevel.CATEGORY ? category(request.categoryId()) : null);
		rule.setEmployee(request.scope() == RuleLevel.EMPLOYEE ? employee(request.employeeId()) : null);
		validate(request, null);
		rule.setEffectiveFrom(request.effectiveFrom());
		rule.setValues(request.values());
		rule.setNote(blankToNull(request.note()));
		rule.setCreatedBy(CurrentUser.username());
		WorkRuleResponse created = WorkRuleResponse.from(repository.save(rule), today());
		audit.record("CREATE", ENTITY, created.id(), null, created);
		return created;
	}

	@Transactional
	public WorkRuleResponse update(Long id, WorkRuleRequest request) {
		WorkRule rule = find(id);
		requireEditable(rule);
		if (request.scope() != rule.getScope() || !sameTarget(rule, request)) {
			throw ApiException.badRequest("Level dan sasaran aturan tidak bisa diubah; buat versi baru");
		}
		validate(request, id);
		WorkRuleResponse before = WorkRuleResponse.from(rule, today());
		rule.setEffectiveFrom(request.effectiveFrom());
		rule.setValues(request.values());
		rule.setNote(blankToNull(request.note()));
		WorkRuleResponse after = WorkRuleResponse.from(repository.save(rule), today());
		audit.record("UPDATE", ENTITY, id, before, after);
		return after;
	}

	@Transactional
	public void delete(Long id) {
		WorkRule rule = find(id);
		requireEditable(rule);
		WorkRuleResponse before = WorkRuleResponse.from(rule, today());
		repository.delete(rule);
		audit.record("DELETE", ENTITY, id, before, null);
	}

	/** Aturan berlaku untuk karyawan pada tanggal tertentu, lengkap dengan asal tiap nilai. */
	@Transactional(readOnly = true)
	public EffectiveRuleResponse effective(Long employeeId, LocalDate date) {
		Employee employee = employee(employeeId);
		EmployeeCategory category = employee.getCategory();
		return EffectiveRuleResponse.of(employee.getId(), employee.getFullName(), category.getId(),
				category.getName(), date, resolve(employee, date));
	}

	/** Dipakai modul perhitungan; harus dipanggil di dalam transaksi. */
	public EffectiveRule resolve(Employee employee, LocalDate date) {
		List<RuleVersion> versions = repository.findApplicable(employee.getCategory().getId(), employee.getId())
			.stream()
			.map(WorkRule::toVersion)
			.toList();
		return RuleResolver.resolve(versions, date);
	}

	/**
	 * Seperti {@link #resolve}, tetapi versi aturan dibaca sekali untuk banyak tanggal (mis. hari kerja dalam
	 * rentang izin). Harus dipakai di dalam transaksi yang sama.
	 */
	public Function<LocalDate, EffectiveRule> resolverFor(Employee employee) {
		List<RuleVersion> versions = repository.findApplicable(employee.getCategory().getId(), employee.getId())
			.stream()
			.map(WorkRule::toVersion)
			.toList();
		return date -> RuleResolver.resolve(versions, date);
	}

	Employee employee(Long id) {
		return employees.findById(required(id, "Karyawan wajib dipilih"))
			.orElseThrow(() -> ApiException.notFound("Karyawan"));
	}

	private List<WorkRule> versionsOf(RuleLevel scope, Long categoryId, Long employeeId) {
		return switch (scope) {
			case GLOBAL -> repository.findByScopeOrderByEffectiveFromDesc(RuleLevel.GLOBAL);
			case CATEGORY -> repository.findByScopeAndCategoryIdOrderByEffectiveFromDesc(RuleLevel.CATEGORY,
					required(categoryId, "Kategori wajib dipilih"));
			case EMPLOYEE -> repository.findByScopeAndEmployeeIdOrderByEffectiveFromDesc(RuleLevel.EMPLOYEE,
					required(employeeId, "Karyawan wajib dipilih"));
		};
	}

	private void validate(WorkRuleRequest request, Long currentId) {
		RuleValues values = request.values();
		if (values.isEmpty()) {
			throw ApiException.badRequest("Isi minimal satu parameter aturan");
		}
		if ((request.breakStart() == null) != (request.breakEnd() == null)) {
			throw ApiException.badRequest("Jam mulai dan selesai istirahat harus diisi keduanya atau dikosongkan");
		}
		if (request.breakStart() != null && !request.breakEnd().isAfter(request.breakStart())) {
			throw ApiException.badRequest("Jam selesai istirahat harus setelah jam mulai istirahat");
		}
		if (request.workStart() != null && request.workEnd() != null
				&& !request.workEnd().isAfter(request.workStart())) {
			throw ApiException.badRequest("Jam pulang harus setelah jam masuk");
		}
		if (request.workDays() != null && request.workDays().isEmpty()) {
			throw ApiException.badRequest("Pilih minimal satu hari kerja, atau kosongkan untuk mewarisi");
		}
		if (request.effectiveFrom().isBefore(today())) {
			throw ApiException.badRequest("Tanggal berlaku tidak boleh sebelum hari ini; perubahan berlaku surut "
					+ "belum didukung");
		}
		boolean duplicate = versionsOf(request.scope(), request.categoryId(), request.employeeId()).stream()
			.anyMatch(other -> other.getEffectiveFrom().equals(request.effectiveFrom())
					&& !other.getId().equals(currentId));
		if (duplicate) {
			throw ApiException.conflict("Sudah ada versi aturan dengan tanggal berlaku yang sama");
		}
	}

	private static void requireNoTargetMismatch(WorkRuleRequest request) {
		boolean valid = switch (request.scope()) {
			case GLOBAL -> request.categoryId() == null && request.employeeId() == null;
			case CATEGORY -> request.employeeId() == null;
			case EMPLOYEE -> request.categoryId() == null;
		};
		if (!valid) {
			throw ApiException.badRequest("Sasaran aturan tidak sesuai dengan levelnya");
		}
	}

	private void requireEditable(WorkRule rule) {
		if (!rule.getEffectiveFrom().isAfter(today())) {
			throw ApiException.conflict("Versi aturan yang sudah berlaku tidak bisa diubah atau dihapus; "
					+ "buat versi baru dengan tanggal berlaku berikutnya");
		}
	}

	private static boolean sameTarget(WorkRule rule, WorkRuleRequest request) {
		Long categoryId = rule.getCategory() == null ? null : rule.getCategory().getId();
		Long employeeId = rule.getEmployee() == null ? null : rule.getEmployee().getId();
		return Objects.equals(categoryId, request.categoryId()) && Objects.equals(employeeId, request.employeeId());
	}

	private WorkRule find(Long id) {
		return repository.findById(id).orElseThrow(() -> ApiException.notFound("Aturan kerja"));
	}

	private EmployeeCategory category(Long id) {
		return categories.findById(required(id, "Kategori wajib dipilih"))
			.orElseThrow(() -> ApiException.notFound("Kategori"));
	}

	private static Long required(Long id, String message) {
		if (id == null) {
			throw ApiException.badRequest(message);
		}
		return id;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
