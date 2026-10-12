package com.biometricattendance.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.attendance.AttendanceDtos.CorrectionResponse;
import com.biometricattendance.attendance.AttendanceDtos.DailyAttendanceResponse;
import com.biometricattendance.attendance.AttendanceDtos.DailyRow;
import com.biometricattendance.attendance.AttendanceDtos.DayRecord;
import com.biometricattendance.attendance.AttendanceDtos.EmployeeAttendanceResponse;
import com.biometricattendance.attendance.AttendanceDtos.ScanResponse;
import com.biometricattendance.calculation.CalculationDtos.DailyResultResponse;
import com.biometricattendance.calculation.DailyAttendanceCalculator;
import com.biometricattendance.calculation.DailyInput;
import com.biometricattendance.calculation.RuleNotConfiguredException;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;
import com.biometricattendance.holiday.Holiday;
import com.biometricattendance.holiday.HolidayRepository;
import com.biometricattendance.workrule.WorkRuleService;

/**
 * Rekap absensi dihitung saat diminta dari scan asli + koreksi aktif + aturan yang berlaku pada tanggalnya
 * (K-058), memakai mesin perhitungan Fase 2. Tidak ada tabel rekap, jadi scan baru, koreksi, atau aturan baru
 * langsung tercermin (ATT-010, COR-006). Batas hari mengikuti zona waktu cabang karyawan.
 */
@Service
public class AttendanceService {

	static final int MAX_RANGE_DAYS = 62;

	private final EmployeeRepository employees;
	private final AttendanceScanRepository scans;
	private final AttendanceCorrectionRepository corrections;
	private final HolidayRepository holidays;
	private final WorkRuleService workRules;

	public AttendanceService(EmployeeRepository employees, AttendanceScanRepository scans,
			AttendanceCorrectionRepository corrections, HolidayRepository holidays, WorkRuleService workRules) {
		this.employees = employees;
		this.scans = scans;
		this.corrections = corrections;
		this.holidays = holidays;
		this.workRules = workRules;
	}

	public LocalDate today() {
		return workRules.today();
	}

	/** Rekap semua karyawan aktif (opsional satu cabang) pada satu tanggal. */
	@Transactional(readOnly = true)
	public DailyAttendanceResponse daily(LocalDate date, Long branchId) {
		List<Employee> list = employees.search(branchId, true, null);
		Map<Long, List<DayRecord>> days = compute(list, date, date);
		List<DailyRow> rows = list.stream()
			.map(e -> new DailyRow(e.getId(), e.getEmployeeNumber(), e.getFullName(), e.getDevicePin(),
					e.getBranch().getId(), e.getBranch().getName(), e.getCategory().getName(), days.get(e.getId()).get(0)))
			.toList();
		return new DailyAttendanceResponse(date, branchId, rows);
	}

	/** Rekap per hari untuk satu karyawan, beserta riwayat koreksi termasuk yang sudah digantikan. */
	@Transactional(readOnly = true)
	public EmployeeAttendanceResponse forEmployee(Long employeeId, LocalDate from, LocalDate to) {
		requireRange(from, to);
		Employee e = employees.findById(employeeId).orElseThrow(() -> ApiException.notFound("Karyawan"));
		List<DayRecord> days = compute(List.of(e), from, to).get(e.getId());
		List<CorrectionResponse> history = corrections.findByEmployeeIdAndWorkDateBetweenOrderByIdDesc(employeeId, from, to)
			.stream()
			.map(CorrectionResponse::from)
			.toList();
		return new EmployeeAttendanceResponse(e.getId(), e.getEmployeeNumber(), e.getFullName(), e.getBranch().getName(),
				e.getBranch().getTimezone(), e.getDevicePin(), from, to, days, history);
	}

	/** Rekap satu hari untuk satu karyawan (dipakai untuk audit nilai sebelum/sesudah koreksi). */
	@Transactional(readOnly = true)
	public DayRecord day(Employee employee, LocalDate date) {
		return compute(List.of(employee), date, date).get(employee.getId()).get(0);
	}

	static void requireRange(LocalDate from, LocalDate to) {
		if (to.isBefore(from)) {
			throw ApiException.badRequest("Tanggal akhir tidak boleh sebelum tanggal awal");
		}
		if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
			throw ApiException.badRequest("Rentang tanggal maksimal " + MAX_RANGE_DAYS + " hari");
		}
	}

	private Map<Long, List<DayRecord>> compute(List<Employee> list, LocalDate from, LocalDate to) {
		if (list.isEmpty()) {
			return Map.of();
		}
		List<Long> ids = list.stream().map(Employee::getId).toList();
		// Jendela lebar (±1 hari UTC) agar mencakup zona waktu cabang mana pun; disaring per hari di bawah.
		Instant windowFrom = from.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant windowTo = to.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant();
		Map<Long, List<AttendanceScan>> scansByEmployee = scans.findForEmployees(ids, windowFrom, windowTo)
			.stream()
			.collect(Collectors.groupingBy(AttendanceScan::getEmployeeId));
		Map<String, AttendanceCorrection> activeCorrections = new HashMap<>();
		corrections.findActive(ids, from, to).forEach(c -> activeCorrections.put(key(c.getEmployeeId(), c.getWorkDate()), c));
		Set<LocalDate> holidayDates = holidays.findByHolidayDateBetweenOrderByHolidayDateAsc(from, to)
			.stream()
			.map(Holiday::getHolidayDate)
			.collect(Collectors.toSet());

		Map<Long, List<DayRecord>> result = new HashMap<>();
		for (Employee e : list) {
			ZoneId zone = ZoneId.of(e.getBranch().getTimezone());
			List<AttendanceScan> all = scansByEmployee.getOrDefault(e.getId(), List.of());
			List<DayRecord> days = new ArrayList<>();
			for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
				Instant start = date.atStartOfDay(zone).toInstant();
				Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();
				List<AttendanceScan> dayScans = all.stream()
					.filter(s -> !s.getScannedAt().isBefore(start) && s.getScannedAt().isBefore(end))
					.toList();
				days.add(calculate(e, date, zone, dayScans, activeCorrections.get(key(e.getId(), date)),
						holidayDates.contains(date)));
			}
			result.put(e.getId(), days);
		}
		return result;
	}

	private DayRecord calculate(Employee employee, LocalDate date, ZoneId zone, List<AttendanceScan> dayScans,
			AttendanceCorrection correction, boolean holiday) {
		Set<ScanFlag> scanFlags = EnumSet.noneOf(ScanFlag.class);
		dayScans.forEach(s -> scanFlags.addAll(s.getFlags()));
		List<ScanResponse> scanResponses = dayScans.stream().map(ScanResponse::from).toList();
		DailyInput.Correction input = correction == null ? null
				: new DailyInput.Correction(correction.getCheckIn(), correction.getCheckOut());
		DailyResultResponse result = null;
		String ruleMissing = null;
		try {
			result = DailyResultResponse.from(DailyAttendanceCalculator.calculate(new DailyInput(date, zone,
					dayScans.stream().map(AttendanceScan::getScannedAt).toList(), input,
					workRules.resolve(employee, date), holiday)));
		}
		catch (RuleNotConfiguredException ex) {
			ruleMissing = ex.getMessage();
		}
		return new DayRecord(date, result, ruleMissing, scanResponses, CorrectionResponse.from(correction),
				scanFlags.isEmpty() ? Set.of() : Set.copyOf(scanFlags));
	}

	private static String key(Long employeeId, LocalDate date) {
		return employeeId + "/" + date;
	}

}
