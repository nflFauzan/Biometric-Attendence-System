package com.biometricattendance.security;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.biometricattendance.IntegrationTest;

/**
 * Dokumentasi API saat dinyalakan (OPENAPI_ENABLED=true): hanya Owner dan HR/Finance yang boleh membaca.
 * Perilaku default (mati) diuji di AccessControlTests.
 */
@TestPropertySource(properties = "OPENAPI_ENABLED=true")
class OpenApiAccessTests extends IntegrationTest {

	private static final RequestPostProcessor OWNER = user("owner-docs").roles("OWNER");
	private static final RequestPostProcessor HR_FINANCE = user("hr-docs").roles("HR_FINANCE");
	private static final RequestPostProcessor EMPLOYEE = user("karyawan-docs").roles("EMPLOYEE");

	@Test
	void apiDocsListPhaseOneEndpointsForManagers() throws Exception {
		mockMvc.perform(get("/v3/api-docs").with(OWNER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.info.title").value("Biometric Attendance System API"))
			.andExpect(jsonPath("$.paths", hasKey("/api/auth/login")))
			.andExpect(jsonPath("$.paths", hasKey("/api/users/{id}/reset-password")))
			.andExpect(jsonPath("$.paths", hasKey("/api/employees/{id}")))
			.andExpect(jsonPath("$.paths", hasKey("/api/devices/{id}")));
		mockMvc.perform(get("/v3/api-docs").with(HR_FINANCE)).andExpect(status().isOk());
	}

	@Test
	void apiDocsAndSwaggerUiAreClosedForEmployeeAndAnonymous() throws Exception {
		for (String path : new String[] { "/v3/api-docs", "/swagger-ui/index.html" }) {
			mockMvc.perform(get(path).with(EMPLOYEE)).andExpect(status().isForbidden());
			mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
		}
		mockMvc.perform(get("/swagger-ui/index.html").with(OWNER)).andExpect(status().isOk());
	}

}
