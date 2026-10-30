package com.biometricattendance.overtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class OvertimeDtos {

	private OvertimeDtos() {
	}

	/** Dihitung saat dibaca, tidak disimpan (K-140). */
	public enum OvertimeStatus {
		/** Belum ada keputusan untuk hari itu. */
		BELUM_DIVALIDASI,
		/** Kandidat saat divalidasi sama dengan kandidat sekarang. */
		TERVALIDASI,
		/** Rekap berubah sejak divalidasi (mis. koreksi jam pulang); tidak dihitung sampai divalidasi ulang. */
		PERLU_VALIDASI_ULANG
	}

	/**
	 * Satu hari lembur satu karyawan. Durasi dalam detik, tanpa pembulatan (Q-008 masih terbuka).
	 *
	 * @param approvedSeconds null bila belum divalidasi; 0 berarti ditolak
	 */
	public record OvertimeRow(Long employeeId, String fullName, String branchName, String categoryName,
			LocalDate date, LocalTime checkOut, long candidateSeconds, Long approvedSeconds, String note,
			String validatedByName, Instant validatedAt, OvertimeStatus status) {
	}

	/**
	 * Keputusan untuk satu hari. {@code approvedMinutes} harus bilangan bulat (BigDecimal agar 1.5 ditolak, bukan
	 * dipotong diam-diam menjadi 1); menit penuh = floor(kandidat/60) berarti disetujui seluruhnya.
	 * {@code expectedCandidateSeconds} adalah kandidat yang dilihat pengguna; bila rekap
	 * sudah berubah, permintaan ditolak 409 agar pengguna tidak menyetujui angka yang sudah usang.
	 */
	public record ValidationItem(@NotNull(message = "Karyawan wajib dipilih") Long employeeId,
			@NotNull(message = "Tanggal wajib diisi") LocalDate workDate,
			@NotNull(message = "Lembur disetujui wajib diisi") @DecimalMin(value = "0",
					message = "Lembur disetujui tidak boleh negatif") @Digits(integer = 9, fraction = 0,
							message = "Lembur disetujui harus bilangan bulat menit") BigDecimal approvedMinutes,
			@NotNull(message = "Kandidat lembur wajib dikirim") Long expectedCandidateSeconds,
			@Size(max = 1000, message = "Catatan maksimal 1000 karakter") String note) {
	}

	public record ValidationRequest(@NotEmpty(message = "Pilih lembur yang divalidasi") @Size(max = 200,
			message = "Maksimal 200 lembur sekali validasi") List<@Valid @NotNull(
					message = "Data lembur wajib diisi") ValidationItem> items) {
	}

}
