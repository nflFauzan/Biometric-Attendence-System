package com.biometricattendance.holiday;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

	List<Holiday> findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate from, LocalDate to);

	boolean existsByHolidayDate(LocalDate date);

	boolean existsByHolidayDateAndIdNot(LocalDate date, Long id);

}
