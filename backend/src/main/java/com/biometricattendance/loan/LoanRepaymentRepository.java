package com.biometricattendance.loan;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, Long> {

	List<LoanRepayment> findByLoanIdOrderByRepaymentDateDesc(Long loanId);

	Optional<LoanRepayment> findByLoanIdAndPeriodLabel(Long loanId, String periodLabel);

}

