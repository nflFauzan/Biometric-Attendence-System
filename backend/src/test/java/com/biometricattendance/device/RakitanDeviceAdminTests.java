package com.biometricattendance.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.biometricattendance.AttendanceFixtures;
import com.jayway.jsonpath.JsonPath;

/** Rencana 2: menambah alat rakitan dan "Buat kode baru" dari web (K-088, K-099). */
class RakitanDeviceAdminTests extends AttendanceFixtures {

	@Test
	void ownerCreatesRakitanDeviceAndGetsKeyOnce() throws Exception {
		long branch = createBranch();
		String body = createRakitan(OWNER, branch, "Uji Kampus").andExpect(status().isCreated())
			.andExpect(jsonPath("$.protocol").value("RAKITAN_V1"))
			.andExpect(jsonPath("$.keyIssued").value(true))
			.andExpect(jsonPath("$.keyFirstUsedAt").doesNotExist())
			.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(body, "$.id")).longValue();
		String serial = JsonPath.read(body, "$.serialNumber");
		String key = JsonPath.read(body, "$.issuedKey.deviceKey");
		assertThat(serial).matches("RK-[0-9A-F]{6}");
		assertThat(key).hasSize(43);
		assertThat(keyHash(id)).isEqualTo(DeviceKeys.hash(key));

		String list = mockMvc.perform(get("/api/devices").with(OWNER)).andReturn().getResponse().getContentAsString();
		List<Object> issued = JsonPath.read(list, "$[?(@.id == " + id + ")].issuedKey");
		assertThat(issued).containsExactly((Object) null);
		assertThat(list).doesNotContain(key);
		assertThat(auditData(id)).doesNotContain(key);
	}

	@Test
	void hrFinanceCannotCreateRakitanButCanCreateZkPush() throws Exception {
		long branch = createBranch();
		createRakitan(HR, branch, "Rakitan HR").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("Hanya Owner yang bisa menambah alat rakitan"));

		mockMvc.perform(post("/api/devices").with(HR).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"X401\",\"serialNumber\":\"" + uniqueSerial() + "\",\"branchId\":" + branch
					+ ",\"active\":true}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.protocol").value("ZK_PUSH"))
			.andExpect(jsonPath("$.keyIssued").value(false))
			.andExpect(jsonPath("$.issuedKey").doesNotExist());
	}

	@Test
	void zkPushStillRequiresSerialNumber() throws Exception {
		long branch = createBranch();
		mockMvc.perform(post("/api/devices").with(OWNER).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"X401\",\"serialNumber\":\"\",\"branchId\":" + branch
					+ ",\"active\":true,\"protocol\":\"ZK_PUSH\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Serial number wajib diisi"));
	}

	@Test
	void unknownProtocolIsRejected() throws Exception {
		long branch = createBranch();
		mockMvc.perform(post("/api/devices").with(OWNER).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"X\",\"serialNumber\":\"A1\",\"branchId\":" + branch
					+ ",\"active\":true,\"protocol\":\"LAINNYA\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.protocol").value("Jenis mesin tidak dikenal"));
	}

	@Test
	void protocolAndRakitanIdCannotChange() throws Exception {
		long branch = createBranch();
		String body = createRakitan(OWNER, branch, "Rakitan").andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(body, "$.id")).longValue();
		String serial = JsonPath.read(body, "$.serialNumber");

		update(id, "{\"name\":\"Rakitan\",\"serialNumber\":\"" + serial + "\",\"branchId\":" + branch
				+ ",\"active\":true,\"protocol\":\"ZK_PUSH\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Jenis mesin tidak bisa diubah. Buat mesin baru."));

		update(id, "{\"name\":\"Baru\",\"serialNumber\":\"LAIN-1\",\"branchId\":" + branch + ",\"active\":true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.serialNumber").value(serial))
			.andExpect(jsonPath("$.name").value("Baru"))
			.andExpect(jsonPath("$.protocol").value("RAKITAN_V1"));
	}

	@Test
	void newKeyRevokesOldKeyAndResetsFirstUse() throws Exception {
		long branch = createBranch();
		String body = createRakitan(OWNER, branch, "Rakitan").andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(body, "$.id")).longValue();
		String oldKey = JsonPath.read(body, "$.issuedKey.deviceKey");
		jdbc.update("update devices set key_first_used_at = now() where id = ?", id);

		String issued = mockMvc.perform(post("/api/devices/" + id + "/key").with(OWNER).with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.keyFirstUsedAt").doesNotExist())
			.andReturn().getResponse().getContentAsString();
		String newKey = JsonPath.read(issued, "$.issuedKey.deviceKey");
		assertThat(newKey).hasSize(43).isNotEqualTo(oldKey);
		assertThat(keyHash(id)).isEqualTo(DeviceKeys.hash(newKey));
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_logs where action = 'ISSUE_KEY' and entity_type = 'DEVICE' and entity_id = ?",
				Long.class, String.valueOf(id))).isEqualTo(1);
		assertThat(auditData(id)).doesNotContain(newKey).doesNotContain(oldKey);
	}

	@Test
	void issuingKeyForZkPushIsRejected() throws Exception {
		long id = createDevice(createBranch(), uniqueSerial(), ACCEPT_FROM);
		mockMvc.perform(post("/api/devices/" + id + "/key").with(OWNER).with(csrf()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Mesin ZK-Push tidak memakai kunci"));
	}

	private ResultActions createRakitan(RequestPostProcessor role, long branch, String name) throws Exception {
		return mockMvc.perform(post("/api/devices").with(role).with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + name + "\",\"branchId\":" + branch + ",\"active\":true,\"protocol\":\"RAKITAN_V1\"}"));
	}

	private ResultActions update(long id, String json) throws Exception {
		return mockMvc.perform(put("/api/devices/" + id).with(OWNER).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private String keyHash(long id) {
		return jdbc.queryForObject("select key_hash from devices where id = ?", String.class, id);
	}

	private String auditData(long id) {
		return String.join("\n", jdbc.queryForList(
				"select coalesce(before_data, '') || coalesce(after_data, '') from audit_logs "
						+ "where entity_type = 'DEVICE' and entity_id = ?",
				String.class, String.valueOf(id)));
	}

}
