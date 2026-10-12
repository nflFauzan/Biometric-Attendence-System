package com.biometricattendance.employee;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.attendance.AttendanceScanRepository;
import com.biometricattendance.audit.AuditService;
import com.biometricattendance.branch.BranchRepository;
import com.biometricattendance.category.EmployeeCategoryRepository;
import com.biometricattendance.common.ApiException;
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

	public EmployeeService(EmployeeRepository repository, BranchRepository branches,
			EmployeeCategoryRepository categories, AuditService audit, AttendanceScanRepository scans) {
		this.repository = repository;
		this.branches = branches;
		this.categories = categories;
		this.audit = audit;
		this.scans = scans;
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
		apply(employee, request);
		EmployeeResponse after = EmployeeResponse.from(repository.save(employee));
		audit.record("UPDATE", ENTITY, id, before, after);
		linkUnmatchedScans(employee, before.devicePin());
		return after;
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
		int linked = scans.linkUnmatched(pin, employee.getId());
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
	}

	private static String normalizePin(String pin) {
		return pin == null || pin.isBlank() ? null : pin.trim();
	}

}
