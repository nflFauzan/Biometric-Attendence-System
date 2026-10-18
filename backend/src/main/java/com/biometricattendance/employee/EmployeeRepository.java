package com.biometricattendance.employee;

import java.time.LocalDate;

import java.util.List;
import java.util.Optional;

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

	@Query("select e from Employee e join fetch e.branch where e.devicePin = :pin")
	Optional<Employee> findByDevicePin(@Param("pin") String pin);

	/**
	 * Kandidat penghapusan sidik jari terjadwal (spec §17.4): belum ditandai, dan nonaktif atau tanggal keluarnya
	 * sebelum {@code maxEndDate}. Zona waktu cabang diperiksa oleh pemanggil.
	 */
	@Query("select e from Employee e join fetch e.branch where e.fingerprintsRemovedAt is null"
			+ " and (e.active = false or (e.endDate is not null and e.endDate < :maxEndDate))")
	List<Employee> findDepartureCandidates(@Param("maxEndDate") LocalDate maxEndDate);

	boolean existsByDevicePin(String devicePin);

	boolean existsByDevicePinAndIdNot(String devicePin, Long id);

	boolean existsByEmployeeNumberIgnoreCase(String employeeNumber);

	boolean existsByEmployeeNumberIgnoreCaseAndIdNot(String employeeNumber, Long id);

}
