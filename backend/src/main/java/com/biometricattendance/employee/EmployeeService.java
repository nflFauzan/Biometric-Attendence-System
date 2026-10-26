package com.biometricattendance.employee;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.attendance.AttendanceScanRepository;
import com.biometricattendance.audit.AuditService;
import com.biometricattendance.branch.Branch;
import com.biometricattendance.branch.BranchRepository;
import com.biometricattendance.category.EmployeeCategoryRepository;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.fingerprint.FingerprintRemoval;
import com.biometricattendance.employee.EmployeeDtos.BranchAccessRequest;
import com.biometricattendance.employee.EmployeeDtos.BranchAccessResponse;
import com.biometricattendance.employee.EmployeeDtos.EmployeeRequest;
import com.biometricattendance.employee.EmployeeDtos.EmployeeResponse;

@Service
public class EmployeeService {

	static final String ENTITY = "EMPLOYEE";

	private final EmployeeRepository repository;
	private final BranchRepository branches;
	private final EmployeeCategoryRepository categories;
	private final AuditService audit;
	private final AttendanceScanRepository scans;
	private final ApplicationEventPublisher events;
	private final FingerprintRemoval fingerprintRemoval;

	public EmployeeService(EmployeeRepository repository, BranchRepository branches,
			EmployeeCategoryRepository categories, AuditService audit, AttendanceScanRepository scans,
			ApplicationEventPublisher events, FingerprintRemoval fingerprintRemoval) {
		this.repository = repository;
		this.branches = branches;
		this.categories = categories;
		this.audit = audit;
		this.scans = scans;
		this.events = events;
		this.fingerprintRemoval = fingerprintRemoval;
	}

	@Transactional(readOnly = true)
	public List<EmployeeResponse> search(Long branchId, Boolean active, String search) {
		String term = search == null || search.isBlank() ? null : search.trim();
		return repository.search(branchId, active, term).stream().map(EmployeeResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public EmployeeResponse get(Long id) {
		return EmployeeResponse.from(find(id));
	}

	@Transactional
	public EmployeeResponse create(EmployeeRequest request) {
		if (repository.existsByEmployeeNumberIgnoreCase(request.employeeNumber().trim())) {
			throw ApiException.conflict("Nomor karyawan sudah dipakai");
		}
		String pin = normalizePin(request.devicePin());
		if (pin != null && repository.existsByDevicePin(pin)) {
			throw ApiException.conflict("PIN mesin sudah dipakai karyawan lain");
		}
		Employee employee = new Employee();
		apply(employee, request);
		events.publishEvent(new EmployeeChangedEvent(employee, null, true));
		EmployeeResponse created = EmployeeResponse.from(repository.save(employee));
		audit.record("CREATE", ENTITY, created.id(), null, created);
		linkUnmatchedScans(employee, null);
		return created;
	}

	@Transactional
	public EmployeeResponse update(Long id, EmployeeRequest request) {
		Employee employee = find(id);
		if (repository.existsByEmployeeNumberIgnoreCaseAndIdNot(request.employeeNumber().trim(), id)) {
			throw ApiException.conflict("Nomor karyawan sudah dipakai");
		}
		String pin = normalizePin(request.devicePin());
		if (pin != null && repository.existsByDevicePinAndIdNot(pin, id)) {
			throw ApiException.conflict("PIN mesin sudah dipakai karyawan lain");
		}
		EmployeeResponse before = EmployeeResponse.from(employee);
		List<Long> extrasBefore = sortedExtraIds(employee);
		Branch previousBranch = employee.getBranch();
		apply(employee, request);
		keepPlacementConsistent(employee, previousBranch);
		events.publishEvent(new EmployeeChangedEvent(employee, before.devicePin(), false));
		EmployeeResponse after = EmployeeResponse.from(repository.save(employee));
		audit.record("UPDATE", ENTITY, id, snapshot(before, extrasBefore),
				snapshot(after, sortedExtraIds(employee)));
		linkUnmatchedScans(employee, before.devicePin());
		return after;
	}

	@Transactional(readOnly = true)
	public BranchAccessResponse branchAccess(Long id) {
		return branchAccessOf(find(id));
	}

	/** K-103: mengganti daftar cabang absen tambahan. ID penempatan diabaikan, duplikat digabung. */
	@Transactional
	public BranchAccessResponse updateBranchAccess(Long id, BranchAccessRequest request) {
		Employee employee = find(id);
		Set<Long> wanted = new LinkedHashSet<>(request.extraBranchIds());
		wanted.remove(null);
		wanted.remove(employee.getBranch().getId());
		List<Branch> found = branches.findAllById(wanted);
		if (found.size() != wanted.size()) {
			throw ApiException.notFound("Cabang");
		}
		Map<String, Object> before = snapshot(EmployeeResponse.from(employee), sortedExtraIds(employee));
		Set<Long> revoked = new LinkedHashSet<>(employee.allowedBranchIds());
		employee.getExtraBranches().clear();
		employee.getExtraBranches().addAll(found);
		revoked.removeAll(employee.allowedBranchIds());
		repository.saveAndFlush(employee);
		// Cabang yang dicabut melupakan jari yang direkam di mesinnya (K-103), di transaksi yang sama.
		int removed = fingerprintRemoval.revokeBranches(employee, revoked, CurrentUser.username(), Instant.now());
		Map<String, Object> after = snapshot(EmployeeResponse.from(employee), sortedExtraIds(employee));
		after.put("removedFingerprints", removed);
		audit.record("UPDATE", ENTITY, id, before, after);
		return new BranchAccessResponse(employee.getBranch().getId(), sortedExtraIds(employee), removed);
	}

	private static BranchAccessResponse branchAccessOf(Employee employee) {
		return new BranchAccessResponse(employee.getBranch().getId(), sortedExtraIds(employee), 0);
	}

	private static List<Long> sortedExtraIds(Employee employee) {
		return employee.getExtraBranches().stream().map(Branch::getId).sorted().toList();
	}

	/** CB-10: penempatan lama menjadi cabang tambahan saat berpindah; penempatan baru keluar dari daftar tambahan. */
	private static void keepPlacementConsistent(Employee employee, Branch previousBranch) {
		Long currentId = employee.getBranch().getId();
		if (!currentId.equals(previousBranch.getId())) {
			employee.getExtraBranches().add(previousBranch);
		}
		employee.getExtraBranches().removeIf(b -> b.getId().equals(currentId));
	}

	/** Snapshot audit karyawan (datar): kolom EmployeeResponse ditambah extraBranchIds terurut. */
	private static Map<String, Object> snapshot(EmployeeResponse e, List<Long> extraBranchIds) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", e.id());
		m.put("employeeNumber", e.employeeNumber());
		m.put("fullName", e.fullName());
		m.put("phone", e.phone());
		m.put("branchId", e.branchId());
		m.put("branchName", e.branchName());
		m.put("categoryId", e.categoryId());
		m.put("categoryName", e.categoryName());
		m.put("position", e.position());
		m.put("joinDate", e.joinDate());
		m.put("endDate", e.endDate());
		m.put("active", e.active());
		m.put("devicePin", e.devicePin());
		m.put("email", e.email());
		m.put("gender", e.gender());
		m.put("birthDate", e.birthDate());
		m.put("address", e.address());
		m.put("notes", e.notes());
		m.put("extraBranchIds", extraBranchIds);
		return m;
	}

	/**
	 * K-068: scan dengan PIN yang belum dikenal dihubungkan ke karyawan saat PIN-nya diisi, di transaksi yang
	 * sama dan tercatat di audit. Isi scan asli tidak berubah, hanya relasinya.
	 */
	private void linkUnmatchedScans(Employee employee, String previousPin) {
		String pin = employee.getDevicePin();
		if (pin == null || pin.equals(previousPin)) {
			return;
		}
		repository.flush();
		int linked = scans.linkUnmatched(pin, employee.getId(), employee.getBranch().getId());
		if (linked > 0) {
			audit.record("LINK_SCANS", ENTITY, employee.getId(), null, Map.of("devicePin", pin, "linkedScans", linked));
		}
	}

	Employee find(Long id) {
		return repository.findById(id).orElseThrow(() -> ApiException.notFound("Karyawan"));
	}

	private void apply(Employee employee, EmployeeRequest request) {
		if (request.endDate() != null && request.endDate().isBefore(request.joinDate())) {
			throw ApiException.badRequest("Tanggal keluar tidak boleh sebelum tanggal masuk");
		}
		employee.setEmployeeNumber(request.employeeNumber().trim());
		employee.setFullName(request.fullName().trim());
		employee.setPhone(request.phone());
		employee.setBranch(branches.findById(request.branchId()).orElseThrow(() -> ApiException.notFound("Cabang")));
		employee.setCategory(
				categories.findById(request.categoryId()).orElseThrow(() -> ApiException.notFound("Kategori")));
		employee.setPosition(request.position());
		employee.setJoinDate(request.joinDate());
		employee.setEndDate(request.endDate());
		employee.setActive(request.active());
		employee.setDevicePin(normalizePin(request.devicePin()));
		employee.setEmail(blankToNull(request.email()));
		employee.setGender(request.gender());
		employee.setBirthDate(request.birthDate());
		employee.setAddress(blankToNull(request.address()));
		employee.setNotes(blankToNull(request.notes()));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String normalizePin(String pin) {
		return pin == null || pin.isBlank() ? null : pin.trim();
	}

}
