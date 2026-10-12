package com.biometricattendance.leave;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

	List<LeaveType> findAllByOrderByNameAsc();

	List<LeaveType> findByActiveTrueOrderByNameAsc();

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

}
