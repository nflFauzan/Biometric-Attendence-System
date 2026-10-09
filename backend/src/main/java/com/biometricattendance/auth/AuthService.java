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

	public AuthService(UserAccountRepository accounts, PasswordEncoder passwordEncoder, AuditService audit) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.audit = audit;
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

	@Transactional
	public void changePassword(String username, ChangePasswordRequest request) {
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
	}

	private UserAccount find(String username) {
		return accounts.findByUsername(username).orElseThrow(() -> ApiException.notFound("Akun"));
	}

}
