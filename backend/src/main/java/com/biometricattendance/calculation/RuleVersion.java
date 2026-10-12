package com.biometricattendance.calculation;

import java.time.LocalDate;
import java.util.Objects;

/** Satu versi aturan pada satu level, berlaku mulai effectiveFrom sampai ada versi lebih baru di level yang sama. */
public record RuleVersion(Long id, RuleLevel level, LocalDate effectiveFrom, RuleValues values) {

	public RuleVersion {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(effectiveFrom, "effectiveFrom");
		Objects.requireNonNull(values, "values");
	}

}
