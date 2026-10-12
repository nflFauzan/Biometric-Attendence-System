package com.biometricattendance.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.calculation.DailyResult.DayType;
import com.biometricattendance.calculation.DailyResult.Flag;
import com.biometricattendance.calculation.DailyResult.TimeSource;

/**
 * Test case TC-01 sampai TC-08 dari docs/aturan-bisnis.md §13, ditambah keputusan K-048 (istirahat opsional),
 * K-052 (durasi tanpa pembulatan), dan K-053 (hari libur perlu tinjauan).
 */
class DailyAttendanceCalculatorTests {

	private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
	private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
	private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 18);

	/** Nilai "acuan awal" aturan-bisnis §3.1 hanya sebagai data uji, bukan konstanta di kode produksi. */
	private static EffectiveRule rule(LocalTime breakStart, LocalTime breakEnd) {
		RuleValues values = RuleValues.builder()
			.workStart(LocalTime.of(8, 0))
			.workEnd(LocalTime.of(17, 0))
			.breakStart(breakStart)
			.breakEnd(breakEnd)
			.lateToleranceMinutes(0)
			.overtimeStart(LocalTime.of(17, 0))
			.workDays(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY))
			.build();
		return RuleResolver.resolve(List.of(new RuleVersion(1L, RuleLevel.GLOBAL, MONDAY, values)), MONDAY);
	}

	private static EffectiveRule ruleWithoutBreak() {
		return rule(null, null);
	}

	private static List<Instant> scans(LocalDate date, String... times) {
		List<Instant> result = new ArrayList<>();
		for (String time : times) {
			result.add(date.atTime(LocalTime.parse(time)).atZone(JAKARTA).toInstant());
		}
		return result;
	}

	private static DailyResult calculate(EffectiveRule rule, String... times) {
		return DailyAttendanceCalculator.calculate(
				new DailyInput(MONDAY, JAKARTA, scans(MONDAY, times), null, rule, false));
	}

	@Test
	void tc01ScanBeforeWorkStartDoesNotAddRegularWork() {
		DailyResult result = calculate(ruleWithoutBreak(), "07:30", "17:00");

		assertThat(result.checkIn().time()).isEqualTo(LocalTime.of(7, 30));
		assertThat(result.regularWork()).isEqualTo(Duration.ofHours(9));
		assertThat(result.late()).isZero();
	}

	@Test
	void tc02ScanExactlyAtWorkStartIsNotLate() {
		assertThat(calculate(ruleWithoutBreak(), "08:00", "17:00").late()).isZero();
	}

	@Test
	void tc03ScanTenMinutesAfterWorkStartIsTenMinutesLate() {
		assertThat(calculate(ruleWithoutBreak(), "08:10", "17:00").late()).isEqualTo(Duration.ofMinutes(10));
	}

	@Test
	void lateToleranceIsSubtracted() {
		RuleValues values = RuleValues.builder()
			.workStart(LocalTime.of(8, 0))
			.workEnd(LocalTime.of(17, 0))
			.lateToleranceMinutes(5)
			.overtimeStart(LocalTime.of(17, 0))
			.workDays(EnumSet.allOf(DayOfWeek.class))
			.build();
		EffectiveRule rule = RuleResolver.resolve(List.of(new RuleVersion(1L, RuleLevel.GLOBAL, MONDAY, values)),
				MONDAY);

		assertThat(calculate(rule, "08:05", "17:00").late()).isZero();
		assertThat(calculate(rule, "08:12", "17:00").late()).isEqualTo(Duration.ofMinutes(7));
	}

	@Test
	void tc04LeavingAtWorkEndHasNoOvertimeCandidate() {
		DailyResult result = calculate(ruleWithoutBreak(), "08:00", "17:00");

		assertThat(result.overtimeCandidate()).isZero();
		assertThat(result.earlyLeave()).isZero();
	}

	@Test
	void tc05LeavingAt18HasSixtyMinutesOvertimeCandidate() {
		assertThat(calculate(ruleWithoutBreak(), "08:00", "18:00").overtimeCandidate())
			.isEqualTo(Duration.ofMinutes(60));
	}

	/** TC-06 / AC-05: masuk 15.00 pulang 18.00 = 2 jam normal dan 1 jam kandidat lembur (K-026). */
	@Test
	void tc06WorkBefore17IsSeparatedFromOvertime() {
		DailyResult result = calculate(ruleWithoutBreak(), "15:00", "18:00");

		assertThat(result.regularWork()).isEqualTo(Duration.ofMinutes(120));
		assertThat(result.overtimeCandidate()).isEqualTo(Duration.ofMinutes(60));
		assertThat(result.late()).isEqualTo(Duration.ofHours(7));
	}

	@Test
	void tc07SingleScanIsIncompleteAndNothingIsInvented() {
		DailyResult result = calculate(ruleWithoutBreak(), "08:00");

		assertThat(result.status()).isEqualTo(AttendanceStatus.TIDAK_LENGKAP);
		assertThat(result.checkIn()).isNull();
		assertThat(result.checkOut()).isNull();
		assertThat(result.regularWork()).isNull();
		assertThat(result.overtimeCandidate()).isNull();
		assertThat(result.late()).isNull();
		assertThat(result.scanTimes()).containsExactly(LocalTime.of(8, 0));
	}

	/** TC-08 bagian mesin perhitungan: jam koreksi dipakai, scan asli tidak berubah, sumber ditandai. */
	@Test
	void tc08CorrectionReplacesScanTimeWithoutTouchingRawScans() {
		List<Instant> raw = scans(MONDAY, "08:00");
		List<Instant> rawCopy = List.copyOf(raw);

		DailyResult result = DailyAttendanceCalculator.calculate(new DailyInput(MONDAY, JAKARTA, raw,
				new DailyInput.Correction(null, LocalTime.of(18, 0)), ruleWithoutBreak(), false));

		assertThat(raw).isEqualTo(rawCopy);
		assertThat(result.status()).isEqualTo(AttendanceStatus.LENGKAP);
		assertThat(result.checkIn().time()).isEqualTo(LocalTime.of(8, 0));
		assertThat(result.checkIn().source()).isEqualTo(TimeSource.SCAN);
		assertThat(result.checkOut().time()).isEqualTo(LocalTime.of(18, 0));
		assertThat(result.checkOut().source()).isEqualTo(TimeSource.KOREKSI);
		assertThat(result.overtimeCandidate()).isEqualTo(Duration.ofMinutes(60));
		assertThat(result.scanTimes()).containsExactly(LocalTime.of(8, 0));
	}

	@Test
	void firstAndLastScanAreUsedRegardlessOfInputOrder() {
		DailyResult result = calculate(ruleWithoutBreak(), "17:30", "08:00", "12:00");

		assertThat(result.checkIn().time()).isEqualTo(LocalTime.of(8, 0));
		assertThat(result.checkOut().time()).isEqualTo(LocalTime.of(17, 30));
		assertThat(result.overtimeCandidate()).isEqualTo(Duration.ofMinutes(30));
	}

	@Test
	void earlyLeaveIsInformational() {
		DailyResult result = calculate(ruleWithoutBreak(), "08:00", "16:15");

		assertThat(result.earlyLeave()).isEqualTo(Duration.ofMinutes(45));
		assertThat(result.regularWork()).isEqualTo(Duration.ofMinutes(495));
	}

	@Test
	void configuredBreakIsSubtractedFromRegularWork() {
		DailyResult result = calculate(rule(LocalTime.of(12, 0), LocalTime.of(13, 0)), "08:00", "17:00");

		assertThat(result.regularWork()).isEqualTo(Duration.ofHours(8));
		assertThat(result.flags()).doesNotContain(Flag.ISTIRAHAT_BELUM_DIATUR);
	}

	@Test
	void breakOutsidePresenceIsNotSubtracted() {
		DailyResult result = calculate(rule(LocalTime.of(12, 0), LocalTime.of(13, 0)), "13:30", "17:00");

		assertThat(result.regularWork()).isEqualTo(Duration.ofMinutes(210));
	}

	@Test
	void missingBreakIsFlagged() {
		DailyResult result = calculate(ruleWithoutBreak(), "08:00", "17:00");

		assertThat(result.flags()).contains(Flag.ISTIRAHAT_BELUM_DIATUR);
		assertThat(result.regularWork()).isEqualTo(Duration.ofHours(9));
	}

	/** K-052: durasi persis sampai detik, tanpa pembulatan. */
	@Test
	void durationsAreExactWithoutRounding() {
		List<Instant> raw = List.of(MONDAY.atTime(8, 0, 0).atZone(JAKARTA).toInstant(),
				MONDAY.atTime(17, 0, 45).atZone(JAKARTA).toInstant());

		DailyResult result = DailyAttendanceCalculator
			.calculate(new DailyInput(MONDAY, JAKARTA, raw, null, ruleWithoutBreak(), false));

		assertThat(result.overtimeCandidate()).isEqualTo(Duration.ofSeconds(45));
	}

	@Test
	void noScanIsAbsentOnWorkingDay() {
		DailyResult result = calculate(ruleWithoutBreak());

		assertThat(result.status()).isEqualTo(AttendanceStatus.TIDAK_HADIR);
		assertThat(result.dayType()).isEqualTo(DayType.HARI_KERJA);
		assertThat(result.flags()).isEmpty();
	}

	/** K-053: kerja di hari libur hanya dihitung total hadirnya dan ditandai perlu tinjauan. */
	@Test
	void workOnHolidayIsOnlyTotalledAndFlagged() {
		DailyResult result = DailyAttendanceCalculator.calculate(
				new DailyInput(MONDAY, JAKARTA, scans(MONDAY, "08:00", "18:00"), null, ruleWithoutBreak(), true));

		assertThat(result.dayType()).isEqualTo(DayType.HARI_LIBUR);
		assertThat(result.totalPresence()).isEqualTo(Duration.ofHours(10));
		assertThat(result.regularWork()).isNull();
		assertThat(result.overtimeCandidate()).isNull();
		assertThat(result.late()).isNull();
		assertThat(result.flags()).containsExactly(Flag.PERLU_TINJAUAN_HARI_LIBUR);
	}

	@Test
	void workOnNonWorkingDayIsFlagged() {
		DailyResult result = DailyAttendanceCalculator.calculate(
				new DailyInput(SUNDAY, JAKARTA, scans(SUNDAY, "08:00", "12:00"), null, ruleWithoutBreak(), false));

		assertThat(result.dayType()).isEqualTo(DayType.BUKAN_HARI_KERJA);
		assertThat(result.totalPresence()).isEqualTo(Duration.ofHours(4));
		assertThat(result.overtimeCandidate()).isNull();
		assertThat(result.flags()).containsExactly(Flag.PERLU_TINJAUAN_HARI_LIBUR);
	}

	@Test
	void noScanOnHolidayIsNotAbsence() {
		DailyResult result = DailyAttendanceCalculator
			.calculate(new DailyInput(SUNDAY, JAKARTA, List.of(), null, ruleWithoutBreak(), false));

		assertThat(result.status()).isEqualTo(AttendanceStatus.TIDAK_ADA_SCAN);
		assertThat(result.flags()).isEmpty();
	}

	@Test
	void correctionWithCheckOutBeforeCheckInNeedsReview() {
		DailyResult result = DailyAttendanceCalculator.calculate(new DailyInput(MONDAY, JAKARTA,
				scans(MONDAY, "09:00", "17:00"), new DailyInput.Correction(null, LocalTime.of(8, 30)),
				ruleWithoutBreak(), false));

		assertThat(result.status()).isEqualTo(AttendanceStatus.TIDAK_LENGKAP);
		assertThat(result.flags()).contains(Flag.URUTAN_TIDAK_WAJAR);
		assertThat(result.regularWork()).isNull();
	}

	@Test
	void scansOfOtherDatesAreIgnored() {
		List<Instant> raw = new ArrayList<>(scans(MONDAY, "08:00", "17:00"));
		raw.add(MONDAY.minusDays(1).atTime(23, 0).atZone(JAKARTA).toInstant());

		DailyResult result = DailyAttendanceCalculator
			.calculate(new DailyInput(MONDAY, JAKARTA, raw, null, ruleWithoutBreak(), false));

		assertThat(result.scanTimes()).containsExactly(LocalTime.of(8, 0), LocalTime.of(17, 0));
	}

	@Test
	void incompleteRuleIsRejected() {
		EffectiveRule empty = RuleResolver.resolve(List.of(), MONDAY);

		assertThatThrownBy(() -> calculate(empty, "08:00", "17:00")).isInstanceOf(RuleNotConfiguredException.class);
	}

}
