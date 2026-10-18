package com.biometricattendance.devicecommand;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, UUID> {

	@Query("select count(c) > 0 from DeviceCommand c where c.device.id = :deviceId and c.type = 'ENROLL'"
			+ " and c.status in ('MENUNGGU', 'DIKIRIM')")
	boolean existsActiveEnrollForDevice(@Param("deviceId") Long deviceId);

	@Query("select count(c) > 0 from DeviceCommand c where c.employee.id = :employeeId and c.finger = :finger"
			+ " and c.type = 'ENROLL' and c.status in ('MENUNGGU', 'DIKIRIM')")
	boolean existsActiveEnroll(@Param("employeeId") Long employeeId, @Param("finger") int finger);

	/** Perintah hapus untuk PIN ini di mesin ini yang belum BERHASIL; ENROLL baru harus menunggu (spec §17.6). */
	@Query("select count(c) > 0 from DeviceCommand c where c.device.id = :deviceId and c.devicePin = :pin"
			+ " and c.type in ('DELETE_TEMPLATE', 'DELETE_EMPLOYEE') and c.status in ('MENUNGGU', 'DIKIRIM', 'GAGAL')")
	boolean existsUnfinishedDeleteOnDevice(@Param("deviceId") Long deviceId, @Param("pin") String pin);

	/** PIN menunggu penghapusan (K-093, spec §17.5): perintah hapus di mesin aktif yang belum BERHASIL. */
	@Query("select count(c) > 0 from DeviceCommand c where c.devicePin = :pin and c.device.active = true"
			+ " and c.type in ('DELETE_TEMPLATE', 'DELETE_EMPLOYEE') and c.status in ('MENUNGGU', 'DIKIRIM', 'GAGAL')")
	boolean existsPendingDeleteForPin(@Param("pin") String pin);

	/** Daftar "Penghapusan di mesin" (spec §17.8): perintah hapus karyawan ini yang belum BERHASIL. */
	@Query("select c from DeviceCommand c join fetch c.device where c.employee.id = :employeeId"
			+ " and c.type in ('DELETE_TEMPLATE', 'DELETE_EMPLOYEE') and c.status in ('MENUNGGU', 'DIKIRIM', 'GAGAL')"
			+ " order by c.createdAt, c.device.id")
	List<DeviceCommand> findUnfinishedDeletesForEmployee(@Param("employeeId") Long employeeId);

	/** ENROLL aktif karyawan ini; perintah hapus punya daftarnya sendiri. */
	@Query("select c from DeviceCommand c join fetch c.device where c.employee.id = :employeeId"
			+ " and c.type = 'ENROLL' and c.status in ('MENUNGGU', 'DIKIRIM') order by c.createdAt")
	List<DeviceCommand> findActiveForEmployee(@Param("employeeId") Long employeeId);

	/**
	 * Perintah aktif mesin ini yang belum lewat batas waktu. Yang sudah lewat tidak dikirim dan dibiarkan untuk sweeper,
	 * supaya heartbeat tidak pernah menulis perintah mesin lain.
	 */
	@Query("select c from DeviceCommand c where c.device.id = :deviceId and ((c.type <> 'ENROLL'"
			+ " and c.status in ('MENUNGGU', 'DIKIRIM')) or (c.type = 'ENROLL' and ((c.status = 'MENUNGGU'"
			+ " and c.createdAt >= :pickupCutoff) or (c.status = 'DIKIRIM' and c.sentAt >= :resultCutoff))))"
			+ " order by c.createdAt")
	List<DeviceCommand> findDeliverable(@Param("deviceId") Long deviceId, @Param("pickupCutoff") Instant pickupCutoff,
			@Param("resultCutoff") Instant resultCutoff);

	/** Perintah yang dibatalkan setelah sempat dikirim; mesin perlu diberi tahu lewat daftar {@code cancel}. */
	@Query("select c from DeviceCommand c where c.device.id = :deviceId and c.status = 'DIBATALKAN'"
			+ " and c.sentAt is not null and c.finishedAt >= :since")
	List<DeviceCommand> findRecentlyCancelledSent(@Param("deviceId") Long deviceId, @Param("since") Instant since);

	// Kedaluwarsa bisa berjalan bersamaan (sweeper dan admin): baris yang sedang dikunci transaksi lain dilewati
	// (FOR UPDATE SKIP LOCKED), bukan ditunggu lalu gagal karena versinya sudah berubah.

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = SKIP_LOCKED_HINT, value = SKIP_LOCKED))
	@Query("select c from DeviceCommand c where c.type = 'ENROLL' and c.status = 'MENUNGGU' and c.createdAt < :cutoff")
	List<DeviceCommand> findUnpickedBefore(@Param("cutoff") Instant cutoff);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = SKIP_LOCKED_HINT, value = SKIP_LOCKED))
	@Query("select c from DeviceCommand c where c.type = 'ENROLL' and c.status = 'DIKIRIM' and c.sentAt < :cutoff")
	List<DeviceCommand> findSentBefore(@Param("cutoff") Instant cutoff);

	String SKIP_LOCKED_HINT = "jakarta.persistence.lock.timeout";
	/** Nilai lock timeout Hibernate untuk SKIP LOCKED. */
	String SKIP_LOCKED = "-2";

}
