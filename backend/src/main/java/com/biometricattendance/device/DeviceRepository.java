package com.biometricattendance.device;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<Device, Long> {

	@Query("select d from Device d join fetch d.branch order by d.name")
	List<Device> findAllWithBranch();

	@Query("select d from Device d join fetch d.branch where lower(d.serialNumber) = lower(:serialNumber)")
	Optional<Device> findBySerialNumberIgnoreCase(@Param("serialNumber") String serialNumber);

	@Query("select d from Device d join fetch d.branch where d.id = :id")
	Optional<Device> findWithBranch(@Param("id") Long id);

	boolean existsBySerialNumberIgnoreCase(String serialNumber);

	boolean existsBySerialNumberIgnoreCaseAndIdNot(String serialNumber, Long id);

}
