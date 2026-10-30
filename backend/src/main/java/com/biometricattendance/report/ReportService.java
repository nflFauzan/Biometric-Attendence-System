package com.biometricattendance.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.attendance.AttendanceDtos.DayRecord;
import com.biometricattendance.attendance.AttendanceDtos.EmployeeDays;
import com.biometricattendance.attendance.AttendanceService;
import com.biometricattendance.branch.BranchRepository;
import com.biometricattendance.calculation.CalculationDtos.DailyResultResponse;
import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.calculation.DailyResult.TimeValue;
import com.biometricattendance.calculation.EffectiveRule;
import com.biometricattendance.category.EmployeeCategoryRepository;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;
import com.biometricattendance.loan.LoanService;
import com.biometricattendance.overtime.OvertimeService;
import com.biometricattendance.report.ReportDtos.BranchSummary;
import com.biometricattendance.report.ReportDtos.ReportDay;
import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportFilter;
import com.biometricattendance.report.ReportDtos.ReportFinancial;
import com.biometricattendance.report.ReportDtos.ReportModel;
import com.biometricattendance.report.ReportDtos.ReportTotals;
import com.biometricattendance.workrule.WorkRuleService;

/**
 * Menyusun model laporan dari rekap harian ({@link AttendanceService#range}), lembur tervalidasi
 * ({@link OvertimeService#validatedSeconds}), dan modul kasbon ({@link LoanService}).
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

	private final LoanService loanService;

	private final WorkRuleService workRuleService;

	public ReportService(AttendanceService attendance, OvertimeService overtime, BranchRepository branches,
			EmployeeCategoryRepository categories, EmployeeRepository employees, LoanService loanService,
			WorkRuleService workRuleService) {
		this.attendance = attendance;
		this.overtime = overtime;
		this.branches = branches;
		this.categories = categories;
		this.employees = employees;
		this.loanService = loanService;
		this.workRuleService = workRuleService;
	}

	@Transactional(readOnly = true)
	public ReportModel build(ReportType type, ReportFilter filter) {
		List<EmployeeDays> data = attendance.range(filter.from(), filter.to(), filter.branchId(),
				filter.categoryId(), filter.employeeId());
		Map<Long, Map<LocalDate, Long>> validated = overtime.validatedSeconds(data);

		List<Long> employeeIds = data.stream().map(EmployeeDays::employeeId).toList();
		Map<Long, BigDecimal> activeLoans = loanService.getActiveInstallments(employeeIds);

		List<LocalDate> dates = filter.from().datesUntil(filter.to().plusDays(1)).toList();
		List<ReportEmployee> rows = new ArrayList<>();
		for (EmployeeDays employee : data) {
			rows.add(employeeRow(type, employee, validated.getOrDefault(employee.employeeId(), Map.of()),
					activeLoans.get(employee.employeeId()), filter.to()));
		}

		List<BranchSummary> branchSummaries = computeBranchSummaries(rows);

		return new ReportModel(type, type.title(), DATE.format(filter.from()) + " s.d. " + DATE.format(filter.to()),
				filterLabels(filter), dates, rows, branchSummaries);
	}

	private ReportEmployee employeeRow(ReportType type, EmployeeDays employee, Map<LocalDate, Long> validated,
			BigDecimal loanInstallment, LocalDate refDate) {
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

		ReportTotals totals = new ReportTotals(countingDays, leaveDays, absentDays, hours(regularSeconds),
				hours(overtimeSeconds));

		ReportFinancial financial = computeFinancial(type, employee.employeeId(), totals, loanInstallment, refDate);

		return new ReportEmployee(employee.employeeId(), employee.employeeNumber(), employee.fullName(),
				employee.branchName(), employee.categoryName(), days, totals, financial);
	}

	private ReportFinancial computeFinancial(ReportType type, Long employeeId, ReportTotals totals,
			BigDecimal loanInstallment, LocalDate date) {
		if (type != ReportType.UPAH_MINGGUAN && type != ReportType.GAJI_BULANAN) {
			return null;
		}

		Employee emp = employees.findById(employeeId).orElse(null);
		EffectiveRule rule = emp == null ? null : workRuleService.resolve(emp, date);

		BigDecimal dailyWage = rule != null && rule.values().dailyWage() != null ? rule.values().dailyWage()
				: BigDecimal.valueOf(100000);
		BigDecimal overtimeRate = rule != null && rule.values().overtimeRatePerHour() != null
				? rule.values().overtimeRatePerHour() : BigDecimal.valueOf(14000);
		BigDecimal baseSalary = rule != null ? rule.values().baseSalary() : null;

		BigDecimal days = BigDecimal.valueOf(totals.countingDays());
		BigDecimal otHours = totals.validatedOvertimeHours();
		BigDecimal overtimeWage = overtimeRate.multiply(otHours).setScale(2, RoundingMode.HALF_UP);
		BigDecimal installment = loanInstallment != null ? loanInstallment : BigDecimal.ZERO;

		if (type == ReportType.UPAH_MINGGUAN) {
			BigDecimal grossDailyWage = dailyWage.multiply(days).setScale(2, RoundingMode.HALF_UP);
			BigDecimal grossTotal = grossDailyWage.add(overtimeWage);
			BigDecimal actualDeduction = installment.min(grossTotal);
			BigDecimal netWage = grossTotal.subtract(actualDeduction);
			return new ReportFinancial(dailyWage, grossDailyWage, overtimeRate, overtimeWage, actualDeduction, netWage,
					null, null);
		}
		else {
			// GAJI_BULANAN: Onsite = Days * Wages (standar Rp 40.000)
			BigDecimal onsiteRate = dailyWage;
			BigDecimal onsiteWage = onsiteRate.multiply(days).setScale(2, RoundingMode.HALF_UP);
			BigDecimal base = baseSalary != null ? baseSalary : BigDecimal.ZERO;
			BigDecimal grossTotal = base.add(onsiteWage).add(overtimeWage);
			BigDecimal actualDeduction = installment.min(grossTotal);
			BigDecimal netWage = grossTotal.subtract(actualDeduction);
			return new ReportFinancial(onsiteRate, null, overtimeRate, overtimeWage, actualDeduction, netWage, base,
					onsiteWage);
		}
	}

	private List<BranchSummary> computeBranchSummaries(List<ReportEmployee> rows) {
		Map<String, BigDecimal> branchTotals = new LinkedHashMap<>();
		for (ReportEmployee emp : rows) {
			if (emp.financial() != null && emp.financial().netWage() != null) {
				String bName = emp.branchName() != null ? emp.branchName() : "Lainnya";
				branchTotals.merge(bName, emp.financial().netWage(), BigDecimal::add);
			}
		}
		List<BranchSummary> list = branchTotals.entrySet()
			.stream()
			.map(e -> new BranchSummary(e.getKey(), e.getValue()))
			.collect(Collectors.toList());

		BigDecimal grandTotal = branchTotals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		list.add(new BranchSummary("Cash", grandTotal));
		return list;
	}

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

	private static BigDecimal hours(long seconds) {
		return BigDecimal.valueOf(seconds).divide(SECONDS_PER_HOUR, 2, RoundingMode.HALF_UP);
	}

	private static long orZero(Long value) {
		return value == null ? 0L : value;
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
