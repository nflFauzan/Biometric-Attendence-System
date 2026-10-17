package com.biometricattendance.attendance;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceScanRepository extends JpaRepository<AttendanceScan, Long> {

	/**
	 * Menyimpan satu scan; scan yang sama diabaikan database, termasuk bila dua request datang bersamaan (K-061).
	 * "Sama" berarti mesin + PIN + waktu, atau mesin + ID scan buatan mesin (alat rakitan, K-099). Tanpa target
	 * conflict, kedua aturan unik berlaku. Mengembalikan 1 jika tersimpan, 0 jika duplikat.
	 */
	@Modifying
	@Query(value = """
			insert into attendance_scans (device_id, device_pin, employee_id, scanned_at, device_local_time,
			    status_code, verify_mode, flags, raw_line, device_scan_id)
			values (:deviceId, :pin, cast(:employeeId as bigint), :scannedAt, :localTime,
			    cast(:statusCode as varchar), cast(:verifyMode as varchar), cast(:flags as varchar), :rawLine,
			    cast(cast(:deviceScanId as varchar) as uuid))
			on conflict do nothing
			""", nativeQuery = true)
	int insertIgnoringDuplicate(@Param("deviceId") long deviceId, @Param("pin") String pin,
			@Param("employeeId") Long employeeId, @Param("scannedAt") Instant scannedAt,
			@Param("localTime") LocalDateTime localTime, @Param("statusCode") String statusCode,
			@Param("verifyMode") String verifyMode, @Param("flags") String flags, @Param("rawLine") String rawLine,
			@Param("deviceScanId") String deviceScanId);

	@Query("""
			select s from AttendanceScan s join fetch s.device
			where s.employeeId in :employeeIds and s.scannedAt >= :from and s.scannedAt < :to
			order by s.scannedAt
			""")
	List<AttendanceScan> findForEmployees(@Param("employeeIds") Collection<Long> employeeIds,
			@Param("from") Instant from, @Param("to") Instant to);

	@Query("""
			select s from AttendanceScan s join fetch s.device
			where s.employeeId is null and s.scannedAt >= :from and s.scannedAt < :to
			order by s.scannedAt desc
			""")
	List<AttendanceScan> findUnmatched(@Param("from") Instant from, @Param("to") Instant to, Pageable page);

	@Query("select s from AttendanceScan s join fetch s.device where s.scannedAt >= :from order by s.scannedAt desc")
	List<AttendanceScan> findRecent(@Param("from") Instant from, Pageable page);

	@Query("select count(s) from AttendanceScan s where s.employeeId is null and s.scannedAt >= :from and s.scannedAt < :to")
	long countUnmatched(@Param("from") Instant from, @Param("to") Instant to);

	/** K-068: menghubungkan scan PIN_TIDAK_DIKENAL ke karyawan yang baru diberi PIN tersebut. */
	@Modifying
	@Query(value = """
			update attendance_scans
			set employee_id = :employeeId,
			    flags = nullif(array_to_string(array_remove(string_to_array(flags, ','), 'PIN_TIDAK_DIKENAL'), ','), '')
			where employee_id is null and device_pin = :pin
			""", nativeQuery = true)
	int linkUnmatched(@Param("pin") String pin, @Param("employeeId") long employeeId);

	long countByDeviceId(Long deviceId);

}
