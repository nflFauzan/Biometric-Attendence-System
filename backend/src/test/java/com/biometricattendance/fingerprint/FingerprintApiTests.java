package com.biometricattendance.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.biometricattendance.ingest.RakitanFixtures;
import com.jayway.jsonpath.JsonPath;

/**
 * API web pendaftaran sidik jari (spec §3.2, §16): persetujuan sekali per karyawan, syarat mulai pendaftaran,
 * satu ENROLL aktif per mesin dan per jari, serta pembatalan.
 */
class FingerprintApiTests extends RakitanFixtures {

	@Test
	void consentIsRecordedOnceWithAudit() throws Exception {
		long employee = createEmployee(createBranch(), unique("P"));

		String first = consent(employee).andExpect(status().isOk())
			.andExpect(jsonPath("$.consentBy").value("owner-absensi"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String second = consent(employee).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<String>read(second, "$.consentAt")).isEqualTo(JsonPath.<String>read(first, "$.consentAt"));
		assertThat(audits("FINGERPRINT_CONSENT", String.valueOf(employee))).isEqualTo(1);
	}

	@Test
	void enrollCreatesWaitingCommand() throws Exception {
		long branch = createBranch();
		long employee = readyEmployee(branch);
		long device = onlineRakitan(branch).id();

		String id = JsonPath.read(enroll(employee, device, 1).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("MENUNGGU"))
			.andExpect(jsonPath("$.message").value("Menunggu mesin…"))
			.andExpect(jsonPath("$.finger").value(1))
			.andExpect(jsonPath("$.deviceId").value(device))
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.id");

		assertThat(audits("ENROLL_REQUEST", id)).isEqualTo(1);
	}

	@Test
	void enrollRequiresConsent() throws Exception {
		long branch = createBranch();
		long employee = createEmployee(branch, unique("P"));
		expectDetail(enroll(employee, onlineRakitan(branch).id(), 1), 400, "Centang persetujuan karyawan dulu.");
	}

	@Test
	void enrollRequiresPin() throws Exception {
		long branch = createBranch();
		long employee = createEmployee(branch, null);
		consent(employee).andExpect(status().isOk());
		expectDetail(enroll(employee, onlineRakitan(branch).id(), 1), 400, "Isi PIN mesin karyawan dulu.");
	}

	@Test
	void enrollRequiresActiveEmployee() throws Exception {
		long branch = createBranch();
		long employee = readyEmployee(branch);
		jdbc.update("update employees set active = false where id = ?", employee);
		expectDetail(enroll(employee, onlineRakitan(branch).id(), 1), 400,
				"Karyawan nonaktif tidak bisa didaftarkan sidik jarinya.");
	}

	@Test
	void enrollRejectsZkPushDevice() throws Exception {
		long branch = createBranch();
		long employee = readyEmployee(branch);
		long zk = createDevice(branch, unique("SN"), ACCEPT_FROM);
		jdbc.update("update devices set last_seen_at = now() where id = ?", zk);
		expectDetail(enroll(employee, zk, 1), 400,
				"Mesin ini tidak mendukung pendaftaran dari web; daftarkan langsung di mesin.");
	}

	@Test
	void enrollRejectsInactiveDevice() throws Exception {
		long branch = createBranch();
		long employee = readyEmployee(branch);
		long device = onlineRakitan(branch).id();
		jdbc.update("update devices set active = false where id = ?", device);
		expectDetail(enroll(employee, device, 1), 400, "Mesin nonaktif.");
	}

	@Test
	void enrollRejectsOfflineDevice() throws Exception {
		long branch = createBranch();
		long employee = readyEmployee(branch);
		expectDetail(enroll(employee, createRakitan(branch).id(), 1), 400, "Mesin sedang offline.");
	}

	@Test
	void enrollUnknownDeviceIs404() throws Exception {
		long employee = readyEmployee(createBranch());
		enroll(employee, 999_999_999L, 1).andExpect(status().isNotFound());
	}

	@Test
	void enrollRejectsFingerOutsideOneOrTwo() throws Exception {
		long branch = createBranch();
		long employee = readyEmployee(branch);
		enroll(employee, onlineRakitan(branch).id(), 3).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.finger").value("Jari harus 1 atau 2"));
	}

	@Test
	void oneActiveEnrollPerDevice() throws Exception {
		long branch = createBranch();
		long device = onlineRakitan(branch).id();
		enrollOk(readyEmployee(branch), device, 1);
		expectDetail(enroll(readyEmployee(branch), device, 1), 409, "Mesin sedang dipakai untuk pendaftaran.");
	}

	@Test
	void oneActiveEnrollPerFinger() throws Exception {
		long branch = createBranch();
		long employee = readyEmployee(branch);
		enrollOk(employee, onlineRakitan(branch).id(), 1);
		expectDetail(enroll(employee, onlineRakitan(branch).id(), 1), 409, "Jari ini sedang didaftarkan.");
	}

	@Test
	void staleCommandDoesNotBlock() throws Exception {
		long branch = createBranch();
		long device = onlineRakitan(branch).id();
		String id = enrollOk(readyEmployee(branch), device, 1);
		jdbc.update("update device_commands set created_at = now() - interval '31 seconds' where id = ?::uuid", id);

		enroll(readyEmployee(branch), device, 1).andExpect(status().isCreated());
	}

	@Test
	void overviewListsFingersAndActiveCommands() throws Exception {
		long branch = createBranch();
		long employee = readyEmployee(branch);
		String id = enrollOk(employee, onlineRakitan(branch).id(), 2);

		mockMvc.perform(get("/api/employees/" + employee + "/fingerprints").with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.consentBy").value("owner-absensi"))
			.andExpect(jsonPath("$.fingers").isEmpty())
			.andExpect(jsonPath("$.activeCommands[0].id").value(id))
			.andExpect(jsonPath("$.activeCommands[0].finger").value(2));
	}

	@Test
	void cancelActiveCommand() throws Exception {
		long branch = createBranch();
		String id = enrollOk(readyEmployee(branch), onlineRakitan(branch).id(), 1);

		cancel(id).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DIBATALKAN"))
			.andExpect(jsonPath("$.message").value("Pendaftaran dibatalkan."));
		assertThat(audits("CANCEL_COMMAND", id)).isEqualTo(1);

		expectDetail(cancel(id), 409, "Perintah sudah selesai.");
		mockMvc.perform(get("/api/device-commands/" + id).with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DIBATALKAN"));
	}

	@Test
	void unknownCommandIs404() throws Exception {
		mockMvc.perform(get("/api/device-commands/" + UUID.randomUUID()).with(HR)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/device-commands/bukan-uuid").with(HR)).andExpect(status().isNotFound());
		cancel(UUID.randomUUID().toString()).andExpect(status().isNotFound());
	}

	private ResultActions cancel(String id) throws Exception {
		return mockMvc.perform(post("/api/device-commands/" + id + "/cancel").with(HR).with(csrf()));
	}

	private static void expectDetail(ResultActions result, int status, String detail) throws Exception {
		result.andExpect(status().is(status)).andExpect(jsonPath("$.detail").value(detail));
	}

	private int audits(String action, String entityId) {
		return jdbc.queryForObject("select count(*) from audit_logs where action = ? and entity_id = ?",
				Integer.class, action, entityId);
	}

}
