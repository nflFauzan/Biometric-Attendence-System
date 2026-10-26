package com.biometricattendance.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.biometricattendance.IntegrationTest;
import com.biometricattendance.account.Role;
import com.biometricattendance.account.UserAccount;
import com.biometricattendance.account.UserAccountRepository;
import com.biometricattendance.audit.AuditLogRepository;
import com.jayway.jsonpath.JsonPath;

/** Tambah karyawan sekaligus akun login (K-133), tanggal masuk otomatis (K-134), hak tulis F&A (K-135). */
class EmployeeAccountTests extends IntegrationTest {

	private static final RequestPostProcessor OWNER = user("owner-akun").roles("OWNER");
	private static final RequestPostProcessor FINANCE = user("fa-akun").roles("HR_FINANCE");

	@Autowired
	private EmployeeRepository employees;

	@Autowired
	private UserAccountRepository accounts;

	@Autowired
	private AuditLogRepository auditLogs;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private Long branchId;

	private Long categoryId;

	@Test
	void createWithAccountMakesBothAndHidesPassword() throws Exception {
		String number = unique("E");
		String username = unique("u") + "@takka.id";
		String password = number + "-takka";
		String body = create(OWNER, employeeJson(number, "\"2026-01-05\"", account(username, password, "EMPLOYEE")))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(body).doesNotContain(password);
		UserAccount account = accounts.findByUsername(username).orElseThrow();
		assertThat(account.getRole()).isEqualTo(Role.EMPLOYEE);
		assertThat(account.getEmployee().getId()).isEqualTo(idOf(body));
		assertThat(account.isMustChangePassword()).isTrue();
		assertThat(passwordEncoder.matches(password, account.getPasswordHash())).isTrue();
		assertThat(auditLogs.findAll()).noneMatch(log -> String.valueOf(log.getAfterData()).contains(password));
	}

	@Test
	void createWithoutJoinDateUsesTodayInBranchZone() throws Exception {
		create(OWNER, employeeJson(unique("E"), "null", "null")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.joinDate").value(LocalDate.now(ZoneId.of("Asia/Makassar")).toString()));
	}

	@Test
	void updateWithoutJoinDateKeepsExisting() throws Exception {
		String number = unique("E");
		String body = create(OWNER, employeeJson(number, "\"2026-01-05\"", "null")).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		mockMvc
			.perform(put("/api/employees/" + idOf(body)).with(OWNER)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(employeeJson(number, "null", "null")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.joinDate").value("2026-01-05"));
	}

	@Test
	void duplicateUsernameRollsBackEmployee() throws Exception {
		String username = unique("u") + "@takka.id";
		create(OWNER, employeeJson(unique("E"), "null", account(username, "rahasia-123", "EMPLOYEE")))
			.andExpect(status().isCreated());

		String number = unique("E");
		create(OWNER, employeeJson(number, "null", account(username, "rahasia-456", "EMPLOYEE")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Username sudah dipakai"));
		assertThat(employees.existsByEmployeeNumberIgnoreCase(number)).isFalse();
	}

	@Test
	void financeCanCreateEmployeeWithEmployeeAccount() throws Exception {
		String username = unique("u") + "@takka.id";
		create(FINANCE, employeeJson(unique("E"), "null", account(username, "rahasia-123", "EMPLOYEE")))
			.andExpect(status().isCreated());
		assertThat(accounts.findByUsername(username))
			.hasValueSatisfying(a -> assertThat(a.getRole()).isEqualTo(Role.EMPLOYEE));
	}

	@Test
	void financeCannotCreateOwnerAccountAndNothingIsSaved() throws Exception {
		String number = unique("E");
		create(FINANCE, employeeJson(number, "null", account(unique("u") + "@takka.id", "rahasia-123", "OWNER")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("Finance & Accounting hanya boleh mengelola akun karyawan"));
		assertThat(employees.existsByEmployeeNumberIgnoreCase(number)).isFalse();
	}

	@Test
	void accountPasswordTooShortIsFieldError() throws Exception {
		create(OWNER, employeeJson(unique("E"), "null", account(unique("u") + "@takka.id", "pendek", "EMPLOYEE")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors['account.password']").value("Password minimal 8 karakter"));
	}

	private ResultActions create(RequestPostProcessor actor, String json) throws Exception {
		return mockMvc.perform(
				post("/api/employees").with(actor).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static String account(String username, String password, String role) {
		return String.format("{\"username\":\"%s\",\"password\":\"%s\",\"role\":\"%s\"}", username, password, role);
	}

	/** Cabang berzona Asia/Makassar agar tanggal "hari ini" mengikuti zona cabang, bukan zona server. */
	private String employeeJson(String number, String joinDate, String account) throws Exception {
		return String.format(
				"{\"employeeNumber\":\"%s\",\"fullName\":\"Karyawan Uji\",\"branchId\":%d,\"categoryId\":%d,"
						+ "\"joinDate\":%s,\"active\":true,\"account\":%s}",
				number, branchId(), categoryId(), joinDate, account);
	}

	private long branchId() throws Exception {
		if (branchId == null) {
			String code = unique("C");
			branchId = idOf(postOwner("/api/branches", String.format(
					"{\"code\":\"%s\",\"name\":\"Cabang %s\",\"timezone\":\"Asia/Makassar\",\"active\":true}",
					code, code)));
		}
		return branchId;
	}

	private long categoryId() throws Exception {
		if (categoryId == null) {
			categoryId = idOf(postOwner("/api/employee-categories",
					String.format("{\"code\":\"%s\",\"name\":\"Kategori Akun\",\"active\":true}", unique("K"))));
		}
		return categoryId;
	}

	private String postOwner(String url, String json) throws Exception {
		return mockMvc.perform(post(url).with(OWNER).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private static long idOf(String body) {
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

}
