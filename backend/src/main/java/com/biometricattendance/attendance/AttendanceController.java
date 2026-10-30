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
import com.biometricattendance.attendance.AttendanceDtos.HeldScanResponse;
import com.biometricattendance.attendance.AttendanceDtos.ReviewItem;
import com.biometricattendance.attendance.AttendanceDtos.ReviewRequest;
import com.biometricattendance.attendance.AttendanceDtos.ReviewStatus;
import com.biometricattendance.attendance.AttendanceDtos.ScanResponse;

import jakarta.validation.Valid;

/** Rekap absensi, scan tak dikenal, dan koreksi untuk Owner dan HR/Finance. */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

	static final int UNMATCHED_LIMIT = 500;

	private final AttendanceService attendance;
	private final CorrectionService corrections;
	private final HeldScanService heldScans;
	private final ReviewQueueService reviewQueue;
	private final AttendanceScanRepository scans;
	private final ZoneId companyZone;

	public AttendanceController(AttendanceService attendance, CorrectionService corrections,
			HeldScanService heldScans, ReviewQueueService reviewQueue, AttendanceScanRepository scans, @Value("${app.company-timezone}") String companyZone) {
		this.attendance = attendance;
		this.corrections = corrections;
		this.heldScans = heldScans;
		this.reviewQueue = reviewQueue;
		this.scans = scans;
		this.companyZone = ZoneId.of(companyZone);
	}

	@GetMapping("/daily")
	DailyAttendanceResponse daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) Long branchId, @RequestParam(required = false) Long categoryId) {
		return attendance.daily(date == null ? attendance.today() : date, branchId, categoryId);
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

	/** Scan tertahan karena cabang (K-103); tanpa status = semua keputusan. Rentang bawaan sama dengan scan tak dikenal. */
	@GetMapping("/held-scans")
	public List<HeldScanResponse> heldScans(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) ReviewStatus status) {
		LocalDate end = to == null ? attendance.today() : to;
		return heldScans.list(from == null ? end.minusDays(6) : from, end, status);
	}

	/** Hari yang perlu keputusan manusia (FR-REV-001). Tanpa rentang: dari tanggal 1 bulan berjalan sampai hari ini. */
	@GetMapping("/review-queue")
	public List<ReviewItem> reviewQueue(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long branchId, @RequestParam(required = false) Long categoryId) {
		LocalDate end = to == null ? attendance.today() : to;
		return reviewQueue.list(from == null ? end.withDayOfMonth(1) : from, end, branchId, categoryId);
	}

	@PostMapping("/held-scans/review")
	List<HeldScanResponse> reviewHeldScans(@Valid @RequestBody ReviewRequest request) {
		return heldScans.review(request);
	}

	@PostMapping("/corrections")
	@ResponseStatus(HttpStatus.CREATED)
	CorrectionResponse correct(@Valid @RequestBody CorrectionRequest request) {
		return corrections.create(request);
	}

}
