package com.biometricattendance.account;

import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.common.ApiException;
import com.biometricattendance.employee.EmployeeDtos.EmployeeResponse;

/** Data karyawan milik pengguna yang sedang login (untuk role Karyawan). */
@RestController
@RequestMapping("/api/me")
public class MyProfileController {

	private final UserAccountRepository accounts;

	public MyProfileController(UserAccountRepository accounts) {
		this.accounts = accounts;
	}

	@GetMapping("/employee")
	@Transactional(readOnly = true)
	public EmployeeResponse myEmployee(Authentication authentication) {
		UserAccount account = accounts.findByUsername(authentication.getName())
			.orElseThrow(() -> ApiException.notFound("Akun"));
		if (account.getEmployee() == null) {
			throw ApiException.notFound("Data karyawan untuk akun ini");
		}
		return EmployeeResponse.from(account.getEmployee());
	}

}
