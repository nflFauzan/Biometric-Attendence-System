package com.biometricattendance.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import com.biometricattendance.common.ProblemResponses;

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
			ProblemResponses.write(response, HttpStatus.FORBIDDEN, ProblemResponses.PASSWORD_CHANGE_REQUIRED);
			return false;
		}
		return true;
	}

}
