package com.biometricattendance.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.attendance.AttendanceDtos.DayRecord;
import com.biometricattendance.attendance.AttendanceDtos.EmployeeDays;
import com.biometricattendance.attendance.AttendanceService;
import com.biometricattendance.branch.BranchRepository;
import com.biometricattendance.calculation.CalculationDtos.DailyResultResponse;
import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.calculation.DailyResult.TimeValue;
import com.biometricattendance.category.EmployeeCategoryRepository;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.employee.EmployeeRepository;
import com.biometricattendance.overtime.OvertimeService;
import com.biometricattendance.report.ReportDtos.ReportDay;
import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportFilter;
import com.biometricattendance.report.ReportDtos.ReportModel;
import com.biometricattendance.report.ReportDtos.ReportTotals;

/**
 * Menyusun model laporan dari rekap harian ({@link AttendanceService#range}) dan lembur tervalidasi
 * ({@link OvertimeService#validatedSeconds}). Jam kerja tidak dihitung ulang di sini; isi sel kisi dan nama hari
 * ditentukan di sini supaya penulis Excel dan PDF hanya merender.
 */
@Service
public class ReportService {

	private static final String[] DAY_NAMES = { "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu" };

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

	private final AttendanceService attendance;

	private final OvertimeService overtime;

	private final BranchRepository branches;

	private final EmployeeCategoryRepository categories;

	private final EmployeeRepository employees;

	public ReportService(AttendanceService attendance, OvertimeService overtime, BranchRepository branches,
			EmployeeCategoryRepository categories, EmployeeRepository employees) {
		this.attendance = attendance;
		this.overtime = overtime;
		this.branches = branches;
		this.categories = categories;
		this.employees = employees;
	}

	@Transactional(readOnly = true)
	public ReportModel build(ReportType type, ReportFilter filter) {
		List<EmployeeDays> data = attendance.range(filter.from(), filter.to(), filter.branchId(),
				filter.categoryId(), filter.employeeId());
		Map<Long, Map<LocalDate, Long>> validated = overtime.validatedSeconds(data);

		List<LocalDate> dates = filter.from().datesUntil(filter.to().plusDays(1)).toList();
		List<ReportEmployee> rows = new ArrayList<>();
		for (EmployeeDays employee : data) {
			rows.add(employeeRow(employee, validated.getOrDefault(employee.employeeId(), Map.of())));
		}
		return new ReportModel(type, type.title(), DATE.format(filter.from()) + " s.d. " + DATE.format(filter.to()),
				filterLabels(filter), dates, rows);
	}

	private ReportEmployee employeeRow(EmployeeDays employee, Map<LocalDate, Long> validated) {
		List<ReportDay> days = new ArrayList<>();
		int countingDays = 0;
		int leaveDays = 0;
		int absentDays = 0;
		long regularSeconds = 0;
		long overtimeSeconds = 0;
		for (DayRecord record : employee.days()) {
			DailyResultResponse result = record.result();
			if (!employee.employed(record.date()) || result == null) {
				days.add(blankDay(record.date()));
				continue;
			}
			AttendanceStatus status = result.status();
			long regular = orZero(result.regularWorkSeconds());
			long approved = validated.getOrDefault(record.date(), 0L);
			switch (status) {
				case LENGKAP -> countingDays++;
				case IZIN -> leaveDays++;
				case TIDAK_HADIR -> absentDays++;
				default -> {
				}
			}
			regularSeconds += regular;
			overtimeSeconds += approved;
			days.add(new ReportDay(record.date(), dayName(record.date()), status, timeOf(result.checkIn()),
					timeOf(result.checkOut()), hours(regular), hours(approved), result.scanTimes(), markOf(status)));
		}
		return new ReportEmployee(employee.employeeNumber(), employee.fullName(), employee.branchName(),
				employee.categoryName(), days,
				new ReportTotals(countingDays, leaveDays, absentDays, hours(regularSeconds), hours(overtimeSeconds)));
	}

	/** Hari di luar masa kerja atau tanpa hasil perhitungan: kosong dan tidak dihitung. */
	private static ReportDay blankDay(LocalDate date) {
		return new ReportDay(date, dayName(date), null, null, null, hours(0), hours(0), List.of(), "");
	}

	private static String markOf(AttendanceStatus status) {
		return switch (status) {
			case LENGKAP -> "V";
			case TIDAK_LENGKAP -> "?";
			case IZIN -> "I";
			case TIDAK_HADIR, TIDAK_ADA_SCAN -> "";
		};
	}

	private static String dayName(LocalDate date) {
		return DAY_NAMES[date.getDayOfWeek().getValue() - 1];
	}

	private static LocalTime timeOf(TimeValue value) {
		return value == null ? null : value.time();
	}

	private static long orZero(Long value) {
		return value == null ? 0 : value;
	}

	private static BigDecimal hours(long seconds) {
		return BigDecimal.valueOf(seconds).divide(SECONDS_PER_HOUR, 2, RoundingMode.HALF_UP);
	}

	private List<String> filterLabels(ReportFilter filter) {
		String branch = filter.branchId() == null ? "Semua cabang"
				: branches.findById(filter.branchId()).orElseThrow(() -> ApiException.notFound("Cabang")).getName();
		String category = filter.categoryId() == null ? "Semua kategori"
				: categories.findById(filter.categoryId())
					.orElseThrow(() -> ApiException.notFound("Kategori karyawan"))
					.getName();
		String employee = filter.employeeId() == null ? "Semua karyawan"
				: employees.findById(filter.employeeId())
					.orElseThrow(() -> ApiException.notFound("Karyawan"))
					.getFullName();
		return List.of(branch, category, employee);
	}

}
