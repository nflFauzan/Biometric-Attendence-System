package com.biometricattendance.device;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class DeviceDtos {

	private DeviceDtos() {
	}

	/**
	 * @param acceptScansFrom scan yang lebih lama tidak disimpan (K-057); kosong saat membuat = sekarang,
	 * kosong saat mengubah = nilai lama tetap dipakai
	 */
	public record DeviceRequest(
			@NotBlank(message = "Nama wajib diisi") @Size(max = 100) String name,
			@NotBlank(message = "Serial number wajib diisi") @Size(max = 100) String serialNumber,
			@NotNull(message = "Cabang wajib dipilih") Long branchId,
			@NotNull(message = "Status aktif wajib diisi") Boolean active,
			Instant acceptScansFrom) {
	}

	public record DeviceResponse(Long id, String name, String serialNumber, Long branchId, String branchName,
			boolean active, String protocol, Instant acceptScansFrom, long ignoredScanCount, Instant lastSeenAt,
			String lastIp, String firmware, boolean online) {

		public static DeviceResponse from(Device device, boolean online) {
			return new DeviceResponse(device.getId(), device.getName(), device.getSerialNumber(),
					device.getBranch().getId(), device.getBranch().getName(), device.isActive(), device.getProtocol(),
					device.getAcceptScansFrom(), device.getIgnoredScanCount(), device.getLastSeenAt(),
					device.getLastIp(), device.getFirmware(), online);
		}

	}

}
