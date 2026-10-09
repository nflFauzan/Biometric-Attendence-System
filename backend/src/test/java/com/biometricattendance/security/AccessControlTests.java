package com.biometricattendance.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.biometricattendance.IntegrationTest;

/** Matrix hak akses sesuai docs/kebutuhan.md: Owner, HR/Finance, Karyawan, dan pengguna anonim. */
class AccessControlTests extends IntegrationTest {

	private static final RequestPostProcessor OWNER = user("owner-test").roles("OWNER");
	private static final RequestPostProcessor HR_FINANCE = user("hr-test").roles("HR_FINANCE");
	private static final RequestPostProcessor EMPLOYEE = user("karyawan-test").roles("EMPLOYEE");

	@ParameterizedTest
	@ValueSource(strings = { "/api/branches", "/api/employee-categories", "/api/employees", "/api/devices",
			"/api/users" })
	void masterDataReadableByOwnerAndHrFinanceOnly(String path) throws Exception {
		mockMvc.perform(get(path).with(OWNER)).andExpect(status().isOk());
		mockMvc.perform(get(path).with(HR_FINANCE)).andExpect(status().isOk());
		mockMvc.perform(get(path).with(EMPLOYEE)).andExpect(status().isForbidden());
		mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/branches", "/api/employee-categories", "/api/employees", "/api/devices",
			"/api/users" })
	void employeeCannotWriteMasterData(String path) throws Exception {
		mockMvc.perform(post(path).with(EMPLOYEE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden());
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/branches", "/api/employees", "/api/users" })
	void writeWithoutCsrfTokenIsRejected(String path) throws Exception {
		mockMvc.perform(post(path).with(OWNER).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void auditLogIsOwnerOnly() throws Exception {
		mockMvc.perform(get("/api/audit-logs").with(OWNER)).andExpect(status().isOk());
		mockMvc.perform(get("/api/audit-logs").with(HR_FINANCE)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/audit-logs").with(EMPLOYEE)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/audit-logs")).andExpect(status().isUnauthorized());
	}

}
