package com.biometricattendance.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.biometricattendance.AttendanceFixtures;
import com.biometricattendance.loan.LoanDtos.CreateLoanRequest;
import com.biometricattendance.loan.LoanService;
import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportFilter;
import com.biometricattendance.report.ReportDtos.ReportModel;

class ReportServiceFinancialTests extends AttendanceFixtures {

	@Autowired
	private ReportService reportService;

	@Autowired
	private LoanService loanService;

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

}
