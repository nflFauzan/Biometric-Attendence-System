package com.biometricattendance.ingest;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Satu scan dalam format netral, hasil terjemahan adapter protokol (K-054).
 *
 * @param pin PIN/User ID karyawan di mesin
 * @param localTime waktu scan menurut jam mesin, tanpa zona waktu
 * @param statusCode kode status dari mesin (mis. masuk/pulang), apa adanya
 * @param verifyMode cara verifikasi dari mesin (mis. sidik jari), apa adanya
 * @param rawLine baris asli dari mesin untuk audit
 */
public record ScanEvent(String pin, LocalDateTime localTime, String statusCode, String verifyMode, String rawLine) {

	public ScanEvent {
		Objects.requireNonNull(pin, "pin");
		Objects.requireNonNull(localTime, "localTime");
		Objects.requireNonNull(rawLine, "rawLine");
	}

}
