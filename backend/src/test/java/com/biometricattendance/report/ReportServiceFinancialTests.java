package com.biometricattendance.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.biometricattendance.AttendanceFixtures;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.loan.LoanDtos.CreateLoanRequest;
import com.biometricattendance.loan.LoanService;
import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportFilter;
import com.biometricattendance.report.ReportDtos.ReportModel;
import com.biometricattendance.report.ReportDtos.ReportSettingRequest;

class ReportServiceFinancialTests extends AttendanceFixtures {

	@Autowired
	private ReportService reportService;

	@Autowired
	private LoanService loanService;

	@Autowired
	private ReportSettingService settings;

	@Test
	void testWeeklyWageCalculationMatchesAS1() throws Exception {
		long branchId = createBranch();
		String pin = uniquePin();
		long empId = createEmployee(branchId, pin);
		String sn = uniqueSerial();
		createDevice(branchId, sn, ACCEPT_FROM);
		addFullRule(empId);
		jdbc.update("update work_rules set daily_wage = 100000, overtime_rate_per_hour = 14000 where employee_id = ?",
				empId);

		// Scan masuk dan pulang 1 hari (Senin 2026-10-05)
		postAttlog(sn, lines(line(pin, "2026-10-05 08:00:00"), line(pin, "2026-10-05 17:00:00")));

		// Buat kasbon cicilan Rp 50.000
		loanService.createLoan(new CreateLoanRequest(empId, LocalDate.of(2026, 10, 1), new BigDecimal("500000.00"),
				new BigDecimal("50000.00"), "Kasbon supir"), "owner");

		LocalDate from = LocalDate.of(2026, 10, 3); // Sabtu
		LocalDate to = LocalDate.of(2026, 10, 9); // Jumat

		ReportFilter filter = new ReportFilter(from, to, branchId, null, empId);
		ReportModel model = reportService.build(ReportType.UPAH_MINGGUAN, filter);

		assertNotNull(model);
		assertEquals(ReportType.UPAH_MINGGUAN, model.type());
		List<ReportEmployee> rows = model.employees();
		assertThat(rows).isNotEmpty();

		ReportEmployee empRow = rows.get(0);
		// 1 hari hadir, upah harian 100.000, gross 100.000, cicilan 50.000, net 50.000
		assertThat(empRow.totals().countingDays()).isEqualTo(1);
		assertThat(empRow.financial().dailyWage()).isEqualByComparingTo("100000.00");
		assertThat(empRow.financial().grossDailyWage()).isEqualByComparingTo("100000.00");
		assertThat(empRow.financial().loanDeduction()).isEqualByComparingTo("50000.00");
		assertThat(empRow.financial().netWage()).isEqualByComparingTo("50000.00");
	}

	@Test
	void weeklyReportExcludesBaseSalaryStaffAndSortsByEmployeeNumber() throws Exception {
		long branchId = createBranch();
		long staff = createEmployee(branchId, uniquePin());
		long first = createEmployee(branchId, uniquePin());
		long second = createEmployee(branchId, uniquePin());
		addFullRule(staff);
		addFullRule(first);
		addFullRule(second);
		jdbc.update("update work_rules set base_salary = 3000000 where employee_id = ?", staff);
		jdbc.update("update work_rules set daily_wage = 100000 where employee_id in (?, ?)", first, second);

		ReportModel model = reportService.build(ReportType.UPAH_MINGGUAN,
				new ReportFilter(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 9), branchId, null, null));

		List<Long> ids = model.employees().stream().map(ReportEmployee::employeeId).toList();
		assertThat(ids).containsExactlyInAnyOrder(first, second);
		List<String> numbers = model.employees().stream().map(ReportEmployee::employeeNumber).toList();
		assertThat(numbers).isSorted();
	}

	@Test
	void testMonthlySalaryCalculationMatchesAS5() throws Exception {
		long branchId = createBranch();
		String pin = uniquePin();
		long empId = createEmployee(branchId, pin);
		String sn = uniqueSerial();
		createDevice(branchId, sn, ACCEPT_FROM);
		addFullRule(empId);
		jdbc.update("update work_rules set daily_wage = 40000, base_salary = 3000000, overtime_rate_per_hour = 14000 where employee_id = ?",
				empId);

		// Scan masuk dan pulang 1 hari
		postAttlog(sn, lines(line(pin, "2026-09-01 08:00:00"), line(pin, "2026-09-01 17:00:00")));

		LocalDate from = LocalDate.of(2026, 9, 1);
		LocalDate to = LocalDate.of(2026, 9, 30);

		ReportFilter filter = new ReportFilter(from, to, branchId, null, empId);
		ReportModel model = reportService.build(ReportType.GAJI_BULANAN, filter);

		assertNotNull(model);
		assertEquals(ReportType.GAJI_BULANAN, model.type());
		List<ReportEmployee> rows = model.employees();
		assertThat(rows).isNotEmpty();

		ReportEmployee empRow = rows.get(0);
		assertThat(empRow.totals().countingDays()).isEqualTo(1);
		assertThat(empRow.financial().baseSalary()).isEqualByComparingTo("3000000.00");
		assertThat(empRow.financial().onsiteWage()).isEqualByComparingTo("40000.00");
		assertThat(empRow.financial().netWage()).isEqualByComparingTo("3040000.00");
	}

	private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);

	private static final LocalDate SEP_30 = LocalDate.of(2026, 9, 30);

	@Test
	void monthlyReportRejectsPartialMonth() throws Exception {
		long branchId = createBranch();
		ReportFilter partial = new ReportFilter(SEP_1, LocalDate.of(2026, 9, 15), branchId, null, null);
		assertThatThrownBy(() -> reportService.build(ReportType.GAJI_BULANAN, partial))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("Laporan gaji bulanan harus satu bulan penuh");
	}

	@Test
	void monthlyReportCarriesHeader() throws Exception {
		long branchId = createBranch();
		settings.save("gaji-bulanan", new ReportSettingRequest("MANAGER SAMARAN", "DEPARTEMEN SAMARAN"));
		ReportModel model = reportService.build(ReportType.GAJI_BULANAN,
				new ReportFilter(SEP_1, SEP_30, branchId, null, null));
		assertThat(model.managerName()).isEqualTo("MANAGER SAMARAN");
		assertThat(model.departmentName()).isEqualTo("DEPARTEMEN SAMARAN");
		assertThat(model.monthLabel()).isEqualTo("September 2026");
	}

	@Test
	void offDatesFollowWorkRule() throws Exception {
		long branchId = createBranch();
		long empId = createEmployee(branchId, uniquePin());
		addFullRule(empId);
		jdbc.update("update work_rules set work_days = 'MON,TUE,WED,THU,FRI,SAT' where employee_id = ?", empId);
		LocalDate holiday = LocalDate.of(2026, 9, 17);
		jdbc.update("delete from holidays where holiday_date = ?", holiday);
		jdbc.update("insert into holidays (holiday_date, name) values (?, 'Libur uji')", holiday);
		try {
			ReportModel model = reportService.build(ReportType.GAJI_BULANAN,
					new ReportFilter(SEP_1, SEP_30, branchId, null, empId));
			assertThat(model.offDates()).containsExactly(LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 13),
					holiday, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 27));
		}
		finally {
			jdbc.update("delete from holidays where holiday_date = ?", holiday);
		}
	}

	@Test
	void missingRateIsNotInvented() throws Exception {
		long branchId = createBranch();
		long empId = createEmployee(branchId, uniquePin());
		addFullRule(empId);
		ReportModel model = reportService.build(ReportType.GAJI_BULANAN,
				new ReportFilter(SEP_1, SEP_30, branchId, null, empId));
		assertThat(model.employees().get(0).financial().overtimeRate()).isNull();
		assertThat(model.employees().get(0).financial().dailyWage()).isNull();
		assertThat(model.employees().get(0).financial().baseSalary()).isNull();
		assertThat(model.employees().get(0).financial().overtimeWage()).isEqualByComparingTo("0");
	}

	@Test
	void monthlyRowsSortedByEmployeeNumber() throws Exception {
		long branchId = createBranch();
		createEmployee(branchId, uniquePin());
		createEmployee(branchId, uniquePin());
		createEmployee(branchId, uniquePin());
		ReportModel model = reportService.build(ReportType.GAJI_BULANAN,
				new ReportFilter(SEP_1, SEP_30, branchId, null, null));
		assertThat(model.employees()).extracting(ReportEmployee::employeeNumber).isSorted();
	}

}
