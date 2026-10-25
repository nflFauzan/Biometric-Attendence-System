package com.biometricattendance.team;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.account.UserAccount;
import com.biometricattendance.account.UserAccountRepository;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.team.TeamDtos.TeamEmployeeResponse;

/** Absensi Saya: karyawan hanya melihat riwayat absensinya sendiri (K-127, menggantikan K-083). */
@RestController
@RequestMapping("/api/me/attendance")
public class MyAttendanceController {

	private final UserAccountRepository accounts;
	private final TeamService team;

	public MyAttendanceController(UserAccountRepository accounts, TeamService team) {
		this.accounts = accounts;
		this.team = team;
	}

	/** Tanpa rentang: awal bulan berjalan sampai hari ini. */
	@GetMapping
	@Transactional(readOnly = true)
	TeamEmployeeResponse mine(Authentication authentication,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		Employee employee = accounts.findByUsername(authentication.getName())
			.map(UserAccount::getEmployee)
			.filter(Employee::isActive)
			.orElseThrow(() -> ApiException.notFound("Data karyawan untuk akun ini"));
		LocalDate end = to == null ? team.today() : to;
		return team.forEmployee(employee.getId(), from == null ? end.withDayOfMonth(1) : from, end);
	}

}
