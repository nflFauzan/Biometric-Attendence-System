package com.biometricattendance.fingerprint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.biometricattendance.devicecommand.DeviceCommandDtos.CommandResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body request/response API sidik jari karyawan. Template tidak pernah ada di sini (spec §1, §16). */
public final class FingerprintDtos {

	private FingerprintDtos() {
	}

	public record EnrollRequest(@NotNull(message = "Pilih mesin") Long deviceId,
			@NotNull(message = "Jari harus 1 atau 2") @Min(value = 1, message = "Jari harus 1 atau 2")
			@Max(value = 2, message = "Jari harus 1 atau 2") Integer finger) {
	}

	public record FingerResponse(int finger, Long deviceId, String deviceName, Instant enrolledAt) {
	}

	/**
	 * Penghapusan di satu mesin yang belum selesai (spec §17.8). {@code message} sudah memperhitungkan mesin offline atau
	 * nonaktif; {@code retryable} = boleh "Coba lagi".
	 */
	public record DeletionResponse(UUID commandId, String type, Integer finger, Long deviceId, String deviceName,
			String status, String message, boolean retryable) {
	}

	/** {@code zkPushDeviceActive}: ada mesin X401 aktif, jadi web mengingatkan untuk menghapus langsung di mesin. */
	public record FingerprintOverview(Instant consentAt, String consentBy, List<FingerResponse> fingers,
			List<CommandResponse> activeCommands, List<DeletionResponse> deletions, boolean zkPushDeviceActive) {
	}

}
