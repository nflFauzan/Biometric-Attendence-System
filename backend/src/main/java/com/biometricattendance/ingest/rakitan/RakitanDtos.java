package com.biometricattendance.ingest.rakitan;

import java.util.List;
import java.util.Map;

/** Body request/response protokol alat rakitan {@code /device/v1} (docs/protokol-alat-rakitan.md §3–§5). */
public final class RakitanDtos {

	private RakitanDtos() {
	}

	/** Semua field opsional; field yang tidak dikenal diabaikan. */
	public record HeartbeatRequest(String firmware, String ip, Long uptimeSeconds, Integer pendingScans,
			Sensor sensor) {
	}

	public record Sensor(Integer used, Integer capacity) {
	}

	/**
	 * @param serverTime waktu server di zona cabang, ISO-8601 dengan offset dan detik
	 * @param commands perintah aktif untuk mesin ini; dikirim ulang sampai mesin melaporkan hasil
	 * @param cancel ID perintah yang dibatalkan setelah sempat dikirim; mesin menghentikannya
	 */
	public record HeartbeatResponse(String serverTime, String timezone, String deviceName, String branchName,
			List<CommandItem> commands, List<String> cancel) {
	}

	/** Isi perintah tanpa data rahasia (spec §4). {@code timeoutSeconds} adalah batas mesin menunggu jari. */
	public record CommandItem(String id, String type, String pin, int finger, String displayName,
			long timeoutSeconds) {
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
	 */
	public record ResultRequest(String status, String error, String pin) {
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
