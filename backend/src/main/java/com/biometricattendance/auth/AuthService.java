package com.biometricattendance.auth;

import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.account.UserAccount;
import com.biometricattendance.account.UserAccountRepository;
import com.biometricattendance.audit.AuditService;
import com.biometricattendance.auth.AuthDtos.ChangePasswordRequest;
import com.biometricattendance.auth.AuthDtos.MeResponse;
import com.biometricattendance.common.ApiException;

@Service
public class AuthService {

	private final UserAccountRepository accounts;
	private final PasswordEncoder passwordEncoder;
	private final AuditService audit;
	private final SessionRevoker sessions;

	public AuthService(UserAccountRepository accounts, PasswordEncoder passwordEncoder, AuditService audit,
			SessionRevoker sessions) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.audit = audit;
		this.sessions = sessions;
	}

	@Transactional
	public MeResponse recordLogin(String username) {
		UserAccount account = find(username);
		account.setLastLoginAt(Instant.now());
		return MeResponse.from(account);
	}

	@Transactional(readOnly = true)
	public MeResponse me(String username) {
		return MeResponse.from(find(username));
	}

	/** Sesi yang sedang dipakai tetap berlaku; sesi lain milik akun ini (perangkat lain) dicabut. */
	@Transactional
	public void changePassword(String username, ChangePasswordRequest request, String currentSessionId) {
		UserAccount account = find(username);
		if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
			throw ApiException.badRequest("Password lama salah");
		}
		if (request.currentPassword().equals(request.newPassword())) {
			throw ApiException.badRequest("Password baru harus berbeda dari password lama");
		}
		account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		account.setMustChangePassword(false);
		audit.record("CHANGE_PASSWORD", "USER_ACCOUNT", account.getId(), null, null);
		sessions.revokeOthers(account.getUsername(), currentSessionId);
	}

	private UserAccount find(String username) {
		return accounts.findByUsername(username).orElseThrow(() -> ApiException.notFound("Akun"));
	}

}
