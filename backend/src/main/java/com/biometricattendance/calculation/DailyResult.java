package com.biometricattendance.calculation;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Hasil perhitungan rekap harian. Durasi persis sampai detik tanpa pembulatan (K-052); durasi bernilai null
 * jika tidak dapat atau tidak boleh dihitung, misalnya scan tidak lengkap atau hari libur (K-053).
 *
 * @param scanTimes jam scan asli pada tanggal tersebut, terurut
 * @param late keterlambatan, hanya hari kerja
 * @param earlyLeave pulang sebelum jam pulang, hanya sebagai informasi
 * @param regularWork jam kerja normal di dalam jadwal, dikurangi istirahat bila diatur
 * @param overtimeCandidate kandidat lembur setelah batas mulai lembur (K-026), belum diverifikasi
 * @param totalPresence selisih jam pulang dan jam masuk
 */
public record DailyResult(LocalDate date, DayType dayType, AttendanceStatus status, List<LocalTime> scanTimes,
		TimeValue checkIn, TimeValue checkOut, Duration late, Duration earlyLeave, Duration regularWork,
		Duration overtimeCandidate, Duration totalPresence, Set<Flag> flags) {

	public DailyResult {
		scanTimes = List.copyOf(scanTimes);
		flags = flags.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(flags));
	}

	public enum DayType {
		HARI_KERJA, BUKAN_HARI_KERJA, HARI_LIBUR
	}

	public enum AttendanceStatus {
		/** Jam masuk dan pulang tersedia. */
		LENGKAP,
		/** Hanya satu scan, atau urutan jam tidak wajar; perlu ditinjau (TC-07). */
		TIDAK_LENGKAP,
		/** Tidak ada scan pada hari kerja. */
		TIDAK_HADIR,
		/** Tidak ada scan pada hari libur atau bukan hari kerja. */
		TIDAK_ADA_SCAN
	}

	public enum Flag {
		/** Istirahat belum diatur sehingga tidak ada pengurangan (K-048). */
		ISTIRAHAT_BELUM_DIATUR,
		/** Hadir di hari libur atau bukan hari kerja; perlakuan belum diketahui (K-053, Q-005). */
		PERLU_TINJAUAN_HARI_LIBUR,
		/** Jam pulang tidak setelah jam masuk. */
		URUTAN_TIDAK_WAJAR
	}

	public enum TimeSource {
		SCAN, KOREKSI
	}

	public record TimeValue(LocalTime time, TimeSource source) {
	}

}
