package com.biometricattendance.calculation;

/** Parameter aturan kerja yang diwariskan per kolom. Istirahat diwariskan sebagai satu pasangan mulai-selesai. */
public enum RuleField {

	WORK_START("jam masuk"),
	WORK_END("jam pulang"),
	BREAK("jam istirahat"),
	LATE_TOLERANCE("toleransi keterlambatan"),
	OVERTIME_START("batas mulai lembur"),
	WORK_DAYS("hari kerja"),
	DAILY_WAGE("upah harian"),
	OVERTIME_RATE("tarif lembur per jam"),
	BASE_SALARY("gaji pokok"),
	MIN_SCAN_GAP("jeda minimum antar scan");

	private final String label;

	RuleField(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

}
