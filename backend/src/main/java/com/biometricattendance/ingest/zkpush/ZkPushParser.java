package com.biometricattendance.ingest.zkpush;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.biometricattendance.ingest.RejectedLine;
import com.biometricattendance.ingest.ScanEvent;

/**
 * Membaca body tabel ATTLOG protokol push ZKTeco. Format diambil dari tangkapan asli mesin X401
 * (docs/panduan-mesin-x401.md §7): satu scan per baris, kolom dipisah tab:
 * {@code PIN, yyyy-MM-dd HH:mm:ss, status, verifikasi[, ...]}. Kolom tambahan diabaikan.
 */
public final class ZkPushParser {

	static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	static final int MAX_PIN = 20;
	static final int MAX_CODE = 10;

	private ZkPushParser() {
	}

	public record Batch(List<ScanEvent> events, List<RejectedLine> rejected) {
	}

	public static Batch parseAttlog(String body) {
		List<ScanEvent> events = new ArrayList<>();
		List<RejectedLine> rejected = new ArrayList<>();
		for (String line : body.split("\\r?\\n")) {
			if (line.isBlank()) {
				continue;
			}
			String[] cols = line.split("\t", -1);
			if (cols.length < 2) {
				rejected.add(new RejectedLine(line, "Kolom kurang: butuh PIN dan waktu"));
				continue;
			}
			String pin = cols[0].trim();
			if (pin.isEmpty()) {
				rejected.add(new RejectedLine(line, "PIN kosong"));
				continue;
			}
			if (pin.length() > MAX_PIN) {
				rejected.add(new RejectedLine(line, "PIN lebih dari " + MAX_PIN + " karakter"));
				continue;
			}
			LocalDateTime time;
			try {
				time = LocalDateTime.parse(cols[1].trim(), TIME);
			}
			catch (DateTimeParseException ex) {
				rejected.add(new RejectedLine(line, "Format waktu tidak dikenali"));
				continue;
			}
			events.add(new ScanEvent(pin, time, column(cols, 2), column(cols, 3), line));
		}
		return new Batch(events, rejected);
	}

	private static String column(String[] cols, int index) {
		if (cols.length <= index || cols[index].isBlank()) {
			return null;
		}
		String value = cols[index].trim();
		return value.length() > MAX_CODE ? value.substring(0, MAX_CODE) : value;
	}

}
