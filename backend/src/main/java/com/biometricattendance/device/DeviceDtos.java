package com.biometricattendance.device;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class DeviceDtos {

	private DeviceDtos() {
	}

	/**
	 * @param serialNumber wajib untuk ZK-Push; diabaikan untuk alat rakitan (ID dibuat server, K-099)
	 * @param acceptScansFrom scan yang lebih lama tidak disimpan (K-057); kosong saat membuat = sekarang,
	 * kosong saat mengubah = nilai lama tetap dipakai
	 * @param protocol jenis mesin; kosong = ZK_PUSH saat membuat, nilai lama saat mengubah
	 */
	public record DeviceRequest(
			@NotBlank(message = "Nama wajib diisi") @Size(max = 100) String name,
			@Size(max = 100) String serialNumber,
			@NotNull(message = "Cabang wajib dipilih") Long branchId,
			@NotNull(message = "Status aktif wajib diisi") Boolean active,
			Instant acceptScansFrom,
			@Pattern(regexp = "ZK_PUSH|RAKITAN_V1", message = "Jenis mesin tidak dikenal") String protocol) {
	}

	/**
	 * Kunci alat rakitan yang baru diterbitkan; hanya ada di response penerbitan (K-088).
	 *
	 * @param setupServerUrl alamat server bawaan untuk kode pengaturan ({@code app.device.public-url}), boleh null
	 */
	public record IssuedKey(String deviceKey, String setupServerUrl) {
	}

	public record DeviceResponse(Long id, String name, String serialNumber, Long branchId, String branchName,
			boolean active, String protocol, Instant acceptScansFrom, long ignoredScanCount, Instant lastSeenAt,
			String lastIp, String firmware, boolean online, boolean keyIssued, Instant keyFirstUsedAt,
			String lastInfo, Integer sensorUsed, Integer sensorCapacity, boolean sensorAlmostFull, IssuedKey issuedKey) {

		/** @param sensorWarningPercent batas peringatan sensor ({@code app.device.sensor-warning-percent}) */
		public static DeviceResponse from(Device device, boolean online, int sensorWarningPercent) {
			Integer used = device.getSensorUsed();
			Integer capacity = device.getSensorCapacity();
			boolean almostFull = used != null && capacity != null && used * 100L > capacity * (long) sensorWarningPercent;
			return new DeviceResponse(device.getId(), device.getName(), device.getSerialNumber(),
					device.getBranch().getId(), device.getBranch().getName(), device.isActive(), device.getProtocol(),
					device.getAcceptScansFrom(), device.getIgnoredScanCount(), device.getLastSeenAt(),
					device.getLastIp(), device.getFirmware(), online, device.getKeyHash() != null,
					device.getKeyFirstUsedAt(), device.getLastInfo(), used, capacity, almostFull, null);
		}

		public DeviceResponse withKey(IssuedKey key) {
			return new DeviceResponse(id, name, serialNumber, branchId, branchName, active, protocol, acceptScansFrom,
					ignoredScanCount, lastSeenAt, lastIp, firmware, online, keyIssued, keyFirstUsedAt, lastInfo, sensorUsed,
					sensorCapacity, sensorAlmostFull, key);
		}

	}

}
