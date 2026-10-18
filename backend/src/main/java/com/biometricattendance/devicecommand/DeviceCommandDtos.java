package com.biometricattendance.devicecommand;

import java.time.Instant;
import java.util.UUID;

/** Body response API web perintah mesin. */
public final class DeviceCommandDtos {

	private DeviceCommandDtos() {
	}

	/** {@code message} adalah teks status siap tampil (spec §9). */
	public record CommandResponse(UUID id, String type, String status, Long deviceId, String deviceName,
			Long employeeId, Integer finger, Integer step, String errorCode, String message, Instant createdAt,
			Instant finishedAt) {

		public static CommandResponse from(DeviceCommand c) {
			return new CommandResponse(c.getId(), c.getType(), c.getStatus(), c.getDevice().getId(),
					c.getDevice().getName(), c.getEmployee() == null ? null : c.getEmployee().getId(), c.getFinger(),
					c.getStep(), c.getErrorCode(), DeviceCommandMessages.message(c), c.getCreatedAt(),
					c.getFinishedAt());
		}
	}

}
