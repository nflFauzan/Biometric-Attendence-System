package com.biometricattendance.device;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<Device, Long> {

	@Query("select d from Device d join fetch d.branch order by d.name")
	List<Device> findAllWithBranch();

	@Query("select d from Device d join fetch d.branch where lower(d.serialNumber) = lower(:serialNumber)")
	Optional<Device> findBySerialNumberIgnoreCase(@Param("serialNumber") String serialNumber);

	@Query("select d from Device d join fetch d.branch where d.keyHash = :keyHash")
	Optional<Device> findByKeyHash(@Param("keyHash") String keyHash);

	/**
	 * Menandai kontak pertama kunci ini secara atomik. Mengembalikan 1 hanya untuk request pertama dengan kunci
	 * yang masih berlaku; request bersamaan atau dengan kunci lama mendapat 0.
	 */
	@Modifying
	@Query("update Device d set d.keyFirstUsedAt = :at where d.id = :id and d.keyHash = :keyHash and d.keyFirstUsedAt is null")
	int claimKeyFirstUse(@Param("id") Long id, @Param("keyHash") String keyHash, @Param("at") Instant at);

	/**
	 * Tujuan perintah hapus (spec §17.2): alat rakitan yang pernah tersambung, aktif maupun nonaktif. Yang belum pernah
	 * heartbeat dilewati karena sensornya pasti kosong.
	 */
	@Query("select d from Device d where d.protocol = 'RAKITAN_V1' and d.lastSeenAt is not null order by d.id")
	List<Device> findConnectedRakitan();

	boolean existsByProtocolAndActiveTrue(String protocol);

	@Query("select d from Device d join fetch d.branch where d.id = :id")
	Optional<Device> findWithBranch(@Param("id") Long id);

	boolean existsBySerialNumberIgnoreCase(String serialNumber);

	boolean existsBySerialNumberIgnoreCaseAndIdNot(String serialNumber, Long id);

}
