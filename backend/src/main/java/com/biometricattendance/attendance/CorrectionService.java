package com.biometricattendance.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.attendance.AttendanceDtos.CorrectionRequest;
import com.biometricattendance.attendance.AttendanceDtos.CorrectionResponse;
import com.biometricattendance.attendance.AttendanceDtos.DayRecord;
import com.biometricattendance.audit.AuditService;
import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.calculation.DailyResult.TimeValue;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;

/**
 * Koreksi absensi (K-059, TC-08): scan asli tidak disentuh (K-019). Koreksi baru menggantikan koreksi aktif
 * sebelumnya yang tetap tersimpan (K-066). Audit mencatat jam masuk/pulang sebelum dan sesudah, pelaku, waktu,
 * dan alasan (COR-002–COR-005) di transaksi yang sama.
 */
@Service
public class CorrectionService {

	static final String ENTITY = "ATTENDANCE_CORRECTION";

	private final AttendanceCorrectionRepository corrections;
	private final EmployeeRepository employees;
	private final AttendanceService attendance;
	private final AuditService audit;
	private final ApplicationEventPublisher events;

	public CorrectionService(AttendanceCorrectionRepository corrections, EmployeeRepository employees,
			AttendanceService attendance, AuditService audit, ApplicationEventPublisher events) {
		this.corrections = corrections;
		this.employees = employees;
		this.attendance = attendance;
		this.audit = audit;
		this.events = events;
	}

	/** Isi rekap yang dicatat di audit: cukup untuk menelusuri apa yang berubah. */
	record AuditView(LocalDate date, TimeValue checkIn, TimeValue checkOut, AttendanceStatus status,
			List<Instant> scans, LocalTime correctionCheckIn, LocalTime correctionCheckOut) {

		static AuditView of(DayRecord day) {
			return new AuditView(day.date(), day.result() == null ? null : day.result().checkIn(),
					day.result() == null ? null : day.result().checkOut(),
					day.result() == null ? null : day.result().status(),
					day.scans().stream().map(AttendanceDtos.ScanResponse::scannedAt).toList(),
					day.correction() == null ? null : day.correction().checkIn(),
					day.correction() == null ? null : day.correction().checkOut());
		}

	}

	@Transactional
	public CorrectionResponse create(CorrectionRequest request) {
		Employee employee = employees.findById(request.employeeId())
			.orElseThrow(() -> ApiException.notFound("Karyawan"));
		if (request.checkIn() == null && request.checkOut() == null) {
			throw ApiException.badRequest("Isi jam masuk atau jam pulang yang dikoreksi");
		}
		if (request.checkIn() != null && request.checkOut() != null && !request.checkOut().isAfter(request.checkIn())) {
			throw ApiException.badRequest("Jam pulang harus setelah jam masuk");
		}
		if (request.date().isAfter(attendance.today())) {
			throw ApiException.badRequest("Koreksi hanya untuk tanggal yang sudah terjadi");
		}
		String reason = request.reason().trim();

		DayRecord before = attendance.day(employee, request.date());
		corrections.findByEmployeeIdAndWorkDateAndSupersededAtIsNull(employee.getId(), request.date())
			.ifPresent(old -> {
				old.supersede(Instant.now());
				corrections.saveAndFlush(old);
			});
		AttendanceCorrection saved = corrections.saveAndFlush(new AttendanceCorrection(employee.getId(),
				request.date(), request.checkIn(), request.checkOut(), reason, CurrentUser.username()));
		DayRecord after = attendance.day(employee, request.date());

		audit.record(CurrentUser.username(), "CORRECT", ENTITY, saved.getId(), AuditView.of(before),
				AuditView.of(after), reason);
		events.publishEvent(new AttendanceChangedEvent(employee.getId(), request.date()));
		return CorrectionResponse.from(saved);
	}

}
