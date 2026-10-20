package com.biometricattendance.ingest.rakitan;

import java.util.List;
import java.util.Map;

import com.biometricattendance.devicesettings.DeviceSettingsDtos.SettingsPackage;
import com.biometricattendance.devicesettings.NetworkDtos.NetworkPayload;

/** Body request/response protokol alat rakitan {@code /device/v1} (docs/protokol-alat-rakitan.md §3–§5). */
public final class RakitanDtos {

	private RakitanDtos() {
	}

	/** Semua field opsional; field yang tidak dikenal diabaikan. */
	/** @param settingsVersion versi paket pengaturan ringan yang dipakai mesin; 0 = belum pernah menerima (§4) */
	public record HeartbeatRequest(String firmware, String ip, Long uptimeSeconds, Integer pendingScans,
			Sensor sensor, Integer settingsVersion) {
	}

	public record Sensor(Integer used, Integer capacity) {
	}

	/**
	 * @param serverTime waktu server di zona cabang, ISO-8601 dengan offset dan detik
	 * @param commands perintah aktif untuk mesin ini; dikirim ulang sampai mesin melaporkan hasil
	 * @param cancel ID perintah yang dibatalkan setelah sempat dikirim; mesin menghentikannya
	 * @param settings paket pengaturan ringan lengkap jika versi mesin berbeda dari server; null jika tidak (spec §18.2)
	 */
	public record HeartbeatResponse(String serverTime, String timezone, String deviceName, String branchName,
			List<CommandItem> commands, List<String> cancel, SettingsPackage settings) {
	}

	/**
	 * Isi perintah tanpa data rahasia (spec §4). {@code timeoutSeconds} adalah batas mesin menunggu jari (ENROLL saja);
	 * perintah hapus tidak membawa nama maupun batas waktu, dan {@code DELETE_EMPLOYEE} tanpa jari (spec §17).
	 * Pengecualian rahasia: {@code network} (APPLY_NETWORK) memuat password Wi-Fi dan {@code key} (ROTATE_KEY) memuat
	 * kunci baru; keduanya hanya dikirim ke mesin pemiliknya (spec §18.5–§18.6).
	 */
	public record CommandItem(String id, String type, String pin, Integer finger, String displayName,
			Long timeoutSeconds, NetworkPayload network, String key) {
	}

	/** Langkah tempelan jari 1–3. */
	public record ProgressRequest(Integer step) {
	}

	/**
	 * Hasil perintah. Sengaja tanpa field {@code template}: template di body diabaikan dan tidak pernah disimpan
	 * (spec §16).
	 *
	 * @param status {@code BERHASIL} atau {@code GAGAL}
	 * @param error kode error mesin, wajib jika GAGAL
	 * @param pin PIN pemilik sidik jari yang sudah ada, untuk {@code JARI_SUDAH_TERDAFTAR}
	 * @param detail keterangan kegagalan untuk perintah selain ENROLL, mis. SSID yang gagal (spec §18.5)
	 */
	public record ResultRequest(String status, String error, String pin, String detail) {
	}

	/** Setiap scan dibaca sebagai objek bebas supaya satu scan rusak tidak membuat seluruh kiriman ditolak. */
	public record ScanBatch(List<Map<String, Object>> scans) {
	}

	/**
	 * @param accepted semua ID scan yang sudah diproses; mesin menghapus scan dari microSD hanya jika ID-nya di sini
	 */
	public record ScansResponse(List<String> accepted, int saved, int duplicates, int ignored, int rejected) {
	}

	/** Balasan gagal untuk mesin; bukan ProblemDetail karena dibaca firmware. */
	public record ErrorBody(String error) {
	}

}
