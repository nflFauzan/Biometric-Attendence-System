package com.biometricattendance.team;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.biometricattendance.attendance.AttendanceDtos.DayRecord;
import com.biometricattendance.attendance.AttendanceDtos.ScanResponse;
import com.biometricattendance.calculation.CalculationDtos.DailyResultResponse;
import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.calculation.DailyResult.TimeValue;
import com.biometricattendance.leave.LeaveStatus;

/**
 * Respons daftar rekan kerja (K-083). Sengaja berisi kolom yang diizinkan saja, sehingga data sensitif tidak bisa
 * ikut terkirim walaupun entity atau DTO pengelola bertambah kolom.
 */
public final class TeamDtos {

	private TeamDtos() {
	}

	/** Status sederhana untuk rekan kerja (K-084): izin tanpa jenis, tanpa flag peninjauan. */
	public enum TeamStatus {
		HADIR, TIDAK_LENGKAP, TIDAK_HADIR, IZIN, LIBUR, BELUM_DIATUR
	}

	/**
	 * Jam berupa {@link LocalTime} polos; {@link TimeValue#source()} tidak dikirim agar koreksi tidak terlihat. Jam dan
	 * durasi dibulatkan ke bawah ke menit penuh, karena koreksi selalu berdetik 0 sedangkan scan asli tidak (K-084).
	 */
	public record TeamDay(LocalDate date, TeamStatus status, LocalTime checkIn, LocalTime checkOut, Long lateSeconds,
			Long overtimeSeconds) {

		static TeamDay from(DayRecord day) {
			DailyResultResponse r = day.result();
			TeamStatus status = statusOf(day);
			if (status == TeamStatus.IZIN || r == null) {
				return new TeamDay(day.date(), status, null, null, null, null);
			}
			return new TeamDay(day.date(), status, time(r.checkIn()), time(r.checkOut()), wholeMinutes(r.lateSeconds()),
					wholeMinutes(r.overtimeCandidateSeconds()));
		}

		/** Urutannya sama dengan {@code dayStatus} di frontend pengelola: izin disetujui tanpa kehadiran diutamakan. */
		static TeamStatus statusOf(DayRecord day) {
			DailyResultResponse r = day.result();
			boolean approvedLeave = day.leave() != null && day.leave().status() == LeaveStatus.DISETUJUI;
			boolean noPresence = r == null && day.scans().stream().noneMatch(ScanResponse::counted) && day.correction() == null;
			if (approvedLeave && ((r != null && r.status() == AttendanceStatus.IZIN) || noPresence)) {
				return TeamStatus.IZIN;
			}
			if (day.ruleMissing() != null || r == null) {
				return TeamStatus.BELUM_DIATUR;
			}
			return switch (r.status()) {
				case LENGKAP -> TeamStatus.HADIR;
				case TIDAK_LENGKAP -> TeamStatus.TIDAK_LENGKAP;
				case TIDAK_HADIR -> TeamStatus.TIDAK_HADIR;
				case TIDAK_ADA_SCAN -> TeamStatus.LIBUR;
				case IZIN -> TeamStatus.IZIN;
			};
		}

		private static LocalTime time(TimeValue value) {
			return value == null ? null : value.time().truncatedTo(ChronoUnit.MINUTES);
		}

		private static Long wholeMinutes(Long seconds) {
			return seconds == null ? null : seconds - seconds % 60;
		}

	}

	public record BranchOption(Long id, String name) {
	}

	public record TeamRow(Long employeeId, String employeeNumber, String fullName, Long branchId, String branchName,
			String position, TeamDay day) {
	}

	public record TeamDailyResponse(LocalDate date, Long branchId, List<BranchOption> branches, List<TeamRow> rows) {
	}

	public record TeamEmployeeResponse(Long employeeId, String employeeNumber, String fullName, String branchName,
			String position, LocalDate from, LocalDate to, List<TeamDay> days) {
	}

}
