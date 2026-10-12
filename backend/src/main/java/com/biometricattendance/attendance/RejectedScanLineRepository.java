package com.biometricattendance.attendance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RejectedScanLineRepository extends JpaRepository<RejectedScanLine, Long> {

	List<RejectedScanLine> findByDeviceIdOrderByIdAsc(Long deviceId);

}
