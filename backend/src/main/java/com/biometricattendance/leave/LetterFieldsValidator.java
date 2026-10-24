package com.biometricattendance.leave;

import java.util.regex.Pattern;

import com.biometricattendance.common.ApiException;

/**
 * Memeriksa isian surat menurut templat yang dibekukan, saat pengajuan dikirim (spec §7). Draft boleh belum
 * lengkap sehingga validator ini tidak dipanggil saat menyimpan draft.
 */
final class LetterFieldsValidator {

	private static final Pattern PHONE = Pattern.compile("[0-9 +\\-]{8,30}");

	private LetterFieldsValidator() {
	}

	static void validate(LetterTemplate template, LeaveRequest r) {
		if (isBlank(r.getReason())) {
			throw ApiException.badRequest("Alasan wajib diisi");
		}
		if (template == LetterTemplate.CUTI) {
			if (isBlank(r.getContactPhone())) {
				throw ApiException.badRequest("No. HP yang bisa dihubungi wajib diisi");
			}
			if (isBlank(r.getLeaveAddress())) {
				throw ApiException.badRequest("Alamat selama cuti wajib diisi");
			}
		}
		if (!isBlank(r.getContactPhone()) && !PHONE.matcher(r.getContactPhone()).matches()) {
			throw ApiException.badRequest(
					"No. HP hanya boleh berisi angka, spasi, + dan -, sepanjang 8 sampai 30 karakter");
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
