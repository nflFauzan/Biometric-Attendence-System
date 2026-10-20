package com.biometricattendance.devicesettings;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTO pengaturan ringan alat rakitan (spec §7.1, §18.1). Batas nilai adalah parameter teknis, bukan aturan bisnis. */
public final class DeviceSettingsDtos {

	private DeviceSettingsDtos() {
	}

	/**
	 * @param headerText judul layar; kosong = nama cabang
	 * @param nameDisplay nama yang tampil setelah scan: nama lengkap, nama depan, atau PIN
	 * @param buzzerVolume 0 = buzzer mati
	 * @param duplicateNoticeSeconds jeda tampilan "Sudah tercatat"; 0 = mati (bukan aturan rekap, K-092)
	 * @param heartbeatSeconds paling lama 20 detik karena ENROLL kedaluwarsa jika tidak diambil dalam 30 detik
	 */
	public record SettingsValues(
			@Size(max = 32, message = "Judul layar maksimal 32 karakter") String headerText,
			@NotNull(message = "Pesan sapaan wajib diisi") @Size(max = 64, message = "Pesan sapaan maksimal 64 karakter") String greeting,
			@NotNull(message = "Tampilan nama wajib dipilih") @Pattern(regexp = "FULL|FIRST|PIN", message = "Tampilan nama tidak dikenal") String nameDisplay,
			@NotNull(message = "Kecerahan wajib diisi") @Min(value = 10, message = "Kecerahan 10–100%") @Max(value = 100, message = "Kecerahan 10–100%") Integer brightness,
			@NotNull(message = "Volume buzzer wajib diisi") @Min(value = 0, message = "Volume buzzer 0–100%") @Max(value = 100, message = "Volume buzzer 0–100%") Integer buzzerVolume,
			@NotNull(message = "Jeda \"Sudah tercatat\" wajib diisi") @Min(value = 0, message = "Jeda \"Sudah tercatat\" 0–600 detik") @Max(value = 600, message = "Jeda \"Sudah tercatat\" 0–600 detik") Integer duplicateNoticeSeconds,
			@NotNull(message = "Interval heartbeat wajib diisi") @Min(value = 2, message = "Interval heartbeat 2–20 detik") @Max(value = 20, message = "Interval heartbeat 2–20 detik") Integer heartbeatSeconds) {

		SettingsValues normalized() {
			return new SettingsValues(headerText == null ? "" : headerText.trim(), greeting.trim(), nameDisplay,
					brightness, buzzerVolume, duplicateNoticeSeconds, heartbeatSeconds);
		}

	}

	/**
	 * @param version versi paket di server; 0 = belum pernah disimpan, mesin memakai bawaan firmware
	 * @param appliedVersion versi yang terakhir dilaporkan mesin lewat heartbeat
	 */
	public record SettingsResponse(Long deviceId, int version, int appliedVersion, SettingsValues values,
			Instant updatedAt, String updatedBy) {
	}

	/** Isi {@code settings} di balasan heartbeat (docs/protokol-alat-rakitan.md §4). */
	public record SettingsPackage(int version, String headerText, String greeting, String nameDisplay, int brightness,
			int buzzerVolume, int duplicateNoticeSeconds, int heartbeatSeconds) {
	}

}
