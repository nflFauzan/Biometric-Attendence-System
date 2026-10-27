package com.biometricattendance.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.biometricattendance.IntegrationTest;
import com.biometricattendance.account.Role;
import com.biometricattendance.account.UserAccount;
import com.biometricattendance.account.UserAccountRepository;
import com.biometricattendance.employee.EmployeeRepository;
import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/**
 * B-03: sesi lama tidak berlaku lagi setelah password di-reset, akun dinonaktifkan, atau role diubah.
 * Test ini memakai sesi Spring Session JDBC sungguhan (cookie SESSION + tabel SPRING_SESSION),
 * bukan MockHttpSession, agar pencabutan di database benar-benar teruji.
 */
class SessionRevocationTests extends IntegrationTest {

	private static final String PASSWORD = "password-awal";

	private static final RequestPostProcessor OWNER = user("owner-sesi").roles("OWNER");

	private static final RequestPostProcessor HR = user("hr-sesi").roles("HR_FINANCE");

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private SessionRepositoryFilter<?> sessionRepositoryFilter;

	@Autowired
	private UserAccountRepository accounts;

	@Autowired
	private EmployeeRepository employees;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbc;

	private MockMvc web;

	@BeforeEach
	void setUpRealSessions() {
		web = MockMvcBuilders.webAppContextSetup(context)
			.addFilters(sessionRepositoryFilter)
			.apply(springSecurity())
			.build();
	}

	@Test
	void resetPasswordRevokesExistingSessions() throws Exception {
		UserAccount target = createAccount(Role.HR_FINANCE, null);
		Cookie session = login(target.getUsername());
		web.perform(get("/api/branches").cookie(session)).andExpect(status().isOk());
		assertThat(sessionCount(target.getUsername())).isEqualTo(1);

		web.perform(post("/api/users/" + target.getId() + "/reset-password").with(OWNER).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"newPassword\":\"sementara-123\"}"))
			.andExpect(status().isNoContent());

		assertThat(sessionCount(target.getUsername())).isZero();
		web.perform(get("/api/branches").cookie(session)).andExpect(status().isUnauthorized());
		web.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void hrResettingEmployeePasswordRevokesEmployeeSessions() throws Exception {
		UserAccount target = createAccount(Role.EMPLOYEE, createEmployee());
		Cookie session = login(target.getUsername());
		web.perform(get("/api/me/employee").cookie(session)).andExpect(status().isOk());

		web.perform(post("/api/users/" + target.getId() + "/reset-password").with(HR).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"newPassword\":\"sementara-123\"}"))
			.andExpect(status().isNoContent());

		web.perform(get("/api/me/employee").cookie(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void deactivatingAccountRevokesExistingSessions() throws Exception {
		UserAccount target = createAccount(Role.HR_FINANCE, null);
		Cookie session = login(target.getUsername());

		updateAccount(target, "HR_FINANCE", null, false);

		web.perform(get("/api/branches").cookie(session)).andExpect(status().isUnauthorized());
		web.perform(loginRequest(target.getUsername())).andExpect(status().isUnauthorized());
	}

	@Test
	void roleChangeRevokesSessionsAndOldPermissionsCannotBeReused() throws Exception {
		long employeeId = createEmployee();
		UserAccount target = createAccount(Role.HR_FINANCE, null);
		Cookie oldSession = login(target.getUsername());
		web.perform(get("/api/branches").cookie(oldSession)).andExpect(status().isOk());

		updateAccount(target, "EMPLOYEE", employeeId, true);

		// Sesi lama yang membawa role HR/Finance tidak bisa dipakai lagi, untuk baca maupun tulis.
		web.perform(get("/api/branches").cookie(oldSession)).andExpect(status().isUnauthorized());
		web.perform(post("/api/branches").cookie(oldSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"" + unique("C") + "\",\"name\":\"X\",\"timezone\":\"Asia/Jakarta\",\"active\":true}"))
			.andExpect(status().isUnauthorized());

		// Login ulang mendapat role baru, dan hak HR/Finance sudah tidak ada.
		Cookie newSession = login(target.getUsername());
		web.perform(get("/api/auth/me").cookie(newSession)).andExpect(jsonPath("$.role").value("EMPLOYEE"));
		web.perform(get("/api/branches").cookie(newSession)).andExpect(status().isForbidden());
	}

	@Test
	void updateWithoutRoleOrStatusChangeKeepsSessions() throws Exception {
		UserAccount target = createAccount(Role.HR_FINANCE, null);
		Cookie session = login(target.getUsername());

		updateAccount(target, "HR_FINANCE", null, true);

		web.perform(get("/api/branches").cookie(session)).andExpect(status().isOk());
	}

	@Test
	void changingOwnPasswordKeepsCurrentSessionAndRevokesOthers() throws Exception {
		UserAccount target = createAccount(Role.HR_FINANCE, null);
		Cookie current = login(target.getUsername());
		Cookie otherDevice = login(target.getUsername());
		assertThat(sessionCount(target.getUsername())).isEqualTo(2);

		web.perform(post("/api/auth/change-password").cookie(current).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"password-baru-1\"}"))
			.andExpect(status().isNoContent());

		web.perform(get("/api/branches").cookie(current)).andExpect(status().isOk());
		web.perform(get("/api/branches").cookie(otherDevice)).andExpect(status().isUnauthorized());
		assertThat(sessionCount(target.getUsername())).isEqualTo(1);
	}

	/**
	 * K-014: sesi berlaku 8 jam. Atribut cookie (HttpOnly, SameSite) diuji lewat server HTTP sungguhan
	 * di SessionCookieHttpTests, karena MockMvc tidak menampilkan atribut yang ditulis Spring Session.
	 */
	@Test
	void sessionTimeoutIsEightHours() throws Exception {
		UserAccount target = createAccount(Role.HR_FINANCE, null);
		login(target.getUsername());

		Integer maxInactive = jdbc.queryForObject(
				"SELECT MAX_INACTIVE_INTERVAL FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", Integer.class,
				target.getUsername());
		assertThat(maxInactive).isEqualTo(8 * 60 * 60);
	}

	@Test
	void unknownSessionCookieIsUnauthorized() throws Exception {
		web.perform(get("/api/auth/me").cookie(new Cookie("SESSION", "YmVsdW0tYWRhLXNlc2k=")))
			.andExpect(status().isUnauthorized());
	}

	/** K-137: menonaktifkan karyawan ikut menonaktifkan akunnya dan mencabut sesinya. */
	@Test
	void deactivatingEmployeeDeactivatesLinkedAccountAndEndsSessions() throws Exception {
		long employeeId = createEmployee();
		UserAccount target = createAccount(Role.EMPLOYEE, employeeId);
		Cookie session = login(target.getUsername());

		updateEmployeeActive(employeeId, false);

		assertThat(accounts.findById(target.getId()).orElseThrow().isActive()).isFalse();
		web.perform(get("/api/me/employee").cookie(session)).andExpect(status().isUnauthorized());
		web.perform(loginRequest(target.getUsername())).andExpect(status().isUnauthorized());
		assertThat(accountAudits(target, "UPDATE")).isEqualTo(1);
	}

	/** K-137: mengaktifkan kembali karyawan ikut mengaktifkan akunnya (keputusan pengguna 2026-10-27). */
	@Test
	void reactivatingEmployeeReactivatesLinkedAccount() throws Exception {
		long employeeId = createEmployee();
		UserAccount target = createAccount(Role.EMPLOYEE, employeeId);

		updateEmployeeActive(employeeId, false);
		updateEmployeeActive(employeeId, true);

		assertThat(accounts.findById(target.getId()).orElseThrow().isActive()).isTrue();
		assertThat(accountAudits(target, "UPDATE")).isEqualTo(2);
		login(target.getUsername());
	}

	/** K-137: Finance & Accounting tidak boleh mengubah akun non-karyawan lewat form karyawan (sama dengan menu Akun). */
	@Test
	void financeCannotDeactivateEmployeeLinkedToFinanceAccount() throws Exception {
		long employeeId = createEmployee();
		UserAccount colleague = createAccount(Role.HR_FINANCE, employeeId);

		performEmployeeActive(employeeId, false, HR).andExpect(status().isForbidden());

		assertThat(accounts.findById(colleague.getId()).orElseThrow().isActive()).isTrue();
		assertThat(employees.findById(employeeId).orElseThrow().isActive()).isTrue();
	}

	@Test
	void deactivatingEmployeeWithoutAccountStillWorks() throws Exception {
		long employeeId = createEmployee();

		updateEmployeeActive(employeeId, false);

		assertThat(employees.findById(employeeId).orElseThrow().isActive()).isFalse();
	}

	@Test
	void savingEmployeeWithoutStatusChangeLeavesAccountAlone() throws Exception {
		long employeeId = createEmployee();
		UserAccount target = createAccount(Role.EMPLOYEE, employeeId);
		Cookie session = login(target.getUsername());

		updateEmployeeActive(employeeId, true);

		web.perform(get("/api/me/employee").cookie(session)).andExpect(status().isOk());
		assertThat(accountAudits(target, "UPDATE")).isZero();
	}

	private int accountAudits(UserAccount target, String action) {
		Integer count = jdbc.queryForObject(
				"SELECT count(*) FROM audit_logs WHERE action = ? AND entity_type = 'USER_ACCOUNT' AND entity_id = ?",
				Integer.class, action, target.getId().toString());
		return count == null ? 0 : count;
	}

	private void updateEmployeeActive(long employeeId, boolean active) throws Exception {
		performEmployeeActive(employeeId, active, OWNER).andExpect(status().isOk());
	}

	private ResultActions performEmployeeActive(long employeeId, boolean active, RequestPostProcessor actor)
			throws Exception {
		String current = web.perform(get("/api/employees/" + employeeId).with(OWNER))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return web.perform(put("/api/employees/" + employeeId).with(actor).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(String.format(
					"{\"employeeNumber\":\"%s\",\"fullName\":\"Sesi Uji\",\"branchId\":%s,\"categoryId\":%s,\"joinDate\":\"2026-01-05\",\"active\":%s}",
					JsonPath.read(current, "$.employeeNumber"), JsonPath.read(current, "$.branchId").toString(),
					JsonPath.read(current, "$.categoryId").toString(), active)));
	}

	private UserAccount createAccount(Role role, Long employeeId) {
		UserAccount account = new UserAccount();
		account.setUsername(unique("sesi-"));
		account.setPasswordHash(passwordEncoder.encode(PASSWORD));
		account.setRole(role);
		account.setMustChangePassword(false);
		if (employeeId != null) {
			account.setEmployee(employees.getReferenceById(employeeId));
		}
		return accounts.save(account);
	}

	private void updateAccount(UserAccount target, String role, Long employeeId, boolean active) throws Exception {
		web.perform(put("/api/users/" + target.getId()).with(OWNER).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(String.format("{\"role\":\"%s\",\"employeeId\":%s,\"active\":%s}", role,
					employeeId == null ? "null" : employeeId.toString(), active)))
			.andExpect(status().isOk());
	}

	private Cookie login(String username) throws Exception {
		Cookie cookie = web.perform(loginRequest(username))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie("SESSION");
		assertThat(cookie).as("cookie SESSION dari Spring Session").isNotNull();
		return cookie;
	}

	private static RequestBuilder loginRequest(String username) {
		return post("/api/auth/login").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, PASSWORD));
	}

	private int sessionCount(String username) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?",
				Integer.class, username);
		return count == null ? 0 : count;
	}

	private long createEmployee() throws Exception {
		long branchId = idOf("/api/branches",
				String.format("{\"code\":\"%s\",\"name\":\"C\",\"timezone\":\"Asia/Jakarta\",\"active\":true}",
						unique("C")));
		long categoryId = idOf("/api/employee-categories",
				String.format("{\"code\":\"%s\",\"name\":\"K\",\"active\":true}", unique("K")));
		return idOf("/api/employees", String.format(
				"{\"employeeNumber\":\"%s\",\"fullName\":\"Sesi Uji\",\"branchId\":%d,\"categoryId\":%d,\"joinDate\":\"2026-01-05\",\"active\":true}",
				unique("EMP"), branchId, categoryId));
	}

	private long idOf(String path, String body) throws Exception {
		String response = web.perform(post(path).with(OWNER).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

}
