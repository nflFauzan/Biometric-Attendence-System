package com.biometricattendance.loan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class LoanDtos {

	private LoanDtos() {
	}

	public record CreateLoanRequest(
			@NotNull(message = "Karyawan wajib dipilih") Long employeeId,
			@NotNull(message = "Tanggal pinjaman wajib diisi") LocalDate loanDate,
			@NotNull(message = "Total pinjaman wajib diisi") @DecimalMin(value = "1.00", message = "Total pinjaman minimal 1") BigDecimal totalAmount,
			@NotNull(message = "Nominal cicilan wajib diisi") @DecimalMin(value = "1.00", message = "Cicilan minimal 1") BigDecimal installmentAmount,
			String notes) {
	}

	public record ApplyDeductionsRequest(
			@NotBlank(message = "Label periode wajib diisi") String periodLabel,
			@NotBlank(message = "Tipe laporan wajib diisi") String reportType,
			List<DeductionItem> items) {
	}

	public record DeductionItem(
			@NotNull(message = "Karyawan wajib diisi") Long employeeId,
			@NotNull(message = "Nominal potongan wajib diisi") BigDecimal amount) {
	}

	public record LoanResponse(
			Long id,
			Long employeeId,
			String employeeNumber,
			String employeeName,
			String branchName,
			String loanNumber,
			LocalDate loanDate,
			BigDecimal totalAmount,
			BigDecimal installmentAmount,
			BigDecimal remainingAmount,
			String status,
			String notes,
			Instant createdAt) {

		public static LoanResponse from(EmployeeLoan loan) {
			return new LoanResponse(
					loan.getId(),
					loan.getEmployee().getId(),
					loan.getEmployee().getEmployeeNumber(),
					loan.getEmployee().getFullName(),
					loan.getEmployee().getBranch() != null ? loan.getEmployee().getBranch().getName() : "-",
					loan.getLoanNumber(),
					loan.getLoanDate(),
					loan.getTotalAmount(),
					loan.getInstallmentAmount(),
					loan.getRemainingAmount(),
					loan.getStatus(),
					loan.getNotes(),
					loan.getCreatedAt());
		}
	}

	public record LoanRepaymentResponse(
			Long id,
			Long loanId,
			LocalDate repaymentDate,
			BigDecimal amount,
			String periodLabel,
			String reportType,
			String notes,
			Instant createdAt) {

		public static LoanRepaymentResponse from(LoanRepayment rep) {
			return new LoanRepaymentResponse(
					rep.getId(),
					rep.getLoan().getId(),
					rep.getRepaymentDate(),
					rep.getAmount(),
					rep.getPeriodLabel(),
					rep.getReportType(),
					rep.getNotes(),
					rep.getCreatedAt());
		}
	}

}

