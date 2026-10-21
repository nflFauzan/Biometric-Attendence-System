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

	/** Perintah tersegel dari sebelum kunci server hilang tidak dikirim, dan heartbeat tetap 200 (review akhir I1). */
	@Test
	void heartbeatSurvivesSealedCommandsWithoutServerKey() throws Exception {
		Rakitan network = onlineRakitan(createBranch());
		Long configId = jdbc.queryForObject("insert into device_network_configs (device_id, server_url, created_by,"
				+ " created_at) values (?, null, 'owner', now()) returning id", Long.class, network.id());
		jdbc.update("insert into device_network_wifi (config_id, position, ssid, password_enc)"
				+ " values (?, 0, 'Kantor', 'v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA')", configId);
		jdbc.update("insert into device_commands (id, device_id, type, status, network_config_id, created_by,"
				+ " created_at) values (gen_random_uuid(), ?, 'APPLY_NETWORK', 'MENUNGGU', ?, 'owner', now())",
				network.id(), configId);
		Rakitan rotating = onlineRakitan(createBranch());
		jdbc.update("update devices set pending_key_hash = repeat('a', 64),"
				+ " pending_key_enc = 'v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA' where id = ?", rotating.id());
		jdbc.update("insert into device_commands (id, device_id, type, status, created_by, created_at)"
				+ " values (gen_random_uuid(), ?, 'ROTATE_KEY', 'MENUNGGU', 'owner', now())", rotating.id());

		heartbeat(network.key(), "{}").andExpect(status().isOk()).andExpect(jsonPath("$.commands.length()").value(0));
		heartbeat(rotating.key(), "{}").andExpect(status().isOk())
			.andExpect(jsonPath("$.commands.length()").value(0));
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
