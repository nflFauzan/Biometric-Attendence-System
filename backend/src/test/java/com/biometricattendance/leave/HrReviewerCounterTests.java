package com.biometricattendance.leave;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.biometricattendance.AttendanceFixtures;
import com.biometricattendance.account.Role;
import com.biometricattendance.account.UserAccount;
import com.biometricattendance.account.UserAccountRepository;
import com.biometricattendance.employee.EmployeeRepository;

/** Query penghitung HR/Finance lain dijalankan ke database sungguhan (tanpa mock). Data sintetis. */
class HrReviewerCounterTests extends AttendanceFixtures {

	@Autowired
	private HrReviewerCounter counter;

	@Autowired
	private UserAccountRepository accounts;

	@Autowired
	private EmployeeRepository employees;

	@Test
	void countsActiveHrIncludingUnlinkedAccountsButNotTheRequesters() throws Exception {
		long branch = createBranch();
		long requester = createEmployee(branch, null);
		long other = createEmployee(branch, null);
		long baseline = counter.countOtherActiveHr(requester);

		account(Role.HR_FINANCE, true, null);
		account(Role.HR_FINANCE, true, requester);
		account(Role.HR_FINANCE, true, other);
		account(Role.HR_FINANCE, false, null);
		account(Role.OWNER, true, null);

		assertThat(counter.countOtherActiveHr(requester)).isEqualTo(baseline + 2);
	}

	private void account(Role role, boolean active, Long employeeId) {
		UserAccount account = new UserAccount();
		account.setUsername(unique("hr-"));
		account.setPasswordHash("bukan-hash-sungguhan");
		account.setRole(role);
		account.setActive(active);
		if (employeeId != null) {
			account.setEmployee(employees.getReferenceById(employeeId));
		}
		accounts.saveAndFlush(account);
	}

}
