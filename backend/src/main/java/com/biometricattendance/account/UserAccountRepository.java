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

	/** Akun aktif dengan role tertentu, tidak termasuk akun yang terhubung ke karyawan {@code employeeId}. */
	@Query("""
			select count(u) from UserAccount u
			where u.role = :role and u.active = true
			  and (u.employee is null or u.employee.id <> :employeeId)
			""")
	long countActiveByRoleExcludingEmployee(@org.springframework.data.repository.query.Param("role") Role role,
			@org.springframework.data.repository.query.Param("employeeId") Long employeeId);

	@Query("select u from UserAccount u left join fetch u.employee where u.username in :usernames")
	List<UserAccount> findByUsernameInWithEmployee(
			@org.springframework.data.repository.query.Param("usernames") java.util.Collection<String> usernames);

	@Query("select u from UserAccount u left join fetch u.employee order by u.username")
	List<UserAccount> findAllWithEmployee();

	@Query("select u from UserAccount u left join fetch u.employee where u.role = :role order by u.username")
	List<UserAccount> findByRoleWithEmployee(Role role);

}
