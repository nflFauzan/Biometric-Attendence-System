package com.biometricattendance.ingest;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Satu scan dalam format netral, hasil terjemahan adapter protokol (K-054).
 *
 * @param pin PIN/User ID karyawan di mesin
 * @param localTime waktu scan menurut jam mesin, tanpa zona waktu
 * @param statusCode kode status dari mesin (mis. masuk/pulang), apa adanya
 * @param verifyMode cara verifikasi dari mesin (mis. sidik jari), apa adanya
 * @param rawLine baris asli dari mesin untuk audit
 * @param deviceScanId ID scan buatan mesin (alat rakitan); null untuk protokol tanpa ID scan seperti ZK-Push
 */
public record ScanEvent(String pin, LocalDateTime localTime, String statusCode, String verifyMode, String rawLine,
		UUID deviceScanId) {

	public ScanEvent {
		Objects.requireNonNull(pin, "pin");
		Objects.requireNonNull(localTime, "localTime");
		Objects.requireNonNull(rawLine, "rawLine");
	}

	public ScanEvent(String pin, LocalDateTime localTime, String statusCode, String verifyMode, String rawLine) {
		this(pin, localTime, statusCode, verifyMode, rawLine, null);
	}

}
