package com.biometricattendance.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Pemilihan aturan berlaku: versi per tanggal (K-033) dan prioritas per kolom Karyawan > Kategori > Global (K-046). */
class RuleResolverTests {

	private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
	private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
	private static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);

	private static RuleValues globalValues() {
		return RuleValues.builder()
			.workStart(LocalTime.of(8, 0))
			.workEnd(LocalTime.of(17, 0))
			.lateToleranceMinutes(0)
			.overtimeStart(LocalTime.of(17, 0))
			.workDays(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY))
			.build();
	}

	@Test
	void perColumnPriorityEmployeeOverCategoryOverGlobal() {
		List<RuleVersion> versions = List.of(
				new RuleVersion(1L, RuleLevel.GLOBAL, SEP_1, globalValues()),
				new RuleVersion(2L, RuleLevel.CATEGORY, SEP_1, RuleValues.builder().lateToleranceMinutes(15).build()),
				new RuleVersion(3L, RuleLevel.EMPLOYEE, SEP_1,
						RuleValues.builder().workStart(LocalTime.of(9, 0)).build()));

		EffectiveRule rule = RuleResolver.resolve(versions, OCT_1);

		assertThat(rule.workStart()).isEqualTo(LocalTime.of(9, 0));
		assertThat(rule.source(RuleField.WORK_START).level()).isEqualTo(RuleLevel.EMPLOYEE);
		assertThat(rule.lateToleranceMinutes()).isEqualTo(15);
		assertThat(rule.source(RuleField.LATE_TOLERANCE).level()).isEqualTo(RuleLevel.CATEGORY);
		assertThat(rule.workEnd()).isEqualTo(LocalTime.of(17, 0));
		assertThat(rule.source(RuleField.WORK_END).level()).isEqualTo(RuleLevel.GLOBAL);
		assertThat(rule.source(RuleField.WORK_END).versionId()).isEqualTo(1L);
	}

	@Test
	void ruleChangeMidPeriodDoesNotAffectEarlierDates() {
		List<RuleVersion> versions = List.of(
				new RuleVersion(1L, RuleLevel.GLOBAL, SEP_1, globalValues()),
				new RuleVersion(2L, RuleLevel.GLOBAL, OCT_15, RuleValues.builder()
					.workStart(LocalTime.of(9, 0))
					.workEnd(LocalTime.of(17, 0))
					.lateToleranceMinutes(0)
					.overtimeStart(LocalTime.of(17, 0))
					.workDays(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY))
					.build()));

		assertThat(RuleResolver.resolve(versions, OCT_15.minusDays(1)).workStart()).isEqualTo(LocalTime.of(8, 0));
		assertThat(RuleResolver.resolve(versions, OCT_15).workStart()).isEqualTo(LocalTime.of(9, 0));
	}

	/** AC-06 / TC-09 di level aturan: tarif Rp80.000 (September) berubah jadi Rp100.000 mulai Oktober. */
	@Test
	void historicalDailyWageIsUsedForEarlierPeriod() {
		List<RuleVersion> versions = List.of(
				new RuleVersion(1L, RuleLevel.EMPLOYEE, SEP_1,
						RuleValues.builder().dailyWage(new BigDecimal("80000.00")).build()),
				new RuleVersion(2L, RuleLevel.EMPLOYEE, OCT_1,
						RuleValues.builder().dailyWage(new BigDecimal("100000.00")).build()));

		assertThat(RuleResolver.resolve(versions, LocalDate.of(2026, 9, 30)).dailyWage())
			.isEqualByComparingTo("80000");
		assertThat(RuleResolver.resolve(versions, OCT_1).dailyWage()).isEqualByComparingTo("100000");
	}

	@Test
	void versionsAfterTheDateAreIgnored() {
		List<RuleVersion> versions = List.of(new RuleVersion(1L, RuleLevel.GLOBAL, OCT_15, globalValues()));

		EffectiveRule rule = RuleResolver.resolve(versions, OCT_1);

		assertThat(rule.workStart()).isNull();
		assertThat(rule.source(RuleField.WORK_START)).isNull();
	}

	/** Versi terbaru di satu level menggantikan versi lama level itu; kolom kosongnya mewarisi level di atas. */
	@Test
	void newerVersionAtSameLevelReplacesOlderVersionOfThatLevel() {
		List<RuleVersion> versions = List.of(
				new RuleVersion(1L, RuleLevel.GLOBAL, SEP_1, globalValues()),
				new RuleVersion(2L, RuleLevel.EMPLOYEE, SEP_1,
						RuleValues.builder().workStart(LocalTime.of(9, 0)).build()),
				new RuleVersion(3L, RuleLevel.EMPLOYEE, OCT_1, RuleValues.builder().lateToleranceMinutes(5).build()));

		EffectiveRule rule = RuleResolver.resolve(versions, OCT_1);

		assertThat(rule.workStart()).isEqualTo(LocalTime.of(8, 0));
		assertThat(rule.lateToleranceMinutes()).isEqualTo(5);
	}

	@Test
	void breakIsInheritedAsOnePair() {
		List<RuleVersion> versions = List.of(
				new RuleVersion(1L, RuleLevel.GLOBAL, SEP_1, RuleValues.builder()
					.breakStart(LocalTime.of(12, 0))
					.breakEnd(LocalTime.of(13, 0))
					.build()),
				new RuleVersion(2L, RuleLevel.EMPLOYEE, SEP_1, RuleValues.builder()
					.breakStart(LocalTime.of(11, 30))
					.breakEnd(LocalTime.of(12, 0))
					.build()));

		EffectiveRule rule = RuleResolver.resolve(versions, OCT_1);

		assertThat(rule.breakStart()).isEqualTo(LocalTime.of(11, 30));
		assertThat(rule.breakEnd()).isEqualTo(LocalTime.of(12, 0));
		assertThat(rule.source(RuleField.BREAK).level()).isEqualTo(RuleLevel.EMPLOYEE);
	}

	@Test
	void missingRequiredFieldsAreReportedTogether() {
		List<RuleVersion> versions = List.of(new RuleVersion(1L, RuleLevel.GLOBAL, SEP_1,
				RuleValues.builder().workStart(LocalTime.of(8, 0)).build()));

		EffectiveRule rule = RuleResolver.resolve(versions, OCT_1);

		assertThat(rule.missingForDailyCalculation()).containsExactly(RuleField.WORK_END, RuleField.LATE_TOLERANCE,
				RuleField.OVERTIME_START, RuleField.WORK_DAYS);
		assertThatThrownBy(rule::requireForDailyCalculation).isInstanceOf(RuleNotConfiguredException.class)
			.hasMessage("Aturan belum diatur: jam pulang, toleransi keterlambatan, batas mulai lembur, hari kerja");
	}

	@Test
	void breakIsOptionalForDailyCalculation() {
		EffectiveRule rule = RuleResolver.resolve(List.of(new RuleVersion(1L, RuleLevel.GLOBAL, SEP_1, globalValues())),
				OCT_1);

		assertThat(rule.missingForDailyCalculation()).isEmpty();
		assertThat(rule.breakStart()).isNull();
	}

}
