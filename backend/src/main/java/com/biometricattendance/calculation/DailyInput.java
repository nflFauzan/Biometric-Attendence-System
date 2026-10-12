package com.biometricattendance.calculation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * Input perhitungan rekap satu karyawan pada satu tanggal.
 *
 * @param date tanggal rekap dalam zona waktu cabang
 * @param zone zona waktu cabang tempat karyawan bekerja
 * @param scans scan asli (raw); disalin dan tidak pernah diubah
 * @param correction koreksi jam masuk/pulang yang sah, atau null
 * @param rule aturan yang berlaku pada tanggal tersebut
 * @param holiday true jika tanggal tercatat di kalender libur
 */
public record DailyInput(LocalDate date, ZoneId zone, List<Instant> scans, Correction correction, EffectiveRule rule,
		boolean holiday) {

	public DailyInput {
		Objects.requireNonNull(date, "date");
		Objects.requireNonNull(zone, "zone");
		Objects.requireNonNull(rule, "rule");
		scans = scans == null ? List.of() : List.copyOf(scans);
	}

	/** Koreksi jam masuk dan/atau pulang. Nilai null berarti jam dari scan tetap dipakai. */
	public record Correction(LocalTime checkIn, LocalTime checkOut) {
	}

}
