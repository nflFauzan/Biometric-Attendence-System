package com.biometricattendance.account;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.account.UserAccountDtos.CreateUserRequest;
import com.biometricattendance.account.UserAccountDtos.ResetPasswordRequest;
import com.biometricattendance.account.UserAccountDtos.UpdateUserRequest;
import com.biometricattendance.account.UserAccountDtos.UserResponse;
import com.biometricattendance.audit.AuditService;
import com.biometricattendance.auth.SessionRevoker;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;

/** Owner mengelola semua akun. HR/Finance hanya boleh mengelola akun karyawan (role EMPLOYEE). */
@Service
public class UserAccountService {

	static final String ENTITY = "USER_ACCOUNT";

	private final UserAccountRepository repository;
	private final EmployeeRepository employees;
	private final PasswordEncoder passwordEncoder;
	private final AuditService audit;
	private final SessionRevoker sessions;

	public UserAccountService(UserAccountRepository repository, EmployeeRepository employees,
			PasswordEncoder passwordEncoder, AuditService audit, SessionRevoker sessions) {
		this.repository = repository;
		this.employees = employees;
		this.passwordEncoder = passwordEncoder;
		this.audit = audit;
		this.sessions = sessions;
	}

	@Transactional(readOnly = true)
	public List<UserResponse> list() {
		List<UserAccount> accounts = isOwner() ? repository.findAllWithEmployee()
				: repository.findByRoleWithEmployee(Role.EMPLOYEE);
		return accounts.stream().map(UserResponse::from).toList();
	}

	@Transactional
	public UserResponse create(CreateUserRequest request) {
		requireCanManage(request.role());
		String username = normalize(request.username());
		if (repository.existsByUsername(username)) {
			throw ApiException.conflict("Username sudah dipakai");
		}
		UserAccount account = new UserAccount();
		account.setUsername(username);
		account.setPasswordHash(passwordEncoder.encode(request.password()));
		account.setRole(request.role());
		account.setEmployee(resolveEmployee(request.role(), request.employeeId(), null));
		account.setMustChangePassword(true);
		UserResponse created = UserResponse.from(repository.save(account));
		audit.record("CREATE", ENTITY, created.id(), null, created);
		return created;
	}

	@Transactional
	public UserResponse update(Long id, UpdateUserRequest request) {
		UserAccount account = find(id);
		requireCanManage(account.getRole());
		requireCanManage(request.role());
		boolean isSelf = account.getUsername().equals(CurrentUser.username());
		if (isSelf && (!request.active() || request.role() != account.getRole())) {
			throw ApiException.badRequest("Tidak bisa menonaktifkan atau mengubah role akun sendiri");
		}
		boolean removesActiveOwner = account.getRole() == Role.OWNER && account.isActive()
				&& (!request.active() || request.role() != Role.OWNER);
		if (removesActiveOwner && repository.countByRoleAndActiveTrue(Role.OWNER) <= 1) {
			throw ApiException.badRequest("Harus ada minimal satu Owner yang aktif");
		}
		UserResponse before = UserResponse.from(account);
		// Resolve sebelum entity diubah: query di dalamnya memicu auto-flush, dan flush dengan
		// role EMPLOYEE tanpa employee_id melanggar CHECK user_accounts_employee_required.
		Employee employee = resolveEmployee(request.role(), request.employeeId(), account.getId());
		account.setRole(request.role());
		account.setEmployee(employee);
		account.setActive(request.active());
		UserResponse after = UserResponse.from(repository.save(account));
		audit.record("UPDATE", ENTITY, id, before, after);
		// Hak akses lama di sesi yang sedang berjalan tidak boleh dipakai lagi (B-03).
		if (before.role() != after.role() || (before.active() && !after.active())) {
			sessions.revokeAll(account.getUsername());
		}
		return after;
	}

	/**
	 * K-137: status akun mengikuti karyawan yang terhubung saat karyawan dinonaktifkan atau diaktifkan
	 * kembali. Dipanggil dari transaksi {@code EmployeeService.update}; karyawan tanpa akun diabaikan.
	 */
	@Transactional
	public void followEmployeeStatus(Long employeeId, boolean active) {
		UserAccount account = repository.findByEmployeeId(employeeId).orElse(null);
		if (account == null || account.isActive() == active) {
			return;
		}
		// Aturan yang sama dengan menu Akun: Finance & Accounting hanya mengelola akun role EMPLOYEE.
		requireCanManage(account.getRole());
		if (!active && account.getUsername().equals(CurrentUser.username())) {
			throw ApiException.badRequest("Tidak bisa menonaktifkan akun sendiri");
		}
		if (!active && account.getRole() == Role.OWNER && repository.countByRoleAndActiveTrue(Role.OWNER) <= 1) {
			throw ApiException.badRequest("Harus ada minimal satu Owner yang aktif");
		}
		UserResponse before = UserResponse.from(account);
		account.setActive(active);
		UserResponse after = UserResponse.from(repository.save(account));
		audit.record("UPDATE", ENTITY, account.getId(), before, after);
		if (!active) {
			sessions.revokeAll(account.getUsername());
		}
	}

	@Transactional
	public void resetPassword(Long id, ResetPasswordRequest request) {
		UserAccount account = find(id);
		requireCanManage(account.getRole());
		account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		account.setMustChangePassword(true);
		repository.save(account);
		audit.record("RESET_PASSWORD", ENTITY, id, null, null);
		sessions.revokeAll(account.getUsername());
	}

	static String normalize(String username) {
		return username.trim().toLowerCase(Locale.ROOT);
	}

	private UserAccount find(Long id) {
		return repository.findById(id).orElseThrow(() -> ApiException.notFound("Akun"));
	}

	private Employee resolveEmployee(Role role, Long employeeId, Long accountId) {
		if (employeeId == null) {
			if (role == Role.EMPLOYEE) {
				throw ApiException.badRequest("Akun karyawan wajib terhubung ke data karyawan");
			}
			return null;
		}
		Employee employee = employees.findById(employeeId).orElseThrow(() -> ApiException.notFound("Karyawan"));
		repository.findByEmployeeId(employeeId)
			.filter(existing -> !Objects.equals(existing.getId(), accountId))
			.ifPresent(existing -> {
				throw ApiException.conflict("Karyawan ini sudah punya akun");
			});
		return employee;
	}

	private void requireCanManage(Role targetRole) {
		if (!isOwner() && targetRole != Role.EMPLOYEE) {
			throw ApiException.forbidden("Finance & Accounting hanya boleh mengelola akun karyawan");
		}
	}

	private boolean isOwner() {
		return CurrentUser.hasRole(Role.OWNER.name());
	}

}
