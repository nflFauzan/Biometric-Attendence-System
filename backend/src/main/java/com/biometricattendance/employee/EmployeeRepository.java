package com.biometricattendance.employee;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

	@Query("""
			select e from Employee e join fetch e.branch join fetch e.category
			where (:branchId is null or e.branch.id = :branchId)
			  and (:active is null or e.active = :active)
			  and (:search is null
			       or lower(e.fullName) like lower(concat('%', cast(:search as string), '%'))
			       or lower(e.employeeNumber) like lower(concat('%', cast(:search as string), '%')))
			order by e.fullName
			""")
	List<Employee> search(@Param("branchId") Long branchId, @Param("active") Boolean active,
			@Param("search") String search);

	boolean existsByEmployeeNumberIgnoreCase(String employeeNumber);

	boolean existsByEmployeeNumberIgnoreCaseAndIdNot(String employeeNumber, Long id);

}
