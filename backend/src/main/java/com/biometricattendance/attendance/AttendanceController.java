package com.biometricattendance.attendance;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.attendance.AttendanceDtos.CorrectionRequest;
import com.biometricattendance.attendance.AttendanceDtos.CorrectionResponse;
import com.biometricattendance.attendance.AttendanceDtos.DailyAttendanceResponse;
import com.biometricattendance.attendance.AttendanceDtos.EmployeeAttendanceResponse;
import com.biometricattendance.attendance.AttendanceDtos.ScanResponse;

import jakarta.validation.Valid;

/** Rekap absensi, scan tak dikenal, dan koreksi untuk Owner dan HR/Finance. */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

	static final int UNMATCHED_LIMIT = 500;

	private final AttendanceService attendance;
	private final CorrectionService corrections;
	private final AttendanceScanRepository scans;
	private final ZoneId companyZone;

	public AttendanceController(AttendanceService attendance, CorrectionService corrections,
			AttendanceScanRepository scans, @Value("${app.company-timezone}") String companyZone) {
		this.attendance = attendance;
		this.corrections = corrections;
		this.scans = scans;
		this.companyZone = ZoneId.of(companyZone);
	}

	@GetMapping("/daily")
	DailyAttendanceResponse daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) Long branchId) {
		return attendance.daily(date == null ? attendance.today() : date, branchId);
	}

	/** Tanpa rentang: 7 hari terakhir sampai hari ini. */
	@GetMapping("/employees/{id}")
	EmployeeAttendanceResponse employee(@PathVariable Long id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		LocalDate end = to == null ? attendance.today() : to;
		return attendance.forEmployee(id, from == null ? end.minusDays(6) : from, end);
	}

	/** Scan dengan PIN yang belum dipetakan ke karyawan (K-065), terbaru lebih dulu. */
	@GetMapping("/unmatched-scans")
	@Transactional(readOnly = true)
	public List<ScanResponse> unmatched(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		LocalDate end = to == null ? attendance.today() : to;
		LocalDate start = from == null ? end.minusDays(6) : from;
		AttendanceService.requireRange(start, end);
		return scans.findUnmatched(start.atStartOfDay(companyZone).toInstant(),
				end.plusDays(1).atStartOfDay(companyZone).toInstant(), PageRequest.of(0, UNMATCHED_LIMIT))
			.stream()
			.map(ScanResponse::from)
			.toList();
	}

	@PostMapping("/corrections")
	@ResponseStatus(HttpStatus.CREATED)
	CorrectionResponse correct(@Valid @RequestBody CorrectionRequest request) {
		return corrections.create(request);
	}

}
