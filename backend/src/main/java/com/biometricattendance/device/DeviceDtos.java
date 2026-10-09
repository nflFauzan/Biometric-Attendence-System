package com.biometricattendance.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class DeviceDtos {

	private DeviceDtos() {
	}

	public record DeviceRequest(
			@NotBlank(message = "Nama wajib diisi") @Size(max = 100) String name,
			@NotBlank(message = "Serial number wajib diisi") @Size(max = 100) String serialNumber,
			@NotNull(message = "Cabang wajib dipilih") Long branchId,
			@NotNull(message = "Status aktif wajib diisi") Boolean active) {
	}

	public record DeviceResponse(Long id, String name, String serialNumber, Long branchId, String branchName,
			boolean active) {

		public static DeviceResponse from(Device device) {
			return new DeviceResponse(device.getId(), device.getName(), device.getSerialNumber(),
					device.getBranch().getId(), device.getBranch().getName(), device.isActive());
		}

	}

}
