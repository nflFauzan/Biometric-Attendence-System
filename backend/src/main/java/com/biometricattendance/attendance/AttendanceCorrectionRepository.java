package com.biometricattendance.attendance;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, Long> {

	Optional<AttendanceCorrection> findByEmployeeIdAndWorkDateAndSupersededAtIsNull(Long employeeId, LocalDate date);

	@Query("""
			select c from AttendanceCorrection c
			where c.employeeId in :employeeIds and c.workDate between :from and :to and c.supersededAt is null
			""")
	List<AttendanceCorrection> findActive(@Param("employeeIds") Collection<Long> employeeIds,
			@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** Semua koreksi termasuk yang sudah digantikan, terbaru lebih dulu. */
	List<AttendanceCorrection> findByEmployeeIdAndWorkDateBetweenOrderByIdDesc(Long employeeId, LocalDate from,
			LocalDate to);

}
