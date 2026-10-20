package com.biometricattendance.account;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.CurrentUser;

/**
 * Konfirmasi ulang password sebelum tindakan berisiko pada mesin (K-098, spec §18.3). Ditolak dengan 400, bukan 401,
 * supaya web tidak menganggap sesinya habis.
 */
@Service
public class PasswordConfirmation {

	private final UserAccountRepository accounts;
	private final PasswordEncoder passwordEncoder;

	public PasswordConfirmation(UserAccountRepository accounts, PasswordEncoder passwordEncoder) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public void require(String password) {
		String username = CurrentUser.username();
		boolean matches = password != null && username != null && accounts.findByUsername(username)
			.filter(UserAccount::isActive)
			.map(account -> passwordEncoder.matches(password, account.getPasswordHash()))
			.orElse(false);
		if (!matches) {
			throw ApiException.badRequest("Password salah.");
		}
	}

}
