package com.biometricattendance.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.audit.AuditService;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;
import com.biometricattendance.loan.LoanDtos.CreateLoanRequest;
import com.biometricattendance.loan.LoanDtos.DeductionItem;
import com.biometricattendance.loan.LoanDtos.LoanRepaymentResponse;
import com.biometricattendance.loan.LoanDtos.LoanResponse;

@Service
public class LoanService {

	private final EmployeeLoanRepository loanRepo;
	private final LoanRepaymentRepository repaymentRepo;
	private final EmployeeRepository employeeRepo;
	private final AuditService audit;

	public LoanService(EmployeeLoanRepository loanRepo, LoanRepaymentRepository repaymentRepo,
			EmployeeRepository employeeRepo, AuditService audit) {
		this.loanRepo = loanRepo;
		this.repaymentRepo = repaymentRepo;
		this.employeeRepo = employeeRepo;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<LoanResponse> getLoans(Long employeeId, String status) {
		List<EmployeeLoan> list;
		if (employeeId != null && status != null) {
			list = loanRepo.findByEmployeeIdAndStatus(employeeId, status);
		}
		else if (employeeId != null) {
			list = loanRepo.findByEmployeeIdOrderByLoanDateDesc(employeeId);
		}
		else {
			list = loanRepo.findAllWithEmployee();
		}
		return list.stream().map(LoanResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public List<LoanRepaymentResponse> getRepayments(Long loanId) {
		return repaymentRepo.findByLoanIdOrderByRepaymentDateDesc(loanId)
			.stream()
			.map(LoanRepaymentResponse::from)
			.toList();
	}

	@Transactional
	public LoanResponse createLoan(CreateLoanRequest req, String actor) {
		Employee employee = employeeRepo.findById(req.employeeId())
			.orElseThrow(() -> ApiException.notFound("Karyawan"));

		List<EmployeeLoan> active = loanRepo.findByEmployeeIdAndStatus(employee.getId(), "AKTIF");
		if (!active.isEmpty()) {
			throw ApiException.conflict("Karyawan " + employee.getFullName() + " masih memiliki pinjaman aktif ("
					+ active.get(0).getLoanNumber() + "). Harap lunasi terlebih dahulu.");
		}

		String loanNumber = generateLoanNumber(req.loanDate());
		EmployeeLoan loan = new EmployeeLoan(employee, loanNumber, req.loanDate(), req.totalAmount(),
				req.installmentAmount(), req.totalAmount(), "AKTIF", req.notes());

		EmployeeLoan saved = loanRepo.save(loan);
		audit.record(actor, "LOAN_CREATED", "EmployeeLoan", saved.getId(), null, LoanResponse.from(saved),
				"Pencatatan kasbon baru");
		return LoanResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public Map<Long, BigDecimal> getActiveInstallments(List<Long> employeeIds) {
		if (employeeIds == null || employeeIds.isEmpty()) {
			return Map.of();
		}
		List<EmployeeLoan> activeLoans = loanRepo.findActiveLoansByEmployeeIds(employeeIds);
		Map<Long, BigDecimal> map = new HashMap<>();
		for (EmployeeLoan loan : activeLoans) {
			BigDecimal installment = loan.getInstallmentAmount().min(loan.getRemainingAmount());
			map.put(loan.getEmployee().getId(), installment);
		}
		return map;
	}

	@Transactional
	public int applyPeriodDeductions(String periodLabel, String reportType, List<DeductionItem> items, String actor) {
		if (items == null || items.isEmpty()) {
			return 0;
		}
		int applied = 0;
		for (DeductionItem item : items) {
			if (item.amount() == null || item.amount().compareTo(BigDecimal.ZERO) <= 0) {
				continue;
			}
			List<EmployeeLoan> active = loanRepo.findByEmployeeIdAndStatus(item.employeeId(), "AKTIF");
			if (active.isEmpty()) {
				continue;
			}
			EmployeeLoan loan = active.get(0);

			// Idempotency: cek apakah untuk pinjaman dan periode ini sudah pernah dicatat
			if (repaymentRepo.findByLoanIdAndPeriodLabel(loan.getId(), periodLabel).isPresent()) {
				continue;
			}

			BigDecimal deduct = item.amount().min(loan.getRemainingAmount());
			BigDecimal before = loan.getRemainingAmount();
			BigDecimal after = before.subtract(deduct);

			LoanRepayment repayment = new LoanRepayment(loan, LocalDate.now(), deduct, periodLabel, reportType,
					"Potongan payroll " + periodLabel);
			repaymentRepo.save(repayment);

			loan.setRemainingAmount(after);
			if (after.compareTo(BigDecimal.ZERO) <= 0) {
				loan.setStatus("LUNAS");
			}
			loanRepo.save(loan);

			audit.record(actor, "LOAN_REPAYMENT", "EmployeeLoan", loan.getId(), before, after,
					"Potongan kasbon periode " + periodLabel);
			applied++;
		}
		return applied;
	}

	private String generateLoanNumber(LocalDate date) {
		String prefix = "KB-" + date.format(DateTimeFormatter.ofPattern("yyMM")) + "-";
		for (int i = 0; i < 10; i++) {
			String number = prefix + String.format("%04d", ThreadLocalRandom.current().nextInt(1000, 9999));
			if (loanRepo.findByLoanNumber(number).isEmpty()) {
				return number;
			}
		}
		return prefix + System.currentTimeMillis() % 10000;
	}

}

