package com.biometricattendance.devicesettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import com.biometricattendance.ingest.RakitanFixtures;

/** Tanpa DATA_ENCRYPTION_KEY, perubahan jaringan ditolak; fitur lain tetap berjalan (spec §18.7). */
@TestPropertySource(properties = "app.crypto.data-key=")
class NetworkCipherMissingTests extends RakitanFixtures {

	static final String NO_KEY = "Kunci enkripsi server belum diatur. Hubungi pengelola server.";

	@Test
	void networkRejectedWithoutServerKey() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());

		mockMvc.perform(post("/api/devices/" + rakitan.id() + "/network").with(REAL_OWNER).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirmPassword\":\"" + OWNER_PASSWORD + "\",\"serverUrl\":\"http://10.0.0.5:8888\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(NO_KEY));
		assertThat(jdbc.queryForObject("select count(*) from device_commands where device_id = ?", Integer.class,
				rakitan.id())).isZero();
	}

	@Test
	void rotateKeyRejectedButSettingsStillWork() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());

		mockMvc.perform(post("/api/devices/" + rakitan.id() + "/rotate-key").with(REAL_OWNER).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(CONFIRM))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(NO_KEY));
		assertThat(jdbc.queryForObject("select pending_key_hash from devices where id = ?", String.class,
				rakitan.id())).isNull();
		mockMvc.perform(put("/api/devices/" + rakitan.id() + "/settings").with(HR).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"headerText\":\"\",\"greeting\":\"Halo\",\"nameDisplay\":\"FULL\",\"brightness\":80,"
					+ "\"buzzerVolume\":50,\"duplicateNoticeSeconds\":60,\"heartbeatSeconds\":5}"))
			.andExpect(status().isOk());
	}

}
