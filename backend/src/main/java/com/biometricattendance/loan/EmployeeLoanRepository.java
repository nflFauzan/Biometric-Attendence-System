package com.biometricattendance.loan;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeLoanRepository extends JpaRepository<EmployeeLoan, Long> {

	Optional<EmployeeLoan> findByLoanNumber(String loanNumber);

	List<EmployeeLoan> findByEmployeeIdOrderByLoanDateDesc(Long employeeId);

	List<EmployeeLoan> findByEmployeeIdAndStatus(Long employeeId, String status);

	@Query("SELECT l FROM EmployeeLoan l JOIN FETCH l.employee WHERE l.status = 'AKTIF' AND l.employee.id IN :employeeIds")
	List<EmployeeLoan> findActiveLoansByEmployeeIds(@Param("employeeIds") List<Long> employeeIds);

	@Query("SELECT l FROM EmployeeLoan l JOIN FETCH l.employee ORDER BY l.loanDate DESC")
	List<EmployeeLoan> findAllWithEmployee();

}

