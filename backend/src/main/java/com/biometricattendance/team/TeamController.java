package com.biometricattendance.team;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.team.TeamDtos.TeamDailyResponse;
import com.biometricattendance.team.TeamDtos.TeamEmployeeResponse;

/** Absensi rekan kerja untuk semua pengguna yang login (K-083). */
@RestController
@RequestMapping("/api/team")
public class TeamController {

	private final TeamService team;

	public TeamController(TeamService team) {
		this.team = team;
	}

	@GetMapping("/attendance")
	TeamDailyResponse daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) Long branchId) {
		return team.daily(date == null ? team.today() : date, branchId);
	}

	/** Tanpa rentang: awal bulan berjalan sampai hari ini. */
	@GetMapping("/employees/{id}/attendance")
	TeamEmployeeResponse employee(@PathVariable Long id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		LocalDate end = to == null ? team.today() : to;
		return team.forEmployee(id, from == null ? end.withDayOfMonth(1) : from, end);
	}

}
