package com.biometricattendance.account;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

	Optional<UserAccount> findByUsername(String username);

	boolean existsByUsername(String username);

	Optional<UserAccount> findByEmployeeId(Long employeeId);

	boolean existsByRole(Role role);

	long countByRoleAndActiveTrue(Role role);

	@Query("select u from UserAccount u left join fetch u.employee order by u.username")
	List<UserAccount> findAllWithEmployee();

	@Query("select u from UserAccount u left join fetch u.employee where u.role = :role order by u.username")
	List<UserAccount> findByRoleWithEmployee(Role role);

}
