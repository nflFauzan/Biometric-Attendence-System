package com.biometricattendance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class HealthEndpointTests extends IntegrationTest {

	@Test
	void healthIsPublicAndUp() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void unknownEndpointsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/anything")).andExpect(status().isUnauthorized());
	}

}
