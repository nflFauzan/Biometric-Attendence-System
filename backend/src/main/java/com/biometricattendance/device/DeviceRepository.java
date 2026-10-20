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

	@Query("select d from Device d join fetch d.branch where d.pendingKeyHash = :keyHash")
	Optional<Device> findByPendingKeyHash(@Param("keyHash") String keyHash);

	/**
	 * Kunci tertunda menjadi kunci utama secara atomik (spec §18.6). Mengembalikan 1 hanya untuk request pertama;
	 * request bersamaan mendapat 0 lalu menemukan mesinnya lewat {@link #findByKeyHash}. Persistence context
	 * dikosongkan supaya pencarian berikutnya membaca kunci yang baru.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Device d set d.keyHash = d.pendingKeyHash, d.pendingKeyHash = null, d.pendingKeyEnc = null,"
			+ " d.keyFirstUsedAt = coalesce(d.keyFirstUsedAt, :now) where d.id = :id and d.pendingKeyHash = :keyHash")
	int promotePendingKey(@Param("id") Long id, @Param("keyHash") String keyHash, @Param("now") Instant now);

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

	/** Tujuan "Pengaturan semua mesin" (spec §18.1). */
	List<Device> findByProtocolAndActiveTrueOrderById(String protocol);

	/** Versi paket pengaturan ringan per mesin untuk daftar Mesin; baris {@code [device_id, version]}. */
	@Query(value = "select device_id, version from device_settings", nativeQuery = true)
	List<Object[]> findSettingsVersions();

	@Query(value = "select version from device_settings where device_id = :id", nativeQuery = true)
	Optional<Integer> findSettingsVersion(@Param("id") Long id);

	/** Update terarah dari heartbeat agar tidak bentrok dengan edit mesin oleh Owner (seperti kontak terakhir). */
	@Modifying
	@Query("update Device d set d.settingsAppliedVersion = :version where d.id = :id and d.settingsAppliedVersion <> :version")
	int recordSettingsApplied(@Param("id") Long id, @Param("version") int version);

	@Query("select d from Device d join fetch d.branch where d.id = :id")
	Optional<Device> findWithBranch(@Param("id") Long id);

	boolean existsBySerialNumberIgnoreCase(String serialNumber);

	boolean existsBySerialNumberIgnoreCaseAndIdNot(String serialNumber, Long id);

}
