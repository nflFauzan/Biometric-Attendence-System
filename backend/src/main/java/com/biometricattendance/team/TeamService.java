package com.biometricattendance.team;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.attendance.AttendanceService;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;
import com.biometricattendance.team.TeamDtos.TeamDay;
import com.biometricattendance.team.TeamDtos.TeamEmployeeResponse;

/** Absensi Saya untuk karyawan (K-127), dihitung dengan mesin rekap yang sama dengan pengelola. */
@Service
public class TeamService {

	private final AttendanceService attendance;
	private final EmployeeRepository employees;

	public TeamService(AttendanceService attendance, EmployeeRepository employees) {
		this.attendance = attendance;
		this.employees = employees;
	}

	public LocalDate today() {
		return attendance.today();
	}

	@Transactional(readOnly = true)
	public TeamEmployeeResponse forEmployee(Long employeeId, LocalDate from, LocalDate to) {
		Employee e = employees.findById(employeeId)
			.filter(Employee::isActive)
			.orElseThrow(() -> ApiException.notFound("Karyawan"));
		requireNotFuture(to);
		List<TeamDay> days = attendance.forEmployee(employeeId, from, to).days().stream().map(TeamDay::from).toList();
		return new TeamEmployeeResponse(e.getId(), e.getEmployeeNumber(), e.getFullName(), e.getBranch().getName(),
				e.getPosition(), from, to, days);
	}

	/** Hari yang belum terjadi akan tampil "Tidak hadir" palsu dan membocorkan izin mendatang. */
	private void requireNotFuture(LocalDate date) {
		if (date.isAfter(today())) {
			throw ApiException.badRequest("Tanggal belum terjadi");
		}
	}

}
