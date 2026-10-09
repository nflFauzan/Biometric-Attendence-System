package com.biometricattendance.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.biometricattendance.IntegrationTest;
import com.biometricattendance.account.Role;
import com.biometricattendance.account.UserAccount;
import com.biometricattendance.account.UserAccountRepository;

class AuthFlowTests extends IntegrationTest {

	private static final String INITIAL_PASSWORD = "password-awal";

	@Autowired
	private UserAccountRepository accounts;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void firstOwnerIsCreatedFromEnvironmentAndMustChangePassword() {
		UserAccount owner = accounts.findByUsername("owner").orElseThrow();
		assertThat(owner.getRole()).isEqualTo(Role.OWNER);
		assertThat(owner.isMustChangePassword()).isTrue();
		assertThat(owner.getPasswordHash()).doesNotContain("owner-awal-123");
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		String username = createAccount(false);
		mockMvc.perform(loginRequest(username, "salah-sekali"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Username atau password salah"));
	}

	@Test
	void loginWithoutCsrfTokenIsRejected() throws Exception {
		String username = createAccount(false);
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content(credentials(username, INITIAL_PASSWORD)))
			.andExpect(status().isForbidden());
	}

	@Test
	void mustChangePasswordBlocksOtherApisUntilChanged() throws Exception {
		String username = createAccount(true);
		MockHttpSession session = login(username);

		mockMvc.perform(get("/api/auth/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mustChangePassword").value(true));
		mockMvc.perform(get("/api/branches").session(session)).andExpect(status().isForbidden());

		mockMvc.perform(changePassword(session, INITIAL_PASSWORD, "password-baru-1"))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/branches").session(session)).andExpect(status().isOk());
		assertThat(accounts.findByUsername(username).orElseThrow().isMustChangePassword()).isFalse();
	}

	@Test
	void changePasswordRequiresCorrectCurrentPassword() throws Exception {
		MockHttpSession session = login(createAccount(false));
		mockMvc.perform(changePassword(session, "bukan-ini", "password-baru-1"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void logoutEndsTheSession() throws Exception {
		MockHttpSession session = login(createAccount(false));
		mockMvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
		mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void deactivatedAccountCannotLogin() throws Exception {
		String username = createAccount(false);
		UserAccount account = accounts.findByUsername(username).orElseThrow();
		account.setActive(false);
		accounts.save(account);
		mockMvc.perform(loginRequest(username, INITIAL_PASSWORD)).andExpect(status().isUnauthorized());
	}

	private String createAccount(boolean mustChangePassword) {
		UserAccount account = new UserAccount();
		account.setUsername(unique("hr-"));
		account.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
		account.setRole(Role.HR_FINANCE);
		account.setMustChangePassword(mustChangePassword);
		return accounts.save(account).getUsername();
	}

	private MockHttpSession login(String username) throws Exception {
		return (MockHttpSession) mockMvc.perform(loginRequest(username, INITIAL_PASSWORD))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value(username))
			.andReturn()
			.getRequest()
			.getSession(false);
	}

	private static org.springframework.test.web.servlet.RequestBuilder loginRequest(String username,
			String password) {
		return post("/api/auth/login").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(credentials(username, password));
	}

	private static org.springframework.test.web.servlet.RequestBuilder changePassword(MockHttpSession session,
			String current, String next) {
		return post("/api/auth/change-password").session(session)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(String.format("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}", current, next));
	}

	private static String credentials(String username, String password) {
		return String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
	}

}
