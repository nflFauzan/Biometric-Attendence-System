package com.biometricattendance.overtime;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OvertimeValidationRepository extends JpaRepository<OvertimeValidation, Long> {

	List<OvertimeValidation> findByEmployeeIdInAndWorkDateBetween(Collection<Long> employeeIds, LocalDate from,
			LocalDate to);

}
