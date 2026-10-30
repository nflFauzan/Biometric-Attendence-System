package com.biometricattendance.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.biometricattendance.AttendanceFixtures;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.loan.LoanDtos.CreateLoanRequest;
import com.biometricattendance.loan.LoanDtos.DeductionItem;
import com.biometricattendance.loan.LoanDtos.LoanResponse;

class LoanServiceTests extends AttendanceFixtures {

	@Autowired
	private LoanService loanService;

	@Test
	void testCreateLoanAndPreventMultipleActiveLoans() throws Exception {
		long branchId = createBranch();
		long empId = createEmployee(branchId, "7701");

		CreateLoanRequest req = new CreateLoanRequest(empId, LocalDate.of(2026, 10, 1), new BigDecimal("1000000.00"),
				new BigDecimal("100000.00"), "Kasbon operasional");

		LoanResponse created = loanService.createLoan(req, "owner");
		assertNotNull(created.id());
		assertEquals("AKTIF", created.status());
		assertThat(created.remainingAmount()).isEqualByComparingTo("1000000.00");

		// Pinjaman kedua saat masih aktif harus ditolak
		assertThatThrownBy(() -> loanService.createLoan(req, "owner"))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("masih memiliki pinjaman aktif");
	}

	@Test
	void testGetActiveInstallmentsAndApplyDeductions() throws Exception {
		long branchId = createBranch();
		long empId1 = createEmployee(branchId, "7702");
		long empId2 = createEmployee(branchId, "7703");

		loanService.createLoan(new CreateLoanRequest(empId1, LocalDate.of(2026, 10, 1), new BigDecimal("500000.00"),
				new BigDecimal("100000.00"), "Kasbon 1"), "owner");

		Map<Long, BigDecimal> installments = loanService.getActiveInstallments(List.of(empId1, empId2));
		assertThat(installments.get(empId1)).isEqualByComparingTo("100000.00");
		assertThat(installments.get(empId2)).isNull();

		// Terapkan potongan kasbon untuk periode ini
		String period = "Minggu 26 Sep - 03 Okt 2026";
		List<DeductionItem> items = List.of(new DeductionItem(empId1, new BigDecimal("100000.00")));
		int applied = loanService.applyPeriodDeductions(period, "UPAH_MINGGUAN", items, "owner");
		assertEquals(1, applied);

		// Periksa sisa saldo pinjaman berkurang menjadi Rp 400.000
		List<LoanResponse> loans = loanService.getLoans(empId1, null);
		assertThat(loans.get(0).remainingAmount()).isEqualByComparingTo("400000.00");

		// Panggilan ulang untuk periode yang sama harus idempoten (0 applied, tidak memotong ganda)
		int reapplied = loanService.applyPeriodDeductions(period, "UPAH_MINGGUAN", items, "owner");
		assertEquals(0, reapplied);
		assertThat(loanService.getLoans(empId1, null).get(0).remainingAmount()).isEqualByComparingTo("400000.00");
	}

	@Test
	void testPartialPaymentAndAutoMarkLunas() throws Exception {
		long branchId = createBranch();
		long empId = createEmployee(branchId, "7704");

		// Pinjaman Rp 150.000 dengan cicilan Rp 100.000
		loanService.createLoan(new CreateLoanRequest(empId, LocalDate.of(2026, 10, 1), new BigDecimal("150000.00"),
				new BigDecimal("100000.00"), "Kasbon kecil"), "owner");

		// Potong pertama Rp 100.000
		loanService.applyPeriodDeductions("Periode 1", "UPAH_MINGGUAN",
				List.of(new DeductionItem(empId, new BigDecimal("100000.00"))), "owner");
		assertThat(loanService.getLoans(empId, null).get(0).remainingAmount()).isEqualByComparingTo("50000.00");
		assertEquals("AKTIF", loanService.getLoans(empId, null).get(0).status());

		// Potong kedua Rp 100.000 (tetapi sisa tinggal 50.000, jadi hanya potong 50.000 dan lunas)
		loanService.applyPeriodDeductions("Periode 2", "UPAH_MINGGUAN",
				List.of(new DeductionItem(empId, new BigDecimal("100000.00"))), "owner");
		LoanResponse loanAfter = loanService.getLoans(empId, null).get(0);
		assertThat(loanAfter.remainingAmount()).isEqualByComparingTo("0.00");
		assertEquals("LUNAS", loanAfter.status());
	}

}

