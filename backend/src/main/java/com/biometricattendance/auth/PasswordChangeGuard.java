package com.biometricattendance.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Selama akun wajib ganti password, semua API selain /api/auth/** ditolak (403).
 * Status disimpan di session saat login dan dihapus setelah password diganti.
 */
public class PasswordChangeGuard implements HandlerInterceptor {

	static final String SESSION_ATTRIBUTE = "MUST_CHANGE_PASSWORD";

	static void setRequired(HttpSession session, boolean required) {
		session.setAttribute(SESSION_ATTRIBUTE, required);
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		HttpSession session = request.getSession(false);
		if (session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_ATTRIBUTE))) {
			response.setStatus(HttpStatus.FORBIDDEN.value());
			response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
			response.getWriter()
				.write("{\"status\":403,\"title\":\"Forbidden\",\"detail\":\"Ganti password terlebih dahulu\"}");
			return false;
		}
		return true;
	}

}
