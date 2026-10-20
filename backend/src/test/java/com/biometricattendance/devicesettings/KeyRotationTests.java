package com.biometricattendance.devicesettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import com.biometricattendance.device.DeviceContactEvent;
import com.biometricattendance.device.DeviceKeys;
import com.biometricattendance.ingest.RakitanFixtures;
import com.jayway.jsonpath.JsonPath;

/** ROTATE_KEY: kunci baru dua tahap, kunci lama berlaku sampai kunci baru dipakai (spec §18.6). */
@RecordApplicationEvents
class KeyRotationTests extends RakitanFixtures {

	@Autowired
	private ApplicationEvents events;

	@Test
	void bothKeysAcceptedDuringTransition() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());
		rotate(rakitan.id()).andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("ROTATE_KEY"))
			.andExpect(jsonPath("$.status").value("MENUNGGU"));

		String newKey = deliveredKey(rakitan);
		assertThat(newKey).hasSize(43).isNotEqualTo(rakitan.key());
		heartbeat(rakitan.key(), "{}").andExpect(status().isOk())
			.andExpect(jsonPath("$.commands[0].key").value(newKey));
		heartbeat(newKey, "{}").andExpect(status().isOk());
	}

	@Test
	void oldKeyRejectedAfterNewKeyUsed() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());
		rotate(rakitan.id()).andExpect(status().isCreated());
		String newKey = deliveredKey(rakitan);

		heartbeat(newKey, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.commands.length()").value(0));

		heartbeat(rakitan.key(), "{}").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error").value("KUNCI_DITOLAK"));
		heartbeat(newKey, "{}").andExpect(status().isOk());
	}

	@Test
	void commandSucceedsOnFirstUseAndNoConnectNotice() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());
		String id = rotateOk(rakitan.id());
		String newKey = deliveredKey(rakitan);
		events.clear();

		heartbeat(newKey, "{}").andExpect(status().isOk());

		assertThat(commandStatus(id)).isEqualTo("BERHASIL");
		mockMvc.perform(get("/api/device-commands/" + id).with(OWNER))
			.andExpect(jsonPath("$.message").value("Kunci baru dipakai"));
		assertThat(audits(id, "KEY_ROTATED")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select actor_username from audit_logs where action = 'KEY_ROTATED'"
				+ " and entity_id = ?", String.class, id)).startsWith("mesin RK-");
		assertThat(events.stream(DeviceContactEvent.class).filter(e -> e.deviceId().equals(rakitan.id())))
			.isNotEmpty()
			.noneMatch(DeviceContactEvent::firstConnect);
	}

	@Test
	void concurrentFirstUseOfNewKeyPromotesOnce() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());
		String id = rotateOk(rakitan.id());
		String newKey = deliveredKey(rakitan);

		CountDownLatch start = new CountDownLatch(1);
		Callable<Integer> call = () -> {
			start.await();
			return heartbeat(newKey, "{}").andReturn().getResponse().getStatus();
		};
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> results = List.of(pool.submit(call), pool.submit(call));
			start.countDown();
			for (Future<Integer> result : results) {
				assertThat(result.get()).isEqualTo(200);
			}
		}
		finally {
			pool.shutdown();
		}

		assertThat(commandStatus(id)).isEqualTo("BERHASIL");
		assertThat(audits(id, "KEY_ROTATED")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select key_hash from devices where id = ?", String.class, rakitan.id()))
			.isEqualTo(DeviceKeys.hash(newKey));
	}

	@Test
	void machineFailureDiscardsPendingKey() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());
		String id = rotateOk(rakitan.id());
		String newKey = deliveredKey(rakitan);

		result(rakitan.key(), id, "{\"status\":\"GAGAL\",\"error\":\"SIMPAN_GAGAL\"}").andExpect(status().isOk());

		assertThat(pendingHash(rakitan.id())).isNull();
		heartbeat(newKey, "{}").andExpect(status().isUnauthorized());
		heartbeat(rakitan.key(), "{}").andExpect(status().isOk());
		mockMvc.perform(get("/api/device-commands/" + id).with(OWNER))
			.andExpect(jsonPath("$.message").value("Ganti kunci gagal (SIMPAN_GAGAL)."));
	}

	@Test
	void revokeCancelsRotation() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());
		String id = rotateOk(rakitan.id());
		String rotationKey = deliveredKey(rakitan);

		String issued = issueNewKey(rakitan.id());

		assertThat(commandStatus(id)).isEqualTo("DIBATALKAN");
		assertThat(pendingHash(rakitan.id())).isNull();
		heartbeat(rotationKey, "{}").andExpect(status().isUnauthorized());
		heartbeat(issued, "{}").andExpect(status().isOk());
	}

	@Test
	void cancelWaitingRotationDiscardsPendingKey() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());
		String id = rotateOk(rakitan.id());
		assertThat(pendingHash(rakitan.id())).isNotNull();

		mockMvc.perform(post("/api/device-commands/" + id + "/cancel").with(OWNER).with(csrf()))
			.andExpect(status().isOk());

		assertThat(pendingHash(rakitan.id())).isNull();
	}

	@Test
	void secondRotationWhileActiveRejected() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());
		rotateOk(rakitan.id());
		String pending = pendingHash(rakitan.id());

		rotate(rakitan.id()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Mesin masih menerapkan perubahan jaringan atau kunci."));
		assertThat(pendingHash(rakitan.id())).isEqualTo(pending);
	}

	@Test
	void pendingKeyNeverInApiOrAudit() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());
		String created = rotate(rakitan.id()).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(created, "$.id");
		String newKey = deliveredKey(rakitan);
		String sealed = jdbc.queryForObject("select pending_key_enc from devices where id = ?", String.class,
				rakitan.id());
		String hash = DeviceKeys.hash(newKey);

		StringBuilder seen = new StringBuilder(created);
		for (String url : new String[] { "/api/devices/" + rakitan.id() + "/network", "/api/devices",
				"/api/device-commands/" + id }) {
			seen.append(mockMvc.perform(get(url).with(OWNER)).andReturn().getResponse().getContentAsString());
		}
		heartbeat(newKey, "{}").andExpect(status().isOk());
		seen.append(jdbc.queryForObject(
				"select string_agg(coalesce(before_data, '') || coalesce(after_data, ''), '') from audit_logs",
				String.class));
		assertThat(seen.toString()).doesNotContain(newKey).doesNotContain(hash).doesNotContain(sealed);
	}

	private ResultActions rotate(long deviceId) throws Exception {
		return mockMvc.perform(post("/api/devices/" + deviceId + "/rotate-key").with(REAL_OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(CONFIRM));
	}

	private String rotateOk(long deviceId) throws Exception {
		return JsonPath.read(rotate(deviceId).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.id");
	}

	/** Kunci baru yang dikirim ke mesin lewat heartbeat dengan kunci lama. */
	private String deliveredKey(Rakitan rakitan) throws Exception {
		return JsonPath.read(heartbeat(rakitan.key(), "{}").andExpect(status().isOk())
			.andExpect(jsonPath("$.commands[0].type").value("ROTATE_KEY"))
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.commands[0].key");
	}

	private String pendingHash(long deviceId) {
		return jdbc.queryForObject("select pending_key_hash from devices where id = ?", String.class, deviceId);
	}

	private int audits(String commandId, String action) {
		return jdbc.queryForObject("select count(*) from audit_logs where action = ? and entity_id = ?",
				Integer.class, action, commandId);
	}

}
