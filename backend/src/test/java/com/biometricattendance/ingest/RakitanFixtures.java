package com.biometricattendance.ingest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.biometricattendance.AttendanceFixtures;
import com.jayway.jsonpath.JsonPath;

/** Data uji untuk alat rakitan (`/device/v1`, docs/protokol-alat-rakitan.md). */
public abstract class RakitanFixtures extends AttendanceFixtures {

	protected record Rakitan(long id, String deviceId, String key, String name) {
	}

	/** Alat rakitan aktif yang menerima scan sejak {@link #ACCEPT_FROM}. */
	protected Rakitan createRakitan(long branchId) throws Exception {
		String name = "Rakitan " + unique("");
		String body = mockMvc.perform(post("/api/devices").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + name + "\",\"branchId\":" + branchId + ",\"active\":true,\"acceptScansFrom\":\""
					+ ACCEPT_FROM + "\",\"protocol\":\"RAKITAN_V1\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return new Rakitan(((Number) JsonPath.read(body, "$.id")).longValue(), JsonPath.read(body, "$.serialNumber"),
				JsonPath.read(body, "$.issuedKey.deviceKey"), name);
	}

	/** "Buat kode baru"; mengembalikan kunci baru. */
	protected String issueNewKey(long deviceId) throws Exception {
		String body = mockMvc.perform(post("/api/devices/" + deviceId + "/key").with(OWNER).with(csrf()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.issuedKey.deviceKey");
	}

	protected ResultActions heartbeat(String key, String json) throws Exception {
		return mockMvc.perform(device("/device/v1/heartbeat", key).content(json));
	}

	protected ResultActions postScans(String key, String json) throws Exception {
		return mockMvc.perform(device("/device/v1/scans", key).content(json));
	}

	private static MockHttpServletRequestBuilder device(String url, String key) {
		MockHttpServletRequestBuilder request = post(url).contentType(MediaType.APPLICATION_JSON);
		return key == null ? request : request.header("Authorization", "Bearer " + key);
	}

}
