package com.biometricattendance.calculation;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aturan yang berlaku untuk satu karyawan pada satu tanggal, hasil {@link RuleResolver}. Setiap nilai
 * membawa asalnya (level, versi, tanggal berlaku) agar perhitungan dapat ditelusuri.
 */
public final class EffectiveRule {

	/** Asal sebuah nilai: versi aturan mana yang menetapkannya. */
	public record Source(RuleLevel level, Long versionId, LocalDate effectiveFrom) {
	}

	/** Parameter yang wajib ada untuk menghitung rekap harian. Istirahat dan tarif tidak wajib. */
	private static final List<RuleField> REQUIRED_FOR_DAILY = List.of(RuleField.WORK_START, RuleField.WORK_END,
			RuleField.LATE_TOLERANCE, RuleField.OVERTIME_START, RuleField.WORK_DAYS);

	private final RuleValues values;
	private final Map<RuleField, Source> sources;

	EffectiveRule(RuleValues values, Map<RuleField, Source> sources) {
		this.values = values;
		this.sources = sources.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(sources));
	}

	public RuleValues values() {
		return values;
	}

	public Source source(RuleField field) {
		return sources.get(field);
	}

	public Map<RuleField, Source> sources() {
		return sources;
	}

	public LocalTime workStart() {
		return values.workStart();
	}

	public LocalTime workEnd() {
		return values.workEnd();
	}

	public LocalTime breakStart() {
		return values.breakStart();
	}

	public LocalTime breakEnd() {
		return values.breakEnd();
	}

	public Integer lateToleranceMinutes() {
		return values.lateToleranceMinutes();
	}

	public LocalTime overtimeStart() {
		return values.overtimeStart();
	}

	public Set<DayOfWeek> workDays() {
		return values.workDays();
	}

	public BigDecimal dailyWage() {
		return values.dailyWage();
	}

	public BigDecimal overtimeRatePerHour() {
		return values.overtimeRatePerHour();
	}

	public BigDecimal baseSalary() {
		return values.baseSalary();
	}

	public Integer minScanGapMinutes() {
		return values.minScanGapMinutes();
	}

	public List<RuleField> missingForDailyCalculation() {
		List<RuleField> missing = new ArrayList<>();
		for (RuleField field : REQUIRED_FOR_DAILY) {
			if (!values.defines(field)) {
				missing.add(field);
			}
		}
		return missing;
	}

	public void requireForDailyCalculation() {
		List<RuleField> missing = missingForDailyCalculation();
		if (!missing.isEmpty()) {
			throw new RuleNotConfiguredException(missing);
		}
	}

}
