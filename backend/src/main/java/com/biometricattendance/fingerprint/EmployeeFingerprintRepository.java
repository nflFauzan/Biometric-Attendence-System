package com.biometricattendance.fingerprint;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeFingerprintRepository extends JpaRepository<EmployeeFingerprint, Long> {

	List<EmployeeFingerprint> findByEmployeeIdOrderByFinger(Long employeeId);

	Optional<EmployeeFingerprint> findByEmployeeIdAndFinger(Long employeeId, int finger);

}
