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
	 * @param commands selalu kosong di versi ini
	 */
	public record HeartbeatResponse(String serverTime, String timezone, String deviceName, String branchName,
			List<Object> commands) {
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
