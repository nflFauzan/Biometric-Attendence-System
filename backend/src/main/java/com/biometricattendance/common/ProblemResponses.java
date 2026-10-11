package com.biometricattendance.common;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Menulis ProblemDetail langsung ke response untuk error yang terjadi di filter/interceptor
 * (sebelum controller), sehingga formatnya sama dengan GlobalExceptionHandler.
 * Pesan berupa konstanta tanpa tanda kutip, jadi aman disusun sebagai JSON manual.
 */
public final class ProblemResponses {

	public static final String UNAUTHORIZED = "Silakan login terlebih dahulu";

	public static final String FORBIDDEN = "Anda tidak memiliki akses untuk tindakan ini";

	public static final String CSRF_INVALID = "Sesi keamanan halaman sudah tidak berlaku. Muat ulang halaman lalu coba lagi";

	public static final String PASSWORD_CHANGE_REQUIRED = "Ganti password terlebih dahulu";

	private ProblemResponses() {
	}

	public static void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter()
			.write("{\"status\":" + status.value() + ",\"title\":\"" + status.getReasonPhrase() + "\",\"detail\":\""
					+ detail + "\"}");
	}

}
