package com.biometricattendance.team;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.attendance.AttendanceService;
import com.biometricattendance.branch.Branch;
import com.biometricattendance.branch.BranchRepository;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;
import com.biometricattendance.team.TeamDtos.BranchOption;
import com.biometricattendance.team.TeamDtos.TeamDailyResponse;
import com.biometricattendance.team.TeamDtos.TeamDay;
import com.biometricattendance.team.TeamDtos.TeamEmployeeResponse;
import com.biometricattendance.team.TeamDtos.TeamRow;

/** Absensi rekan kerja untuk semua role (K-083), dihitung dengan mesin rekap yang sama dengan pengelola. */
@Service
public class TeamService {

	private final AttendanceService attendance;
	private final EmployeeRepository employees;
	private final BranchRepository branches;

	public TeamService(AttendanceService attendance, EmployeeRepository employees, BranchRepository branches) {
		this.attendance = attendance;
		this.employees = employees;
		this.branches = branches;
	}

	public LocalDate today() {
		return attendance.today();
	}

	/**
	 * Karyawan aktif saja ({@code AttendanceService.daily} sudah menyaring {@code active = true}). Jabatan diambil
	 * dari entity yang sudah dimuat {@code daily} di transaksi yang sama, jadi tanpa query tambahan per baris.
	 */
	@Transactional(readOnly = true)
	public TeamDailyResponse daily(LocalDate date, Long branchId) {
		requireNotFuture(date);
		List<TeamRow> rows = attendance.daily(date, branchId)
			.rows()
			.stream()
			.map(r -> new TeamRow(r.employeeId(), r.employeeNumber(), r.fullName(), r.branchId(), r.branchName(),
					employees.getReferenceById(r.employeeId()).getPosition(), TeamDay.from(r.day())))
			.toList();
		return new TeamDailyResponse(date, branchId, branchOptions(), rows);
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

	private List<BranchOption> branchOptions() {
		return branches.findAllByOrderByNameAsc()
			.stream()
			.filter(Branch::isActive)
			.map(b -> new BranchOption(b.getId(), b.getName()))
			.toList();
	}

}
