package com.biometricattendance.leave.letter;

import java.util.List;

/**
 * Isi surat yang siap digambar. Semua teks sudah final (tanggal diformat di zona perusahaan oleh pembuat model),
 * sehingga {@link LeaveLetterRenderer} hanya menata tampilan dan tidak membaca database.
 *
 * @param stamp cap diagonal untuk surat yang belum final, atau null
 * @param verifyUrl alamat verifikasi publik; null untuk draft (tanpa QR)
 */
public record LetterModel(String letterNumber, String branchName, String title, String employeeName,
		String employeeNumber, String position, String intro, List<Field> fields, List<String> attachmentNames,
		List<SignatureBox> boxes, String stamp, String verifyUrl, String generatedAt) {

	public record Field(String label, String value) {
	}

	/**
	 * Satu kotak tanda tangan. {@code image} (PNG) opsional; {@code text} berisi keterangan di bawah nama, atau
	 * null bila tahap belum diputus (kotak dibiarkan kosong, hanya judul).
	 */
	public record SignatureBox(String title, byte[] image, String name, String text) {
	}

}
