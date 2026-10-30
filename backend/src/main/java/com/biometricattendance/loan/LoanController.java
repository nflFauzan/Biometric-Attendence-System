package com.biometricattendance.loan;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.loan.LoanDtos.ApplyDeductionsRequest;
import com.biometricattendance.loan.LoanDtos.CreateLoanRequest;
import com.biometricattendance.loan.LoanDtos.LoanRepaymentResponse;
import com.biometricattendance.loan.LoanDtos.LoanResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

	private final LoanService loanService;

	public LoanController(LoanService loanService) {
		this.loanService = loanService;
	}

	@GetMapping
	public List<LoanResponse> list(@RequestParam(required = false) Long employeeId,
			@RequestParam(required = false) String status) {
		return loanService.getLoans(employeeId, status);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public LoanResponse create(@Valid @RequestBody CreateLoanRequest request) {
		return loanService.createLoan(request, CurrentUser.username());
	}

	@GetMapping("/{id}/repayments")
	public List<LoanRepaymentResponse> repayments(@PathVariable Long id) {
		return loanService.getRepayments(id);
	}

	@PostMapping("/apply-period-deductions")
	public Map<String, Object> applyDeductions(@Valid @RequestBody ApplyDeductionsRequest request) {
		int applied = loanService.applyPeriodDeductions(request.periodLabel(), request.reportType(), request.items(),
				CurrentUser.username());
		return Map.of("applied", applied, "periodLabel", request.periodLabel());
	}

}

