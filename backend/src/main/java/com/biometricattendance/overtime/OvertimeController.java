package com.biometricattendance.overtime;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.attendance.AttendanceService;
import com.biometricattendance.overtime.OvertimeDtos.OvertimeRow;
import com.biometricattendance.overtime.OvertimeDtos.ValidationRequest;

import jakarta.validation.Valid;

/** Validasi lembur (FR-REV-003, K-140) untuk Owner dan Finance & Accounting. */
@RestController
@RequestMapping("/api/overtime")
public class OvertimeController {

	private final OvertimeService overtime;
	private final AttendanceService attendance;

	public OvertimeController(OvertimeService overtime, AttendanceService attendance) {
		this.overtime = overtime;
		this.attendance = attendance;
	}

	/** Tanpa rentang: dari tanggal 1 bulan berjalan sampai hari ini (zona waktu perusahaan). */
	@GetMapping
	public List<OvertimeRow> list(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long branchId, @RequestParam(required = false) Long categoryId) {
		LocalDate end = to == null ? attendance.today() : to;
		return overtime.list(from == null ? end.withDayOfMonth(1) : from, end, branchId, categoryId);
	}

	@PostMapping("/validations")
	public List<OvertimeRow> validate(@Valid @RequestBody ValidationRequest request) {
		return overtime.validate(request);
	}

}
