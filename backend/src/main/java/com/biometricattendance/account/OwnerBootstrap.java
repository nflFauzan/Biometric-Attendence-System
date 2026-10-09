package com.biometricattendance.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.audit.AuditService;

/**
 * Membuat akun Owner pertama dari environment (OWNER_USERNAME, OWNER_INITIAL_PASSWORD)
 * hanya jika belum ada Owner sama sekali. Owner wajib mengganti password saat login pertama.
 */
@Component
public class OwnerBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(OwnerBootstrap.class);

	private final UserAccountRepository repository;
	private final PasswordEncoder passwordEncoder;
	private final AuditService audit;
	private final String username;
	private final String password;

	public OwnerBootstrap(UserAccountRepository repository, PasswordEncoder passwordEncoder, AuditService audit,
			@Value("${app.bootstrap.owner-username:}") String username,
			@Value("${app.bootstrap.owner-password:}") String password) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.audit = audit;
		this.username = username;
		this.password = password;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (repository.existsByRole(Role.OWNER)) {
			return;
		}
		if (username.isBlank() || password.length() < UserAccountDtos.MIN_PASSWORD_LENGTH) {
			log.warn("Belum ada akun Owner. Isi OWNER_USERNAME dan OWNER_INITIAL_PASSWORD (min. 8 karakter) "
					+ "lalu jalankan ulang server.");
			return;
		}
		UserAccount owner = new UserAccount();
		owner.setUsername(UserAccountService.normalize(username));
		owner.setPasswordHash(passwordEncoder.encode(password));
		owner.setRole(Role.OWNER);
		owner.setMustChangePassword(true);
		repository.save(owner);
		audit.record("system", "CREATE", UserAccountService.ENTITY, owner.getId(), null,
				UserAccountDtos.UserResponse.from(owner), "Akun Owner pertama dari environment");
		log.info("Akun Owner pertama dibuat: {}", owner.getUsername());
	}

}
