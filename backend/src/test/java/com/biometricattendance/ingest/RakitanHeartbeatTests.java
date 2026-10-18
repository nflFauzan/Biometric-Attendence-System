package com.biometricattendance.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.biometricattendance.ingest.rakitan.RakitanController;
import com.jayway.jsonpath.JsonPath;

/**
 * Autentikasi alat rakitan dan heartbeat (docs/protokol-alat-rakitan.md §2–§4): kunci salah/dicabut 401, mesin
 * nonaktif 403 tanpa memperbarui status online, dan ID alat rakitan tidak bisa dipakai lewat /iclock.
 */
class RakitanHeartbeatTests extends RakitanFixtures {

	private static final String FULL_HEARTBEAT = """
			{"firmware":"0.1.0","ip":"192.168.43.51","uptimeSeconds":3600,"pendingScans":2,
			 "sensor":{"used":12,"capacity":150},"fieldBaru":true}
			""";

	@Test
	void serverTimeAlwaysHasSeconds() {
		assertThat(RakitanController.formatServerTime(Instant.parse("2026-10-17T02:41:00Z"),
				ZoneId.of("Asia/Jakarta")))
			.isEqualTo("2026-10-17T09:41:00+07:00");
	}

	@Test
	void heartbeatWithValidKeyReturnsBranchTimeAndRecordsContact() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());

		heartbeat(rakitan.key(), FULL_HEARTBEAT).andExpect(status().isOk())
			.andExpect(jsonPath("$.timezone").value("Asia/Jakarta"))
			.andExpect(jsonPath("$.deviceName").value(rakitan.name()))
			.andExpect(jsonPath("$.branchName").isNotEmpty())
			.andExpect(jsonPath("$.commands").isEmpty())
			.andExpect(jsonPath("$.cancel").isEmpty())
			.andExpect(jsonPath("$.serverTime")
				.value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+07:00")));

		Map<String, Object> device = device(rakitan.id());
		assertThat(device.get("firmware")).isEqualTo("0.1.0");
		assertThat(device.get("lastIp")).isEqualTo("192.168.43.51");
		assertThat(device.get("online")).isEqualTo(true);
		assertThat(device.get("lastInfo")).isEqualTo("uptime=3600s; antrean=2; sensor=12/150");
		assertThat(device.get("keyFirstUsedAt")).isNotNull();
	}

	@Test
	void emptyBodyHeartbeatIsAccepted() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());
		heartbeat(rakitan.key(), "{}").andExpect(status().isOk());
	}

	@Test
	void missingOrWrongKeyIs401AndNotRecorded() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());
		heartbeat(null, "{}").andExpect(status().isUnauthorized())
			.andExpect(content().json("{\"error\":\"KUNCI_DITOLAK\"}"));
		heartbeat("salah", "{}").andExpect(status().isUnauthorized())
			.andExpect(content().json("{\"error\":\"KUNCI_DITOLAK\"}"));
		assertThat(device(rakitan.id()).get("lastSeenAt")).isNull();
	}

	@Test
	void inactiveDeviceIs403AndStaysOffline() throws Exception {
		long branch = createBranch();
		Rakitan rakitan = createRakitan(branch);
		mockMvc.perform(put("/api/devices/" + rakitan.id()).with(OWNER).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Nonaktif\",\"branchId\":" + branch + ",\"active\":false}"))
			.andExpect(status().isOk());

		heartbeat(rakitan.key(), "{}").andExpect(status().isForbidden())
			.andExpect(content().json("{\"error\":\"MESIN_NONAKTIF\"}"));
		assertThat(device(rakitan.id()).get("lastSeenAt")).isNull();
	}

	@Test
	void revokedKeyIsRejectedImmediately() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());
		heartbeat(rakitan.key(), "{}").andExpect(status().isOk());

		String newKey = issueNewKey(rakitan.id());

		heartbeat(rakitan.key(), "{}").andExpect(status().isUnauthorized());
		heartbeat(newKey, "{}").andExpect(status().isOk());
	}

	@Test
	void malformedJsonIs400() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());
		heartbeat(rakitan.key(), "{bukan json").andExpect(status().isBadRequest())
			.andExpect(content().json("{\"error\":\"FORMAT_SALAH\"}"));
	}

	@Test
	void rakitanIdIsRejectedAtIclockAndNotListedAsUnregistered() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		createEmployee(branch, pin);
		Rakitan rakitan = createRakitan(branch);

		postAttlog(rakitan.deviceId(), lines(line(pin, "2026-10-17 08:00:00"))).andExpect(status().isUnauthorized());

		assertThat(scanCount(rakitan.id())).isZero();
		mockMvc.perform(get("/api/devices/unregistered-attempts").with(OWNER))
			.andExpect(content().string(not(containsString(rakitan.deviceId()))));
	}

	/** Konfirmasi "baru saja terhubung" sekali per kunci baru (K-099). */
	@Test
	void firstContactPerKeyNotifiesDashboard() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());
		MvcResult stream = mockMvc.perform(get("/api/dashboard/stream").with(HR))
			.andExpect(request().asyncStarted())
			.andReturn();

		heartbeat(rakitan.key(), "{}").andExpect(status().isOk());
		assertThat(connectedEvents(stream)).isEqualTo(1);
		assertThat(stream.getResponse().getContentAsString()).contains(rakitan.name());

		heartbeat(rakitan.key(), "{}").andExpect(status().isOk());
		assertThat(connectedEvents(stream)).isEqualTo(1);

		String newKey = issueNewKey(rakitan.id());
		heartbeat(newKey, "{}").andExpect(status().isOk());
		assertThat(connectedEvents(stream)).isEqualTo(2);
	}

	@Test
	void heartbeatStoresSensorCapacity() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());

		heartbeat(rakitan.key(), "{\"sensor\":{\"used\":12,\"capacity\":150}}").andExpect(status().isOk());

		Map<String, Object> device = device(rakitan.id());
		assertThat(device.get("sensorUsed")).isEqualTo(12);
		assertThat(device.get("sensorCapacity")).isEqualTo(150);
		assertThat(device.get("sensorAlmostFull")).isEqualTo(false);
	}

	/** Peringatan saat terisi lebih dari app.device.sensor-warning-percent (90%); tepat 90% belum. */
	@Test
	void sensorAboveThresholdIsFlagged() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());

		heartbeat(rakitan.key(), "{\"sensor\":{\"used\":136,\"capacity\":150}}").andExpect(status().isOk());
		assertThat(device(rakitan.id()).get("sensorAlmostFull")).isEqualTo(true);

		heartbeat(rakitan.key(), "{\"sensor\":{\"used\":135,\"capacity\":150}}").andExpect(status().isOk());
		assertThat(device(rakitan.id()).get("sensorAlmostFull")).isEqualTo(false);
	}

	@Test
	void heartbeatWithoutSensorKeepsLastValue() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());
		heartbeat(rakitan.key(), "{\"sensor\":{\"used\":12,\"capacity\":150}}").andExpect(status().isOk());

		heartbeat(rakitan.key(), "{}").andExpect(status().isOk());
		heartbeat(rakitan.key(), "{\"sensor\":{\"used\":20,\"capacity\":0}}").andExpect(status().isOk());

		Map<String, Object> device = device(rakitan.id());
		assertThat(device.get("sensorUsed")).isEqualTo(12);
		assertThat(device.get("sensorCapacity")).isEqualTo(150);
	}

	private static int connectedEvents(MvcResult stream) throws Exception {
		return stream.getResponse().getContentAsString().split("\"DEVICE_CONNECTED\"", -1).length - 1;
	}

	private Map<String, Object> device(long id) throws Exception {
		String list = mockMvc.perform(get("/api/devices").with(OWNER)).andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> rows = JsonPath.read(list, "$[?(@.id == " + id + ")]");
		return rows.get(0);
	}

}
