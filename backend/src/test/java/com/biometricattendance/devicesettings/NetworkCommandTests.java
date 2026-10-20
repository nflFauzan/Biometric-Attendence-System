package com.biometricattendance.devicesettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.biometricattendance.ingest.RakitanFixtures;
import com.jayway.jsonpath.JsonPath;

/** APPLY_NETWORK: paket Wi-Fi dan alamat server dikirim lewat antrean perintah (spec §18.5). */
class NetworkCommandTests extends RakitanFixtures {

	private static final String KANTOR = "{\"ssid\":\"Kantor\",\"password\":\"rahasia123\"}";

	@Test
	void requestDeliveredWithDecryptedPasswords() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());

		request(rakitan.id(), "\"wifi\":[" + KANTOR + ",{\"ssid\":\"Gudang\",\"password\":\"\"}],"
				+ "\"serverUrl\":\"http://10.0.0.5:8888\"")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("APPLY_NETWORK"))
			.andExpect(jsonPath("$.status").value("MENUNGGU"));

		heartbeat(rakitan.key(), "{}").andExpect(jsonPath("$.commands[0].type").value("APPLY_NETWORK"))
			.andExpect(jsonPath("$.commands[0].network.wifi[0].ssid").value("Kantor"))
			.andExpect(jsonPath("$.commands[0].network.wifi[0].password").value("rahasia123"))
			.andExpect(jsonPath("$.commands[0].network.wifi[1].ssid").value("Gudang"))
			.andExpect(jsonPath("$.commands[0].network.wifi[1].password").value(""))
			.andExpect(jsonPath("$.commands[0].network.serverUrl").value("http://10.0.0.5:8888"));
		String stored = jdbc.queryForObject("select string_agg(password_enc, ',') from device_network_wifi w"
				+ " join device_network_configs c on c.id = w.config_id where c.device_id = ?", String.class,
				rakitan.id());
		assertThat(stored).startsWith("v1:").doesNotContain("rahasia123");
	}

	@Test
	void serverOnlyLeavesWifiUnchanged() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());
		requestOk(rakitan.id(), "\"serverUrl\":\"https://absensi.contoh.id\"");

		heartbeat(rakitan.key(), "{}").andExpect(jsonPath("$.commands[0].network.wifi").doesNotExist())
			.andExpect(jsonPath("$.commands[0].network.serverUrl").value("https://absensi.contoh.id"));
	}

	@Test
	void wrongPasswordRejected() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());

		mockMvc.perform(post("/api/devices/" + rakitan.id() + "/network").with(REAL_OWNER).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirmPassword\":\"salah\",\"wifi\":[" + KANTOR + "]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Password salah."));
		assertThat(commandCount(rakitan.id())).isZero();
	}

	@Test
	void validationMessages() throws Exception {
		long id = onlineRakitan(createBranch()).id();

		request(id, "\"wifi\":[" + KANTOR + "," + KANTOR + "]").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Nama Wi-Fi tidak boleh ganda"));
		request(id, "\"wifi\":[{\"ssid\":\"Kantor\",\"password\":\"pendek\"}]").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Password Wi-Fi 8–63 karakter atau kosong untuk Wi-Fi terbuka"));
		request(id, "\"wifi\":[{\"ssid\":\"Kantor\",\"password\":\"" + "x".repeat(64) + "\"}]")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Password Wi-Fi 8–63 karakter atau kosong untuk Wi-Fi terbuka"));
		request(id, "\"serverUrl\":\"ftp://10.0.0.5\"").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Alamat server harus diawali http:// atau https://"));
		request(id, "\"wifi\":[],\"serverUrl\":\" \"").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Isi minimal satu Wi-Fi atau alamat server."));
		assertThat(commandCount(id)).isZero();
	}

	@Test
	void successBecomesCurrentNetwork() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());
		view(rakitan.id()).andExpect(jsonPath("$.currentWifi.length()").value(0))
			.andExpect(jsonPath("$.currentServerUrl").doesNotExist());

		String id = requestOk(rakitan.id(), "\"wifi\":[" + KANTOR + "],\"serverUrl\":\"http://10.0.0.5:8888\"");
		apply(rakitan, id, "{\"status\":\"BERHASIL\"}");

		view(rakitan.id()).andExpect(status().isOk())
			.andExpect(jsonPath("$.currentWifi[0]").value("Kantor"))
			.andExpect(jsonPath("$.currentServerUrl").value("http://10.0.0.5:8888"))
			.andExpect(jsonPath("$.latest.status").value("BERHASIL"))
			.andExpect(jsonPath("$.latest.message").value("Jaringan baru diterapkan"));

		// Hanya alamat server yang diubah: Wi-Fi yang berlaku tetap.
		apply(rakitan, requestOk(rakitan.id(), "\"serverUrl\":\"https://absensi.contoh.id\""),
				"{\"status\":\"BERHASIL\"}");
		view(rakitan.id()).andExpect(jsonPath("$.currentWifi[0]").value("Kantor"))
			.andExpect(jsonPath("$.currentServerUrl").value("https://absensi.contoh.id"));
	}

	@Test
	void failureKeepsPreviousNetwork() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());
		apply(rakitan, requestOk(rakitan.id(), "\"wifi\":[" + KANTOR + "]"), "{\"status\":\"BERHASIL\"}");

		String id = requestOk(rakitan.id(), "\"wifi\":[{\"ssid\":\"Kantor-5G\",\"password\":\"rahasia456\"}]");
		apply(rakitan, id, "{\"status\":\"GAGAL\",\"error\":\"WIFI_GAGAL\",\"detail\":\"Kantor-5G\"}");

		view(rakitan.id()).andExpect(jsonPath("$.currentWifi.length()").value(1))
			.andExpect(jsonPath("$.currentWifi[0]").value("Kantor"))
			.andExpect(jsonPath("$.latest.status").value("GAGAL"))
			.andExpect(jsonPath("$.latest.message")
				.value("Gagal tersambung ke Wi-Fi 'Kantor-5G', kembali ke pengaturan lama."));
	}

	@Test
	void blankPasswordKeepsStoredForSameSsid() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());
		apply(rakitan, requestOk(rakitan.id(), "\"wifi\":[" + KANTOR + "]"), "{\"status\":\"BERHASIL\"}");

		requestOk(rakitan.id(), "\"wifi\":[{\"ssid\":\"Tamu\",\"password\":\"\"},{\"ssid\":\"Kantor\",\"password\":\"\"}]");

		heartbeat(rakitan.key(), "{}").andExpect(jsonPath("$.commands[0].network.wifi[0].password").value(""))
			.andExpect(jsonPath("$.commands[0].network.wifi[1].ssid").value("Kantor"))
			.andExpect(jsonPath("$.commands[0].network.wifi[1].password").value("rahasia123"));
	}

	@Test
	void blankPasswordForNewSsidIsOpenNetwork() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());
		requestOk(rakitan.id(), "\"wifi\":[{\"ssid\":\"Kantor\"}]");

		heartbeat(rakitan.key(), "{}").andExpect(jsonPath("$.commands[0].network.wifi[0].password").value(""));
	}

	@Test
	void wifiPasswordNeverInApiOrAudit() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());
		String id = JsonPath.read(request(rakitan.id(), "\"wifi\":[" + KANTOR + "]").andExpect(status().isCreated())
			.andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("rahasia123"))
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.id");
		apply(rakitan, id, "{\"status\":\"BERHASIL\"}");

		for (String url : new String[] { "/api/devices/" + rakitan.id() + "/network", "/api/devices",
				"/api/device-commands/" + id }) {
			String body = mockMvc.perform(get(url).with(OWNER)).andReturn().getResponse().getContentAsString();
			assertThat(body).as(url).doesNotContain("rahasia123");
		}
		String audit = jdbc.queryForObject(
				"select string_agg(coalesce(before_data, '') || coalesce(after_data, ''), '') from audit_logs",
				String.class);
		assertThat(audit).doesNotContain("rahasia123").contains("Kantor");
	}

	private ResultActions request(long deviceId, String fields) throws Exception {
		return mockMvc.perform(post("/api/devices/" + deviceId + "/network").with(REAL_OWNER).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"confirmPassword\":\"" + OWNER_PASSWORD + "\"," + fields + "}"));
	}

	private String requestOk(long deviceId, String fields) throws Exception {
		return JsonPath.read(request(deviceId, fields).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.id");
	}

	private void apply(Rakitan rakitan, String commandId, String resultJson) throws Exception {
		heartbeat(rakitan.key(), "{}").andExpect(status().isOk());
		result(rakitan.key(), commandId, resultJson).andExpect(status().isOk());
	}

	private ResultActions view(long deviceId) throws Exception {
		return mockMvc.perform(get("/api/devices/" + deviceId + "/network").with(OWNER));
	}

	private int commandCount(long deviceId) {
		return jdbc.queryForObject("select count(*) from device_commands where device_id = ?", Integer.class, deviceId);
	}

}
