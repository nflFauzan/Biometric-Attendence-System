package com.biometricattendance.calculation;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.calculation.DailyResult.DayType;
import com.biometricattendance.calculation.DailyResult.Flag;
import com.biometricattendance.calculation.DailyResult.TimeValue;
import com.biometricattendance.workrule.WorkRuleDtos.EffectiveRuleResponse;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CalculationDtos {

	private CalculationDtos() {
	}

	/** Simulasi rekap harian dari jam scan yang diketik pengguna (scan asli baru tersedia di Fase 3). */
	public record DailyPreviewRequest(@NotNull(message = "Karyawan wajib dipilih") Long employeeId,
			@NotNull(message = "Tanggal wajib diisi") LocalDate date,
			@Size(max = 20, message = "Maksimal 20 jam scan") List<@NotNull LocalTime> scanTimes,
			LocalTime correctionCheckIn, LocalTime correctionCheckOut) {
	}

	public record DailyPreviewResponse(EffectiveRuleResponse rule, String timezone, boolean holiday,
			DailyResultResponse result) {
	}

	/** Hasil rekap harian; durasi dalam detik (null = tidak dihitung). */
	public record DailyResultResponse(LocalDate date, DayType dayType, AttendanceStatus status,
			List<LocalTime> scanTimes, TimeValue checkIn, TimeValue checkOut, Long lateSeconds,
			Long earlyLeaveSeconds, Long regularWorkSeconds, Long overtimeCandidateSeconds,
			Long totalPresenceSeconds, Set<Flag> flags) {

		public static DailyResultResponse from(DailyResult result) {
			return new DailyResultResponse(result.date(), result.dayType(), result.status(), result.scanTimes(),
					result.checkIn(), result.checkOut(), seconds(result.late()), seconds(result.earlyLeave()),
					seconds(result.regularWork()), seconds(result.overtimeCandidate()),
					seconds(result.totalPresence()), result.flags());
		}

		private static Long seconds(Duration duration) {
			return duration == null ? null : duration.toSeconds();
		}

	}

}
