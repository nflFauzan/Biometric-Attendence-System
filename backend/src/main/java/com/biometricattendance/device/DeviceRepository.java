package com.biometricattendance.device;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeviceRepository extends JpaRepository<Device, Long> {

	@Query("select d from Device d join fetch d.branch order by d.name")
	List<Device> findAllWithBranch();

	boolean existsBySerialNumberIgnoreCase(String serialNumber);

	boolean existsBySerialNumberIgnoreCaseAndIdNot(String serialNumber, Long id);

}
