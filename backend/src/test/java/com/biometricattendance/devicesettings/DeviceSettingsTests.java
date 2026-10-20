package com.biometricattendance.devicesettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.biometricattendance.ingest.RakitanFixtures;

/** Paket pengaturan ringan berversi untuk alat rakitan (spec §7.2, §18.1–18.2). */
class DeviceSettingsTests extends RakitanFixtures {

	private static String values(String greeting, int heartbeat) {
		return "{\"headerText\":\"Cabang Uji\",\"greeting\":\"" + greeting + "\",\"nameDisplay\":\"FIRST\","
				+ "\"brightness\":70,\"buzzerVolume\":0,\"duplicateNoticeSeconds\":90,\"heartbeatSeconds\":" + heartbeat
				+ "}";
	}

	@Test
	void defaultsBeforeFirstSave() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());

		mockMvc.perform(get("/api/devices/" + rakitan.id() + "/settings").with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(0))
			.andExpect(jsonPath("$.values.headerText").value(""))
			.andExpect(jsonPath("$.values.greeting").value("Silakan tempelkan jari"))
			.andExpect(jsonPath("$.values.nameDisplay").value("FULL"))
			.andExpect(jsonPath("$.values.brightness").value(80))
			.andExpect(jsonPath("$.values.buzzerVolume").value(50))
			.andExpect(jsonPath("$.values.duplicateNoticeSeconds").value(60))
			.andExpect(jsonPath("$.values.heartbeatSeconds").value(5));
		heartbeat(rakitan.key(), "{\"settingsVersion\":0}").andExpect(status().isOk())
			.andExpect(jsonPath("$.settings").doesNotExist());
	}

	@Test
	void saveBumpsVersionAndHeartbeatDeliversOnce() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());

		save(rakitan.id(), values("Halo", 5)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));

		heartbeat(rakitan.key(), "{\"settingsVersion\":0}").andExpect(status().isOk())
			.andExpect(jsonPath("$.settings.version").value(1))
			.andExpect(jsonPath("$.settings.greeting").value("Halo"))
			.andExpect(jsonPath("$.settings.nameDisplay").value("FIRST"))
			.andExpect(jsonPath("$.settings.buzzerVolume").value(0));
		heartbeat(rakitan.key(), "{\"settingsVersion\":1}").andExpect(status().isOk())
			.andExpect(jsonPath("$.settings").doesNotExist());
		mockMvc.perform(get("/api/devices/" + rakitan.id() + "/settings").with(HR))
			.andExpect(jsonPath("$.appliedVersion").value(1));
	}

	@Test
	void unchangedSaveKeepsVersion() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());
		save(rakitan.id(), values("Halo", 5)).andExpect(jsonPath("$.version").value(1));

		save(rakitan.id(), values("Halo", 5)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
		save(rakitan.id(), values("Halo lagi", 5)).andExpect(jsonPath("$.version").value(2));
	}

	@Test
	void validationRejectsOutOfRange() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());

		save(rakitan.id(), values("Halo", 25)).andExpect(status().isBadRequest());
		save(rakitan.id(), values("Halo", 5).replace("\"brightness\":70", "\"brightness\":5"))
			.andExpect(status().isBadRequest());
		save(rakitan.id(), values("Halo", 5).replace("FIRST", "NICK")).andExpect(status().isBadRequest());
		save(rakitan.id(), values("x".repeat(65), 5)).andExpect(status().isBadRequest());
	}

	@Test
	void zkPushRejected() throws Exception {
		long zk = createDevice(createBranch(), unique("SN"), ACCEPT_FROM);

		save(zk, values("Halo", 5)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Pengaturan hanya untuk alat rakitan"));
	}

	@Test
	void applyToAllSkipsInactiveAndZkAndUnchanged() throws Exception {
		long branch = createBranch();
		Rakitan changed = createRakitan(branch);
		Rakitan same = createRakitan(branch);
		Rakitan inactive = createRakitan(branch);
		deactivate(inactive, branch);
		long zk = createDevice(branch, unique("SN"), ACCEPT_FROM);
		String target = values("Untuk semua " + unique(""), 5);
		save(same.id(), target).andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(put("/api/device-settings").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(target)).andExpect(status().isOk());

		assertThat(version(changed.id())).isEqualTo(1);
		assertThat(version(same.id())).isEqualTo(1);
		assertThat(version(inactive.id())).isNull();
		assertThat(version(zk)).isNull();
	}

	@Test
	void machineAheadOfServerGetsPackageAgain() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());
		save(rakitan.id(), values("Halo", 5));

		// Mis. database dipulihkan dari cadangan: mesin memakai versi yang tidak dikenal server.
		heartbeat(rakitan.key(), "{\"settingsVersion\":7}").andExpect(status().isOk())
			.andExpect(jsonPath("$.settings.version").value(1));
	}

	@Test
	void auditPerDevice() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());

		save(rakitan.id(), values("Halo", 5));

		assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'UPDATE_SETTINGS' and"
				+ " entity_id = ?", Integer.class, String.valueOf(rakitan.id()))).isEqualTo(1);
	}

	private ResultActions save(long deviceId, String body) throws Exception {
		return mockMvc.perform(put("/api/devices/" + deviceId + "/settings").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private Integer version(long deviceId) {
		return jdbc.query("select version from device_settings where device_id = ?",
				rs -> rs.next() ? rs.getInt(1) : null, deviceId);
	}

}
