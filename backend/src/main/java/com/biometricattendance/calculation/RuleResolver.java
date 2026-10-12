package com.biometricattendance.calculation;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Menentukan aturan yang berlaku pada sebuah tanggal (kode murni, tanpa database).
 *
 * <ol>
 * <li>Per level, versi yang dipakai adalah versi dengan tanggal berlaku terakhir yang tidak melewati tanggal
 * tersebut. Versi lama di level yang sama tidak ikut diwarisi.</li>
 * <li>Per kolom, nilai diambil dari level paling spesifik yang mengisinya: Karyawan, lalu Kategori, lalu
 * Global (K-046).</li>
 * </ol>
 */
public final class RuleResolver {

	private static final List<RuleLevel> PRIORITY = List.of(RuleLevel.EMPLOYEE, RuleLevel.CATEGORY,
			RuleLevel.GLOBAL);

	private RuleResolver() {
	}

	public static EffectiveRule resolve(List<RuleVersion> versions, LocalDate date) {
		Map<RuleLevel, RuleVersion> active = new EnumMap<>(RuleLevel.class);
		for (RuleLevel level : RuleLevel.values()) {
			versions.stream()
				.filter(v -> v.level() == level && !v.effectiveFrom().isAfter(date))
				.max(Comparator.comparing(RuleVersion::effectiveFrom))
				.ifPresent(v -> active.put(level, v));
		}

		RuleValues.Builder builder = RuleValues.builder();
		Map<RuleField, EffectiveRule.Source> sources = new EnumMap<>(RuleField.class);
		for (RuleField field : RuleField.values()) {
			for (RuleLevel level : PRIORITY) {
				RuleVersion version = active.get(level);
				if (version != null && version.values().defines(field)) {
					copy(field, version.values(), builder);
					sources.put(field, new EffectiveRule.Source(level, version.id(), version.effectiveFrom()));
					break;
				}
			}
		}
		return new EffectiveRule(builder.build(), sources);
	}

	private static void copy(RuleField field, RuleValues from, RuleValues.Builder to) {
		switch (field) {
			case WORK_START -> to.workStart(from.workStart());
			case WORK_END -> to.workEnd(from.workEnd());
			case BREAK -> to.breakStart(from.breakStart()).breakEnd(from.breakEnd());
			case LATE_TOLERANCE -> to.lateToleranceMinutes(from.lateToleranceMinutes());
			case OVERTIME_START -> to.overtimeStart(from.overtimeStart());
			case WORK_DAYS -> to.workDays(from.workDays());
			case DAILY_WAGE -> to.dailyWage(from.dailyWage());
			case OVERTIME_RATE -> to.overtimeRatePerHour(from.overtimeRatePerHour());
			case BASE_SALARY -> to.baseSalary(from.baseSalary());
		}
	}

}
