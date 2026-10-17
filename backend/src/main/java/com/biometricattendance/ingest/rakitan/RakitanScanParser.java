package com.biometricattendance.ingest.rakitan;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.biometricattendance.ingest.RejectedLine;
import com.biometricattendance.ingest.ScanEvent;

import tools.jackson.databind.json.JsonMapper;

/**
 * Menerjemahkan scan alat rakitan ke format netral (docs/protokol-alat-rakitan.md §5). Scan yang rusak tidak
 * menahan scan lain: disimpan sebagai baris ditolak (K-062), dan ID-nya (jika ada) tetap dianggap diproses agar
 * antrean microSD mesin tidak tersangkut (K-099).
 */
@Component
public class RakitanScanParser {

	static final int MAX_PIN_LENGTH = 20;
	/** Panjang kolom attendance_scans.verify_mode (V4). */
	static final int MAX_VERIFY_LENGTH = 10;
	static final int MIN_YEAR = 2000;
	static final int MAX_YEAR = 2100;

	private final JsonMapper json;

	public RakitanScanParser(JsonMapper json) {
		this.json = json;
	}

	/**
	 * @param events scan valid, waktunya sudah diubah ke jam lokal cabang
	 * @param rejected scan rusak beserta alasannya
	 * @param accepted semua ID yang sudah diproses server (urut seperti kiriman)
	 */
	public record Parsed(List<ScanEvent> events, List<RejectedLine> rejected, List<String> accepted) {
	}

	public Parsed parse(List<Map<String, Object>> scans, ZoneId zone) {
		List<ScanEvent> events = new ArrayList<>();
		List<RejectedLine> rejected = new ArrayList<>();
		List<String> accepted = new ArrayList<>();
		for (Map<String, Object> scan : scans) {
			String rawLine = json.writeValueAsString(scan);
			String id = text(scan.get("id"));
			if (id == null) {
				rejected.add(new RejectedLine(rawLine, "ID scan kosong"));
				continue;
			}
			accepted.add(id);
			UUID uuid;
			try {
				uuid = UUID.fromString(id);
			}
			catch (IllegalArgumentException ex) {
				rejected.add(new RejectedLine(rawLine, "ID scan bukan UUID"));
				continue;
			}
			String pin = text(scan.get("pin"));
			if (pin == null || pin.length() > MAX_PIN_LENGTH) {
				rejected.add(new RejectedLine(rawLine, "PIN kosong atau lebih dari 20 karakter"));
				continue;
			}
			OffsetDateTime time;
			try {
				time = OffsetDateTime.parse(String.valueOf(scan.get("time")));
			}
			catch (DateTimeParseException ex) {
				rejected.add(new RejectedLine(rawLine, "Waktu harus ISO-8601 dengan offset"));
				continue;
			}
			// Batas teknis kolom database: nilai di luar ini membuat seluruh batch gagal disimpan dan antrean tersangkut.
			if (time.getYear() < MIN_YEAR || time.getYear() > MAX_YEAR) {
				rejected.add(new RejectedLine(rawLine, "Tahun waktu scan di luar 2000–2100"));
				continue;
			}
			String verify = text(scan.get("verify"));
			if (verify != null && verify.length() > MAX_VERIFY_LENGTH) {
				rejected.add(new RejectedLine(rawLine, "Cara verifikasi lebih dari 10 karakter"));
				continue;
			}
			events.add(new ScanEvent(pin, time.atZoneSameInstant(zone).toLocalDateTime(), null, verify, rawLine, uuid));
		}
		return new Parsed(events, rejected, accepted);
	}

	/** Teks yang tidak kosong; selain teks dianggap tidak ada. */
	private static String text(Object value) {
		return value instanceof String s && !s.isBlank() ? s.trim() : null;
	}

}
