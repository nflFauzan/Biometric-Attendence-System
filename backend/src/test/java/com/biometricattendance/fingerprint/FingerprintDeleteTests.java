package com.biometricattendance.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.biometricattendance.ingest.RakitanFixtures;
import com.jayway.jsonpath.JsonPath;

/**
 * Hapus satu jari (spec §17.2, §17.3, §17.6): catatan jari langsung hilang, lalu satu DELETE_TEMPLATE untuk setiap
 * alat rakitan yang pernah tersambung, termasuk yang nonaktif.
 */
class FingerprintDeleteTests extends RakitanFixtures {

	@Test
	void deleteFingerRemovesRecordAndQueuesPerMachine() throws Exception {
		long branch = createBranch();
		Rakitan a = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		registerFinger(employee, a, 1);
		String enrollId = jdbc.queryForObject("select command_id::text from employee_fingerprints where employee_id = ?",
				String.class, employee);

		deleteFinger(employee, 1).andExpect(status().isOk()).andExpect(jsonPath("$.fingers").isEmpty());

		String command = deleteCommandFor(employee, a.id());
		assertThat(command).isNotNull();
		assertThat(jdbc.queryForMap("select type, finger, status from device_commands where id = ?::uuid", command))
			.containsEntry("type", "DELETE_TEMPLATE")
			.containsEntry("finger", 1)
			.containsEntry("status", "MENUNGGU");
		assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'FINGERPRINT_DELETED'"
				+ " and after_data is null and before_data like ?", Integer.class, "%" + enrollId + "%"))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'DELETE_REQUEST'"
				+ " and entity_id = ?", Integer.class, command)).isEqualTo(1);
	}

	@Test
	void deleteTargetsEveryConnectedRakitan() throws Exception {
		long branch = createBranch();
		Rakitan online = onlineRakitan(branch);
		Rakitan inactive = onlineRakitan(branch);
		Rakitan neverConnected = createRakitan(branch);
		long x401 = createDevice(branch, uniqueSerial(), ACCEPT_FROM);
		long employee = readyEmployee(branch);
		registerFinger(employee, online, 1);
		deactivate(inactive, branch);

		deleteFinger(employee, 1).andExpect(status().isOk());

		assertThat(deleteCommandFor(employee, online.id())).isNotNull();
		assertThat(deleteCommandFor(employee, inactive.id())).isNotNull();
		assertThat(deleteCommandFor(employee, neverConnected.id())).isNull();
		assertThat(deleteCommandFor(employee, x401)).isNull();
	}

	@Test
	void deleteWhileEnrollActiveIs409() throws Exception {
		long branch = createBranch();
		Rakitan a = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		registerFinger(employee, a, 1);
		enrollOk(employee, a.id(), 1);

		deleteFinger(employee, 1).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Jari ini sedang didaftarkan."));
	}

	@Test
	void deleteUnknownFingerIs404() throws Exception {
		long employee = readyEmployee(createBranch());

		deleteFinger(employee, 2).andExpect(status().isNotFound());
	}

	@Test
	void overviewListsDeletionsPerMachineWithStatus() throws Exception {
		long branch = createBranch();
		Rakitan online = onlineRakitan(branch);
		Rakitan offline = onlineRakitan(branch);
		Rakitan inactive = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		registerFinger(employee, online, 1);
		deactivate(inactive, branch);
		jdbc.update("update devices set last_seen_at = now() - interval '1 day' where id = ?", offline.id());

		deleteFinger(employee, 1).andExpect(status().isOk());

		String body = overview(employee);
		assertThat(deletionMessage(body, online.id())).isEqualTo("Menunggu mesin…");
		assertThat(deletionMessage(body, offline.id())).isEqualTo("Menunggu (mesin offline)");
		assertThat(deletionMessage(body, inactive.id())).isEqualTo("Menunggu (mesin nonaktif)");
		assertThat(JsonPath.<List<Object>>read(body, "$.deletions[?(@.deviceId == " + online.id() + ")].finger"))
			.containsExactly(1);
	}

	@Test
	void retryRequeuesFailedDelete() throws Exception {
		long branch = createBranch();
		Rakitan a = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		registerFinger(employee, a, 1);
		deleteFinger(employee, 1).andExpect(status().isOk());
		String id = deleteCommandFor(employee, a.id());
		heartbeat(a.key(), "{}").andExpect(status().isOk());
		result(a.key(), id, "{\"status\":\"GAGAL\",\"error\":\"SENSOR_ERROR\"}").andExpect(status().isOk());
		String failed = overview(employee);
		assertThat(deletionMessage(failed, a.id())).isEqualTo("Sensor bermasalah. Hubungi teknisi.");
		assertThat(JsonPath.<List<Boolean>>read(failed, "$.deletions[?(@.deviceId == " + a.id() + ")].retryable"))
			.containsExactly(true);

		retry(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MENUNGGU"));

		assertThat(commandStatus(id)).isEqualTo("MENUNGGU");
		assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'COMMAND_RETRY'"
				+ " and entity_id = ?", Integer.class, id)).isEqualTo(1);
		heartbeat(a.key(), "{}").andExpect(status().isOk()).andExpect(jsonPath("$.commands[?(@.id == '" + id + "')]")
			.exists());
	}

	@Test
	void retryOnlyForFailedDelete() throws Exception {
		long branch = createBranch();
		Rakitan a = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		String enroll = enrollOk(employee, a.id(), 1);
		heartbeat(a.key(), "{}").andExpect(status().isOk());
		result(a.key(), enroll, "{\"status\":\"GAGAL\",\"error\":\"KUALITAS_BURUK\"}").andExpect(status().isOk());

		retry(enroll).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Hanya penghapusan yang gagal yang bisa dicoba lagi."));

		registerFinger(employee, a, 1);
		deleteFinger(employee, 1).andExpect(status().isOk());
		retry(deleteCommandFor(employee, a.id())).andExpect(status().isConflict());
	}

	@Test
	void successfulDeleteLeavesList() throws Exception {
		long branch = createBranch();
		Rakitan a = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		registerFinger(employee, a, 1);
		deleteFinger(employee, 1).andExpect(status().isOk());
		String id = deleteCommandFor(employee, a.id());
		heartbeat(a.key(), "{}").andExpect(status().isOk());

		result(a.key(), id, "{\"status\":\"BERHASIL\"}").andExpect(status().isOk());

		assertThat(JsonPath.<List<Object>>read(overview(employee), "$.deletions[?(@.deviceId == " + a.id() + ")]"))
			.isEmpty();
	}

	@Test
	void zkPushFlag() throws Exception {
		long branch = createBranch();
		createDevice(branch, uniqueSerial(), ACCEPT_FROM);
		long employee = readyEmployee(branch);

		assertThat(JsonPath.<Boolean>read(overview(employee), "$.zkPushDeviceActive")).isTrue();
	}

	private String overview(long employee) throws Exception {
		return mockMvc.perform(get("/api/employees/" + employee + "/fingerprints").with(HR))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private static String deletionMessage(String overview, long deviceId) {
		List<String> messages = JsonPath.read(overview, "$.deletions[?(@.deviceId == " + deviceId + ")].message");
		assertThat(messages).hasSize(1);
		return messages.get(0);
	}

	private ResultActions retry(String id) throws Exception {
		return mockMvc.perform(post("/api/device-commands/" + id + "/retry").with(HR).with(csrf()));
	}

	@Test
	void enrollBlockedUntilOldDeleteDone() throws Exception {
		long branch = createBranch();
		Rakitan a = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		registerFinger(employee, a, 1);
		deleteFinger(employee, 1).andExpect(status().isOk());

		enroll(employee, a.id(), 1).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Tunggu sampai sidik jari lama selesai dihapus di mesin ini."));

		String delete = deleteCommandFor(employee, a.id());
		heartbeat(a.key(), "{}").andExpect(status().isOk());
		result(a.key(), delete, "{\"status\":\"BERHASIL\"}").andExpect(status().isOk());

		enroll(employee, a.id(), 1).andExpect(status().isCreated());
	}

}
