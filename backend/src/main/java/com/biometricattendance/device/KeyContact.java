package com.biometricattendance.device;

/**
 * Hasil autentikasi alat rakitan dari kuncinya (K-088, docs/protokol-alat-rakitan.md §3).
 *
 * @param device mesin yang dikenali; null jika {@link Status#REJECTED}
 */
public record KeyContact(Status status, Device device) {

	public enum Status {
		/** Kunci benar dan mesin aktif; kontak dicatat. */
		ACCEPTED,
		/** Kunci kosong, salah, dicabut, atau bukan milik alat rakitan (401). */
		REJECTED,
		/** Kunci benar tetapi mesin nonaktif (403); kontak tidak dicatat (K-067). */
		INACTIVE
	}

	static KeyContact rejected() {
		return new KeyContact(Status.REJECTED, null);
	}

}
