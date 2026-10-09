package com.biometricattendance.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Akses ke username dan role pengguna yang sedang login. */
public final class CurrentUser {

	private CurrentUser() {
	}

	public static String username() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth == null ? null : auth.getName();
	}

	public static boolean hasRole(String role) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth != null && auth.getAuthorities().stream()
			.anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
	}

}
