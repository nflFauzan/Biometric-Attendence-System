package com.biometricattendance.attendance;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Penanda pada scan asli (K-065). Scan tetap disimpan; penanda hanya untuk ditinjau. */
public enum ScanFlag {

	/** PIN belum dipetakan ke karyawan mana pun; scan tidak dipakai rekap sampai dihubungkan (K-068). */
	PIN_TIDAK_DIKENAL,
	/** Waktu scan lebih dari batas toleransi di depan waktu server; jam mesin mungkin salah. */
	WAKTU_DI_MASA_DEPAN,
	/** Mesin berada di cabang tambahan karyawan (bukan penempatan) (Q-022, K-103). */
	CABANG_BERBEDA,
	/** Mesin berada di cabang yang tidak diizinkan untuk karyawan; scan tertahan sampai ditinjau (K-103). */
	CABANG_TIDAK_DIIZINKAN;

	public static String join(Set<ScanFlag> flags) {
		return flags.isEmpty() ? null : flags.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
	}

	public static Set<ScanFlag> parse(String value) {
		if (value == null || value.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(value.split(","))
			.map(ScanFlag::valueOf)
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(ScanFlag.class)));
	}

}
