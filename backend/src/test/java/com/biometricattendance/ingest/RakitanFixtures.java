package com.biometricattendance.ingest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

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

	/**
	 * Akun Owner yang benar-benar ada (dibuat bootstrap di {@link com.biometricattendance.IntegrationTest}), untuk
	 * tindakan yang meminta konfirmasi ulang password (K-098). Principal uji lain tidak punya akun.
	 */
	protected static final RequestPostProcessor REAL_OWNER = user("owner").roles("OWNER");

	protected static final String OWNER_PASSWORD = "owner-awal-123";

	protected static final String CONFIRM = "{\"confirmPassword\":\"" + OWNER_PASSWORD + "\"}";

	/** "Buat kode baru"; mengembalikan kunci baru. */
	protected String issueNewKey(long deviceId) throws Exception {
		String body = mockMvc.perform(post("/api/devices/" + deviceId + "/key").with(REAL_OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(CONFIRM))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.issuedKey.deviceKey");
	}

	/** Alat rakitan yang sudah sekali heartbeat sehingga Online. */
	protected Rakitan onlineRakitan(long branchId) throws Exception {
		Rakitan rakitan = createRakitan(branchId);
		heartbeat(rakitan.key(), "{}").andExpect(status().isOk());
		return rakitan;
	}

	/** Menonaktifkan alat rakitan lewat API, seperti yang dilakukan Owner. */
	protected void deactivate(Rakitan rakitan, long branchId) throws Exception {
		setActive(rakitan, branchId, false);
	}

	protected void setActive(Rakitan rakitan, long branchId, boolean active) throws Exception {
		mockMvc.perform(put("/api/devices/" + rakitan.id()).with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + rakitan.name() + "\",\"branchId\":" + branchId + ",\"active\":" + active
					+ "}"))
			.andExpect(status().isOk());
	}

	/** Karyawan aktif dengan PIN mesin dan persetujuan sidik jari (K-091). */
	protected long readyEmployee(long branchId) throws Exception {
		long id = createEmployee(branchId, unique("P"));
		consent(id).andExpect(status().isOk());
		return id;
	}

	protected ResultActions consent(long employeeId) throws Exception {
		return mockMvc.perform(post("/api/employees/" + employeeId + "/fingerprint-consent").with(OWNER).with(csrf()));
	}

	protected ResultActions enroll(long employeeId, long deviceId, int finger) throws Exception {
		return mockMvc.perform(post("/api/employees/" + employeeId + "/fingerprints/enroll").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"deviceId\":" + deviceId + ",\"finger\":" + finger + "}"));
	}

	/** Mulai pendaftaran yang pasti berhasil dibuat; mengembalikan ID perintah. */
	protected String enrollOk(long employeeId, long deviceId, int finger) throws Exception {
		String body = enroll(employeeId, deviceId, finger).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	/** Simpan ulang karyawan lewat API dengan PIN yang sama, mengubah status aktif dan tanggal keluar saja. */
	protected ResultActions saveEmployee(long employeeId, String endDate, boolean active) throws Exception {
		Map<String, Object> row = jdbc.queryForMap(
				"select employee_number, branch_id, device_pin from employees where id = ?", employeeId);
		return saveEmployee(employeeId, (String) row.get("device_pin"), endDate, active);
	}

	/** Seperti {@link #saveEmployee(long, String, boolean)} dengan PIN pilihan (null = kosongkan). */
	protected ResultActions saveEmployee(long employeeId, String pin, String endDate, boolean active)
			throws Exception {
		Map<String, Object> row = jdbc.queryForMap("select employee_number, branch_id from employees where id = ?",
				employeeId);
		String json = employeeJson(((Number) row.get("branch_id")).longValue(), (String) row.get("employee_number"),
				pin).replace("\"active\":true", "\"active\":" + active
						+ (endDate == null ? "" : ",\"endDate\":\"" + endDate + "\""));
		return mockMvc.perform(put("/api/employees/" + employeeId).with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	/** Jari yang terdaftar penuh: ENROLL dibuat, diambil mesin, lalu dilaporkan BERHASIL. */
	protected void registerFinger(long employeeId, Rakitan rakitan, int finger) throws Exception {
		String id = enrollOk(employeeId, rakitan.id(), finger);
		heartbeat(rakitan.key(), "{}").andExpect(status().isOk());
		result(rakitan.key(), id, "{\"status\":\"BERHASIL\"}").andExpect(status().isOk());
	}

	protected ResultActions deleteFinger(long employeeId, int finger) throws Exception {
		return mockMvc.perform(delete("/api/employees/" + employeeId + "/fingerprints/" + finger).with(HR).with(csrf()));
	}

	/** ID perintah hapus terbaru untuk karyawan di mesin ini, atau null. */
	protected String deleteCommandFor(long employeeId, long deviceId) {
		return jdbc.query("select id::text from device_commands where employee_id = ? and device_id = ?"
				+ " and type in ('DELETE_TEMPLATE', 'DELETE_EMPLOYEE') order by created_at desc limit 1",
				(rs, n) -> rs.getString(1), employeeId, deviceId).stream().findFirst().orElse(null);
	}

	protected String commandStatus(String commandId) {
		return jdbc.queryForObject("select status from device_commands where id = ?::uuid", String.class, commandId);
	}

	protected ResultActions heartbeat(String key, String json) throws Exception {
		return mockMvc.perform(device("/device/v1/heartbeat", key).content(json));
	}

	protected ResultActions postScans(String key, String json) throws Exception {
		return mockMvc.perform(device("/device/v1/scans", key).content(json));
	}

	protected ResultActions progress(String key, String commandId, int step) throws Exception {
		return mockMvc.perform(device("/device/v1/commands/" + commandId + "/progress", key)
			.content("{\"step\":" + step + "}"));
	}

	protected ResultActions result(String key, String commandId, String json) throws Exception {
		return mockMvc.perform(device("/device/v1/commands/" + commandId + "/result", key).content(json));
	}

	private static MockHttpServletRequestBuilder device(String url, String key) {
		MockHttpServletRequestBuilder request = post(url).contentType(MediaType.APPLICATION_JSON);
		return key == null ? request : request.header("Authorization", "Bearer " + key);
	}

}
