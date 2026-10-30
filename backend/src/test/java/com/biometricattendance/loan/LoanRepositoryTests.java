package com.biometricattendance.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.biometricattendance.AttendanceFixtures;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;

class LoanRepositoryTests extends AttendanceFixtures {

	@Autowired
	private EmployeeLoanRepository loanRepo;

	@Autowired
	private LoanRepaymentRepository repaymentRepo;

	@Autowired
	private EmployeeRepository employeeRepo;

	@Test
	void testCreateLoanAndRepayment() throws Exception {
		long branchId = createBranch();
		long empId = createEmployee(branchId, "8801");
		Employee employee = employeeRepo.findById(empId).orElseThrow();

		EmployeeLoan loan = new EmployeeLoan(employee, "KB-TEST-001", LocalDate.of(2026, 10, 1),
				new BigDecimal("1000000.00"), new BigDecimal("100000.00"), new BigDecimal("1000000.00"), "AKTIF",
				"Pinjaman operasional supir");
		EmployeeLoan savedLoan = loanRepo.save(loan);
		assertNotNull(savedLoan.getId());
		assertThat(savedLoan.getStatus()).isEqualTo("AKTIF");

		LoanRepayment repayment = new LoanRepayment(savedLoan, LocalDate.of(2026, 10, 8), new BigDecimal("100000.00"),
				"Minggu 1 Okt 2026", "UPAH_MINGGUAN", "Potongan payroll pertama");
		LoanRepayment savedRep = repaymentRepo.save(repayment);
		assertNotNull(savedRep.getId());
		assertThat(savedRep.getAmount()).isEqualByComparingTo("100000.00");
	}

}

