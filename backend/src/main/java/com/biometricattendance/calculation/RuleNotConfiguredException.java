package com.biometricattendance.calculation;

import java.util.List;
import java.util.stream.Collectors;

/** Parameter aturan wajib belum diatur pada tanggal tersebut (K-047: tidak ada nilai bawaan di kode). */
public class RuleNotConfiguredException extends RuntimeException {

	private final List<RuleField> missing;

	public RuleNotConfiguredException(List<RuleField> missing) {
		super("Aturan belum diatur: " + missing.stream().map(RuleField::label).collect(Collectors.joining(", ")));
		this.missing = List.copyOf(missing);
	}

	public List<RuleField> missing() {
		return missing;
	}

}
