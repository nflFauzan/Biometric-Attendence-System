package com.biometricattendance.account;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.biometricattendance.account.UserAccountDtos.UpdateUserRequest;
import com.biometricattendance.audit.AuditService;
import com.biometricattendance.auth.SessionRevoker;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.employee.EmployeeRepository;

/**
 * K-016: sistem mempertahankan minimal satu Owner aktif. Diuji sebagai unit test karena
 * skenarionya (hanya satu Owner aktif) tidak bisa dibuat di database test bersama tanpa
 * menonaktifkan Owner milik test lain.
 */
class LastOwnerRuleTests {

	private final UserAccountRepository repository = mock(UserAccountRepository.class);
	private final AuditService audit = mock(AuditService.class);
	private final SessionRevoker sessions = mock(SessionRevoker.class);
	private UserAccountService service;

	@BeforeEach
	void setUp() {
		service = new UserAccountService(repository, mock(EmployeeRepository.class), mock(PasswordEncoder.class),
				audit, sessions);
		UserAccount lastOwner = new UserAccount();
		ReflectionTestUtils.setField(lastOwner, "id", 7L);
		lastOwner.setUsername("owner-terakhir");
		lastOwner.setRole(Role.OWNER);
		when(repository.findById(7L)).thenReturn(Optional.of(lastOwner));
		when(repository.countByRoleAndActiveTrue(Role.OWNER)).thenReturn(1L);
		// Pelaku: Owner lain yang akunnya sudah nonaktif di data, tetapi request-nya masih berjalan.
		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
				"owner-lain", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void lastActiveOwnerCannotBeDeactivated() {
		assertRejected(new UpdateUserRequest(Role.OWNER, null, false));
	}

	@Test
	void lastActiveOwnerCannotBeDemoted() {
		assertRejected(new UpdateUserRequest(Role.HR_FINANCE, null, true));
	}

	private void assertRejected(UpdateUserRequest request) {
		assertThatThrownBy(() -> service.update(7L, request)).isInstanceOf(ApiException.class)
			.hasMessage("Harus ada minimal satu Owner yang aktif");
		verify(repository, never()).save(any());
		verify(sessions, never()).revokeAll(anyString());
	}

}
