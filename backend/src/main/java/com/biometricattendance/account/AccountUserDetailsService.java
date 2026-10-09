package com.biometricattendance.account;

import java.util.Locale;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountUserDetailsService implements UserDetailsService {

	private final UserAccountRepository repository;

	public AccountUserDetailsService(UserAccountRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) {
		UserAccount account = repository.findByUsername(username.trim().toLowerCase(Locale.ROOT))
			.orElseThrow(() -> new UsernameNotFoundException("Akun tidak ditemukan"));
		return User.withUsername(account.getUsername())
			.password(account.getPasswordHash())
			.roles(account.getRole().name())
			.disabled(!account.isActive())
			.build();
	}

}
