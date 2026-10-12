package com.biometricattendance.workrule;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.biometricattendance.calculation.EffectiveRule;
import com.biometricattendance.calculation.RuleField;
import com.biometricattendance.calculation.RuleLevel;
import com.biometricattendance.calculation.RuleValues;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class WorkRuleDtos {

	private WorkRuleDtos() {
	}

	/** Parameter yang dikosongkan (null) mewarisi level di atasnya. */
	public record WorkRuleRequest(@NotNull(message = "Level aturan wajib diisi") RuleLevel scope, Long categoryId,
			Long employeeId, @NotNull(message = "Tanggal berlaku wajib diisi") LocalDate effectiveFrom,
			LocalTime workStart, LocalTime workEnd, LocalTime breakStart, LocalTime breakEnd,
			@Min(value = 0, message = "Toleransi tidak boleh negatif") @Max(value = 600,
					message = "Toleransi maksimal 600 menit") Integer lateToleranceMinutes,
			LocalTime overtimeStart, Set<DayOfWeek> workDays,
			@DecimalMin(value = "0", message = "Upah harian tidak boleh negatif") @Digits(integer = 12,
					fraction = 2, message = "Upah harian maksimal 2 angka desimal") BigDecimal dailyWage,
			@DecimalMin(value = "0", message = "Tarif lembur tidak boleh negatif") @Digits(integer = 12,
					fraction = 2, message = "Tarif lembur maksimal 2 angka desimal") BigDecimal overtimeRatePerHour,
			@DecimalMin(value = "0", message = "Gaji pokok tidak boleh negatif") @Digits(integer = 12, fraction = 2,
					message = "Gaji pokok maksimal 2 angka desimal") BigDecimal baseSalary,
			@Size(max = 255) String note) {

		RuleValues values() {
			return new RuleValues(workStart, workEnd, breakStart, breakEnd, lateToleranceMinutes, overtimeStart,
					workDays, dailyWage, overtimeRatePerHour, baseSalary);
		}

	}

	/** @param locked true jika tanggal berlakunya sudah tiba sehingga versi tidak bisa diubah lagi (K-050) */
	public record WorkRuleResponse(Long id, RuleLevel scope, Long categoryId, String categoryName, Long employeeId,
			String employeeName, LocalDate effectiveFrom, LocalTime workStart, LocalTime workEnd,
			LocalTime breakStart, LocalTime breakEnd, Integer lateToleranceMinutes, LocalTime overtimeStart,
			Set<DayOfWeek> workDays, BigDecimal dailyWage, BigDecimal overtimeRatePerHour, BigDecimal baseSalary,
			String note, String createdBy, boolean locked) {

		static WorkRuleResponse from(WorkRule rule, LocalDate today) {
			RuleValues v = rule.getValues();
			return new WorkRuleResponse(rule.getId(), rule.getScope(),
					rule.getCategory() == null ? null : rule.getCategory().getId(),
					rule.getCategory() == null ? null : rule.getCategory().getName(),
					rule.getEmployee() == null ? null : rule.getEmployee().getId(),
					rule.getEmployee() == null ? null : rule.getEmployee().getFullName(), rule.getEffectiveFrom(),
					v.workStart(), v.workEnd(), v.breakStart(), v.breakEnd(), v.lateToleranceMinutes(),
					v.overtimeStart(), v.workDays(), v.dailyWage(), v.overtimeRatePerHour(), v.baseSalary(),
					rule.getNote(), rule.getCreatedBy(), !rule.getEffectiveFrom().isAfter(today));
		}

	}

	/**
	 * Aturan yang berlaku untuk seorang karyawan pada sebuah tanggal, beserta asal setiap nilai.
	 *
	 * @param missing parameter wajib yang belum diatur (label Bahasa Indonesia)
	 */
	public record EffectiveRuleResponse(Long employeeId, String employeeName, Long categoryId, String categoryName,
			LocalDate date, RuleValues values, Map<RuleField, EffectiveRule.Source> sources, List<String> missing) {

		public static EffectiveRuleResponse of(Long employeeId, String employeeName, Long categoryId,
				String categoryName, LocalDate date, EffectiveRule rule) {
			return new EffectiveRuleResponse(employeeId, employeeName, categoryId, categoryName, date, rule.values(),
					rule.sources(), rule.missingForDailyCalculation().stream().map(RuleField::label).toList());
		}

	}

}
