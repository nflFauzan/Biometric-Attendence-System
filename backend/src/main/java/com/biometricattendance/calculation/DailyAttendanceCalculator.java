package com.biometricattendance.calculation;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.biometricattendance.calculation.DailyInput.Correction;
import com.biometricattendance.calculation.DailyInput.LeaveState;
import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.calculation.DailyResult.DayType;
import com.biometricattendance.calculation.DailyResult.Flag;
import com.biometricattendance.calculation.DailyResult.TimeSource;
import com.biometricattendance.calculation.DailyResult.TimeValue;

/**
 * Mesin perhitungan rekap harian (kode murni). Semua angka jadwal berasal dari {@link EffectiveRule};
 * tidak ada jam kerja, toleransi, atau batas lembur yang ditanam di kode.
 *
 * <p>Rumus mengikuti docs/aturan-bisnis.md §4–5:
 * <ul>
 * <li>Jam masuk = scan pertama, jam pulang = scan terakhir, setelah scan berdekatan digabung menurut jeda
 * minimum (K-092); koreksi menggantikannya.</li>
 * <li>Keterlambatan = max(0, masuk − (jam masuk + toleransi)).</li>
 * <li>Jam kerja normal = irisan kehadiran dengan jadwal, dikurangi irisan dengan istirahat bila diatur.</li>
 * <li>Kandidat lembur = max(0, pulang − max(batas lembur, masuk)), tanpa syarat 8 jam (K-026).</li>
 * </ul>
 * Scan lintas tengah malam belum didukung: scan dikelompokkan per tanggal di zona waktu cabang.
 */
public final class DailyAttendanceCalculator {

	private DailyAttendanceCalculator() {
	}

	public static DailyResult calculate(DailyInput input) {
		EffectiveRule rule = input.rule();
		rule.requireForDailyCalculation();

		List<LocalTime> scanTimes = input.scans()
			.stream()
			.map(instant -> instant.atZone(input.zone()))
			.filter(time -> time.toLocalDate().equals(input.date()))
			.map(time -> time.toLocalTime())
			.sorted()
			.toList();

		DayType dayType = input.holiday() ? DayType.HARI_LIBUR
				: rule.workDays().contains(input.date().getDayOfWeek()) ? DayType.HARI_KERJA : DayType.BUKAN_HARI_KERJA;
		Set<Flag> flags = EnumSet.noneOf(Flag.class);
		boolean workday = dayType == DayType.HARI_KERJA;
		// K-076: izin hanya berpengaruh di hari kerja; pengajuan yang belum diputus tidak mengubah status.
		if (workday && input.leave() == LeaveState.PENDING) {
			flags.add(Flag.IZIN_MENUNGGU);
		}

		Correction correction = input.correction() == null ? new Correction(null, null) : input.correction();
		TimeValue in = correction.checkIn() == null ? null : new TimeValue(correction.checkIn(), TimeSource.KOREKSI);
		TimeValue out = correction.checkOut() == null ? null
				: new TimeValue(correction.checkOut(), TimeSource.KOREKSI);
		// K-092: scan berdekatan dihitung satu kejadian; scanTimes (scan asli) tetap utuh di hasil.
		List<LocalTime> events = groupStarts(scanTimes, rule.minScanGapMinutes());
		if (events.size() >= 2) {
			in = in != null ? in : new TimeValue(events.get(0), TimeSource.SCAN);
			out = out != null ? out : new TimeValue(events.get(events.size() - 1), TimeSource.SCAN);
		}
		else if (events.size() == 1) {
			// Satu scan tidak bisa dipastikan masuk atau pulang, kecuali sisi lainnya sudah dikoreksi (TC-07).
			TimeValue only = new TimeValue(events.get(0), TimeSource.SCAN);
			if (in == null && out != null) {
				in = only;
			}
			else if (out == null && in != null) {
				out = only;
			}
		}

		if (in == null && out == null && scanTimes.isEmpty()) {
			AttendanceStatus status = !workday ? AttendanceStatus.TIDAK_ADA_SCAN
					: input.leave() == LeaveState.APPROVED ? AttendanceStatus.IZIN : AttendanceStatus.TIDAK_HADIR;
			return incomplete(input, dayType, status, scanTimes, null, null, flags);
		}
		if (workday && input.leave() == LeaveState.APPROVED) {
			// Ada scan/koreksi padahal izin disetujui: rekap tetap dari scan, tetapi perlu ditinjau.
			flags.add(Flag.HADIR_SAAT_IZIN);
		}
		if (in == null || out == null) {
			return incomplete(input, dayType, AttendanceStatus.TIDAK_LENGKAP, scanTimes, in, out, flags);
		}
		if (!out.time().isAfter(in.time())) {
			flags.add(Flag.URUTAN_TIDAK_WAJAR);
			return incomplete(input, dayType, AttendanceStatus.TIDAK_LENGKAP, scanTimes, in, out, flags);
		}

		Duration totalPresence = Duration.between(in.time(), out.time());
		if (dayType != DayType.HARI_KERJA) {
			flags.add(Flag.PERLU_TINJAUAN_HARI_LIBUR);
			return new DailyResult(input.date(), dayType, AttendanceStatus.LENGKAP, scanTimes, in, out, null, null,
					null, null, totalPresence, flags);
		}

		LocalTime workStart = rule.workStart();
		LocalTime workEnd = rule.workEnd();
		LocalTime lateThreshold = workStart.plusMinutes(rule.lateToleranceMinutes());
		if (lateThreshold.isBefore(workStart)) {
			lateThreshold = LocalTime.MAX; // toleransi melewati tengah malam
		}
		Duration late = positive(lateThreshold, in.time());

		Duration regularWork = overlap(in.time(), out.time(), workStart, workEnd);
		if (rule.breakStart() == null) {
			flags.add(Flag.ISTIRAHAT_BELUM_DIATUR);
		}
		else {
			LocalTime regularStart = max(in.time(), workStart);
			LocalTime regularEnd = min(out.time(), workEnd);
			regularWork = regularWork.minus(overlap(regularStart, regularEnd, rule.breakStart(), rule.breakEnd()));
		}

		Duration earlyLeave = positive(out.time(), workEnd);
		Duration overtimeCandidate = positive(max(rule.overtimeStart(), in.time()), out.time());

		return new DailyResult(input.date(), dayType, AttendanceStatus.LENGKAP, scanTimes, in, out, late, earlyLeave,
				regularWork, overtimeCandidate, totalPresence, flags);
	}

	private static DailyResult incomplete(DailyInput input, DayType dayType, AttendanceStatus status,
			List<LocalTime> scanTimes, TimeValue in, TimeValue out, Set<Flag> flags) {
		return new DailyResult(input.date(), dayType, status, scanTimes, in, out, null, null, null, null, null, flags);
	}

	/**
	 * Awal setiap kelompok scan berdekatan (K-092). Kelompok baru dimulai bila jarak dari scan pertama kelompok
	 * berjalan mencapai jeda; jeda null berarti tanpa penggabungan.
	 */
	static List<LocalTime> groupStarts(List<LocalTime> sortedTimes, Integer gapMinutes) {
		if (gapMinutes == null || sortedTimes.isEmpty()) {
			return sortedTimes;
		}
		Duration gap = Duration.ofMinutes(gapMinutes);
		List<LocalTime> starts = new ArrayList<>();
		for (LocalTime time : sortedTimes) {
			if (starts.isEmpty() || Duration.between(starts.get(starts.size() - 1), time).compareTo(gap) >= 0) {
				starts.add(time);
			}
		}
		return starts;
	}

	/** Durasi dari start ke end, atau nol jika end tidak setelah start. */
	private static Duration positive(LocalTime start, LocalTime end) {
		return end.isAfter(start) ? Duration.between(start, end) : Duration.ZERO;
	}

	/** Panjang irisan rentang [aStart, aEnd] dan [bStart, bEnd]. */
	private static Duration overlap(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
		return positive(max(aStart, bStart), min(aEnd, bEnd));
	}

	private static LocalTime max(LocalTime a, LocalTime b) {
		return a.isAfter(b) ? a : b;
	}

	private static LocalTime min(LocalTime a, LocalTime b) {
		return a.isBefore(b) ? a : b;
	}

}
