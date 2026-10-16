package com.biometricattendance.calculation;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Nilai parameter dalam satu versi aturan. Nilai null berarti "tidak diatur di level ini" dan
 * diwarisi dari level di atasnya (K-046).
 */
public record RuleValues(LocalTime workStart, LocalTime workEnd, LocalTime breakStart, LocalTime breakEnd,
		Integer lateToleranceMinutes, LocalTime overtimeStart, Set<DayOfWeek> workDays, BigDecimal dailyWage,
		BigDecimal overtimeRatePerHour, BigDecimal baseSalary, Integer minScanGapMinutes) {

	public RuleValues {
		workDays = workDays == null ? null : Set.copyOf(workDays);
	}

	/** True jika versi ini mengisi parameter tersebut. */
	boolean defines(RuleField field) {
		return switch (field) {
			case WORK_START -> workStart != null;
			case WORK_END -> workEnd != null;
			case BREAK -> breakStart != null;
			case LATE_TOLERANCE -> lateToleranceMinutes != null;
			case OVERTIME_START -> overtimeStart != null;
			case WORK_DAYS -> workDays != null;
			case DAILY_WAGE -> dailyWage != null;
			case OVERTIME_RATE -> overtimeRatePerHour != null;
			case BASE_SALARY -> baseSalary != null;
			case MIN_SCAN_GAP -> minScanGapMinutes != null;
		};
	}

	public boolean isEmpty() {
		for (RuleField field : RuleField.values()) {
			if (defines(field)) {
				return false;
			}
		}
		return true;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private LocalTime workStart;
		private LocalTime workEnd;
		private LocalTime breakStart;
		private LocalTime breakEnd;
		private Integer lateToleranceMinutes;
		private LocalTime overtimeStart;
		private Set<DayOfWeek> workDays;
		private BigDecimal dailyWage;
		private BigDecimal overtimeRatePerHour;
		private BigDecimal baseSalary;
		private Integer minScanGapMinutes;

		private Builder() {
		}

		public Builder workStart(LocalTime value) {
			workStart = value;
			return this;
		}

		public Builder workEnd(LocalTime value) {
			workEnd = value;
			return this;
		}

		public Builder breakStart(LocalTime value) {
			breakStart = value;
			return this;
		}

		public Builder breakEnd(LocalTime value) {
			breakEnd = value;
			return this;
		}

		public Builder lateToleranceMinutes(Integer value) {
			lateToleranceMinutes = value;
			return this;
		}

		public Builder overtimeStart(LocalTime value) {
			overtimeStart = value;
			return this;
		}

		public Builder workDays(Set<DayOfWeek> value) {
			workDays = value == null ? null : (value.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(value));
			return this;
		}

		public Builder dailyWage(BigDecimal value) {
			dailyWage = value;
			return this;
		}

		public Builder overtimeRatePerHour(BigDecimal value) {
			overtimeRatePerHour = value;
			return this;
		}

		public Builder baseSalary(BigDecimal value) {
			baseSalary = value;
			return this;
		}

		public Builder minScanGapMinutes(Integer value) {
			minScanGapMinutes = value;
			return this;
		}

		public RuleValues build() {
			return new RuleValues(workStart, workEnd, breakStart, breakEnd, lateToleranceMinutes, overtimeStart,
					workDays, dailyWage, overtimeRatePerHour, baseSalary, minScanGapMinutes);
		}

	}

}
