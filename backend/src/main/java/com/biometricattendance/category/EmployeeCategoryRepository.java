package com.biometricattendance.category;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeCategoryRepository extends JpaRepository<EmployeeCategory, Long> {

	List<EmployeeCategory> findAllByOrderByNameAsc();

	boolean existsByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

}
