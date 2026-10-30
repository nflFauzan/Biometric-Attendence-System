package com.biometricattendance.report;

import java.util.Arrays;
import java.util.Optional;

/** Jenis laporan absensi yang bisa diekspor; slug dipakai di URL, judul di layar dan di dalam file. */
public enum ReportType {

	REKAP_ABSENSI("rekap-absensi", "Rekap Absensi"),
	REKAP_LEMBUR("rekap-lembur", "Rekap Lembur"),
	DETAIL_ABSENSI("detail-absensi", "Detail Absensi");

	private final String slug;

	private final String title;

	ReportType(String slug, String title) {
		this.slug = slug;
		this.title = title;
	}

	public String slug() {
		return slug;
	}

	public String title() {
		return title;
	}

	public static Optional<ReportType> fromSlug(String slug) {
		return Arrays.stream(values()).filter(t -> t.slug.equals(slug)).findFirst();
	}

}
