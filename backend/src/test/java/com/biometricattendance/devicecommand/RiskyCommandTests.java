package com.biometricattendance.devicecommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.biometricattendance.common.ApiException;
import com.biometricattendance.device.DeviceRepository;
import com.biometricattendance.ingest.RakitanFixtures;

/** Perintah berisiko (APPLY_NETWORK, ROTATE_KEY) di antrean (spec §4.2, §18.4). */
class RiskyCommandTests extends RakitanFixtures {

	private static final String BUSY = "Mesin masih menerapkan perubahan jaringan atau kunci.";

	@Autowired
	private DeviceCommandService service;

	@Autowired
	private DeviceCommandRepository commands;

	@Autowired
	private DeviceRepository devices;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void secondRiskyCommandRejected() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());
		network(rakitan.id(), Instant.now());

		assertThatThrownBy(() -> rotate(rakitan.id(), Instant.now())).isInstanceOfSatisfying(ApiException.class,
				ex -> {
					assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
					assertThat(ex.getMessage()).isEqualTo(BUSY);
				});
	}

	@Test
	void riskyHeldWhileEnrollActive() throws Exception {
		long branch = createBranch();
		Rakitan rakitan = onlineRakitan(branch);
		String enroll = enrollOk(readyEmployee(branch), rakitan.id(), 1);
		UUID risky = network(rakitan.id(), Instant.now());

		heartbeat(rakitan.key(), "{}").andExpect(jsonPath("$.commands.length()").value(1))
			.andExpect(jsonPath("$.commands[0].type").value("ENROLL"));
		mockMvc.perform(post("/api/device-commands/" + enroll + "/cancel").with(OWNER).with(csrf()))
			.andExpect(status().isOk());

		heartbeat(rakitan.key(), "{}").andExpect(jsonPath("$.commands.length()").value(1))
			.andExpect(jsonPath("$.commands[0].id").value(risky.toString()))
			.andExpect(jsonPath("$.commands[0].type").value("APPLY_NETWORK"))
			.andExpect(jsonPath("$.commands[0].timeoutSeconds").doesNotExist());
	}

	@Test
	void enrollRejectedWhileRiskyActive() throws Exception {
		long branch = createBranch();
		Rakitan rakitan = onlineRakitan(branch);
		rotate(rakitan.id(), Instant.now());

		enroll(readyEmployee(branch), rakitan.id(), 1).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(BUSY));
	}

	@Test
	void riskyMenungguNeverExpires() throws Exception {
		Instant t = Instant.now();
		UUID id = network(createRakitan(createBranch()).id(), t);

		service.expireStale(t.plus(Duration.ofHours(48)));

		assertThat(commandStatus(id.toString())).isEqualTo(DeviceCommand.MENUNGGU);
	}

	@Test
	void networkSentWithoutResultFailsAfter10Minutes() throws Exception {
		Instant t = Instant.now();
		UUID id = network(createRakitan(createBranch()).id(), t);
		tx().executeWithoutResult(s -> commands.findById(id).orElseThrow().markSent(t));

		service.expireStale(t.plus(Duration.ofMinutes(9)));
		assertThat(commandStatus(id.toString())).isEqualTo(DeviceCommand.DIKIRIM);

		service.expireStale(t.plus(Duration.ofMinutes(11)));
		assertThat(commandStatus(id.toString())).isEqualTo(DeviceCommand.GAGAL);
		assertThat(errorCode(id)).isEqualTo(DeviceCommand.TIDAK_ADA_HASIL);
	}

	@Test
	void rotateKeyFailsAfter24Hours() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());
		Instant t = Instant.now();
		setPendingKey(rakitan.id());
		UUID id = rotate(rakitan.id(), t);

		service.expireStale(t.plus(Duration.ofHours(23)));
		assertThat(commandStatus(id.toString())).isEqualTo(DeviceCommand.MENUNGGU);

		service.expireStale(t.plus(Duration.ofHours(25)));
		assertThat(commandStatus(id.toString())).isEqualTo(DeviceCommand.GAGAL);
		assertThat(errorCode(id)).isEqualTo("KUNCI_BARU_TIDAK_DIPAKAI");
		assertThat(pendingKeyHash(rakitan.id())).isNull();
	}

	@Test
	void cancelRiskyOnlyOwnerAndOnlyWaiting() throws Exception {
		Rakitan waiting = createRakitan(createBranch());
		Rakitan sent = createRakitan(createBranch());
		setPendingKey(waiting.id());
		UUID rotation = rotate(waiting.id(), Instant.now());
		UUID network = network(sent.id(), Instant.now());
		tx().executeWithoutResult(s -> commands.findById(network).orElseThrow().markSent(Instant.now()));

		mockMvc.perform(post("/api/device-commands/" + rotation + "/cancel").with(HR).with(csrf()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("Hanya Owner yang bisa membatalkan perintah ini"));
		mockMvc.perform(post("/api/device-commands/" + rotation + "/cancel").with(OWNER).with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DIBATALKAN"))
			.andExpect(jsonPath("$.message").value("Dibatalkan."));
		assertThat(pendingKeyHash(waiting.id())).isNull();
		mockMvc.perform(post("/api/device-commands/" + network + "/cancel").with(OWNER).with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Perintah sudah dikirim ke mesin dan tidak bisa dibatalkan."));
	}

	@Test
	void failureDetailShownInMessage() throws Exception {
		Rakitan rakitan = onlineRakitan(createBranch());
		UUID id = network(rakitan.id(), Instant.now());
		heartbeat(rakitan.key(), "{}").andExpect(status().isOk());

		result(rakitan.key(), id.toString(), "{\"status\":\"GAGAL\",\"error\":\"WIFI_GAGAL\",\"detail\":\"Kantor-5G\"}")
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/device-commands/" + id).with(OWNER))
			.andExpect(jsonPath("$.message").value("Gagal tersambung ke Wi-Fi 'Kantor-5G', kembali ke pengaturan lama."));
	}

	private UUID network(long deviceId, Instant at) {
		Long config = jdbc.queryForObject("insert into device_network_configs (device_id, server_url, created_by,"
				+ " created_at) values (?, 'http://10.0.0.5:8888', 'owner', now()) returning id", Long.class, deviceId);
		return tx().execute(s -> {
			DeviceCommand command = DeviceCommand.applyNetwork(devices.findById(deviceId).orElseThrow(), config,
					"owner", at);
			service.createRisky(command, "NETWORK_REQUEST", "owner");
			return command.getId();
		});
	}

	private UUID rotate(long deviceId, Instant at) {
		return tx().execute(s -> {
			DeviceCommand command = DeviceCommand.rotateKey(devices.findById(deviceId).orElseThrow(), "owner", at);
			service.createRisky(command, "ROTATE_KEY_REQUEST", "owner");
			return command.getId();
		});
	}

	private void setPendingKey(long deviceId) {
		jdbc.update("update devices set pending_key_hash = ?, pending_key_enc = 'v1:x' where id = ?",
				UUID.randomUUID().toString().replace("-", ""), deviceId);
	}

	private String pendingKeyHash(long deviceId) {
		return jdbc.queryForObject("select pending_key_hash from devices where id = ?", String.class, deviceId);
	}

	private String errorCode(UUID id) {
		return jdbc.queryForObject("select error_code from device_commands where id = ?", String.class, id);
	}

	private TransactionTemplate tx() {
		return new TransactionTemplate(transactionManager);
	}

}
