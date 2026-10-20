package com.biometricattendance.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.biometricattendance.ingest.RakitanFixtures;

/** "Buat kode baru" mencabut kunci, jadi wajib konfirmasi ulang password Owner (K-098, spec §18.3). */
class DeviceKeyConfirmTests extends RakitanFixtures {

	@Test
	void wrongPasswordRejected() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());
		String before = keyHash(rakitan.id());

		issue(rakitan.id(), "{\"confirmPassword\":\"salah-sekali\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Password salah."));

		assertThat(keyHash(rakitan.id())).isEqualTo(before);
	}

	@Test
	void missingPasswordRejected() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());

		issue(rakitan.id(), "{}").andExpect(status().isBadRequest());
	}

	@Test
	void correctPasswordIssuesKey() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());

		issue(rakitan.id(), "{\"confirmPassword\":\"" + OWNER_PASSWORD + "\"}").andExpect(status().isOk())
			.andExpect(jsonPath("$.issuedKey.deviceKey").isString());

		heartbeat(rakitan.key(), "{}").andExpect(status().isUnauthorized());
	}

	private ResultActions issue(long deviceId, String body) throws Exception {
		return mockMvc.perform(post("/api/devices/" + deviceId + "/key").with(REAL_OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private String keyHash(long deviceId) {
		return jdbc.queryForObject("select key_hash from devices where id = ?", String.class, deviceId);
	}

}
