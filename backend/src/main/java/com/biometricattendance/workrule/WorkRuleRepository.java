package com.biometricattendance.workrule;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.biometricattendance.calculation.RuleLevel;

public interface WorkRuleRepository extends JpaRepository<WorkRule, Long> {

	List<WorkRule> findByScopeOrderByEffectiveFromDesc(RuleLevel scope);

	List<WorkRule> findByScopeAndCategoryIdOrderByEffectiveFromDesc(RuleLevel scope, Long categoryId);

	List<WorkRule> findByScopeAndEmployeeIdOrderByEffectiveFromDesc(RuleLevel scope, Long employeeId);

	/** Semua versi yang relevan untuk seorang karyawan: Global, kategorinya, dan miliknya sendiri. */
	@Query("""
			select r from WorkRule r
			where r.scope = com.biometricattendance.calculation.RuleLevel.GLOBAL
			   or (r.scope = com.biometricattendance.calculation.RuleLevel.CATEGORY and r.category.id = :categoryId)
			   or (r.scope = com.biometricattendance.calculation.RuleLevel.EMPLOYEE and r.employee.id = :employeeId)
			""")
	List<WorkRule> findApplicable(@Param("categoryId") Long categoryId, @Param("employeeId") Long employeeId);

}
