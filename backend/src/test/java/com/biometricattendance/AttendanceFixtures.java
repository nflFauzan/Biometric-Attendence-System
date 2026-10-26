package com.biometricattendance;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

/**
 * Data uji Fase 3: cabang, karyawan dengan PIN mesin, mesin terdaftar, aturan kerja, dan kiriman scan dengan
 * format protokol push ZKTeco seperti tangkapan X401 (data sintetis).
 */
public abstract class AttendanceFixtures extends IntegrationTest {

	protected static final RequestPostProcessor OWNER = user("owner-absensi").roles("OWNER");
	protected static final RequestPostProcessor HR = user("hr-absensi").roles("HR_FINANCE");
	/** Batas "Terima scan sejak" bawaan uji: semua tanggal uji (Oktober 2026) diterima. */
	protected static final Instant ACCEPT_FROM = Instant.parse("2026-01-01T00:00:00Z");

	@Autowired
	protected JdbcTemplate jdbc;

	protected long createBranch() throws Exception {
		return idOf(mockMvc.perform(post("/api/branches").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"" + unique("C") + "\",\"name\":\"Cabang " + unique("") + "\","
					+ "\"timezone\":\"Asia/Jakarta\",\"active\":true}"))
			.andExpect(status().isCreated()));
	}

	protected long createEmployee(long branchId, String pin) throws Exception {
		return idOf(mockMvc.perform(post("/api/employees").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(employeeJson(branchId, unique("E"), pin)))
			.andExpect(status().isCreated()));
	}

	protected ResultActions updateEmployeePin(long employeeId, long branchId, String number, String pin)
			throws Exception {
		return mockMvc.perform(put("/api/employees/" + employeeId).with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(employeeJson(branchId, number, pin)));
	}

	protected static String employeeJson(long branchId, String number, String pin) {
		return "{\"employeeNumber\":\"" + number + "\",\"fullName\":\"Karyawan " + number + "\",\"branchId\":"
				+ branchId + ",\"categoryId\":1,\"joinDate\":\"2026-01-05\",\"active\":true"
				+ (pin == null ? "" : ",\"devicePin\":\"" + pin + "\"") + "}";
	}

	protected long createDevice(long branchId, String sn, Instant acceptFrom) throws Exception {
		return idOf(mockMvc.perform(post("/api/devices").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Mesin " + sn + "\",\"serialNumber\":\"" + sn + "\",\"branchId\":" + branchId
					+ ",\"active\":true" + (acceptFrom == null ? "" : ",\"acceptScansFrom\":\"" + acceptFrom + "\"")
					+ "}"))
			.andExpect(status().isCreated()));
	}

	/** Aturan lengkap 08.00–17.00, toleransi 0, lembur mulai 17.00, semua hari, berlaku sejak 2020 (lewat SQL agar bisa berlaku surut). */
	protected void addFullRule(long employeeId) {
		jdbc.update("""
				insert into work_rules (scope, employee_id, effective_from, work_start, work_end,
				    late_tolerance_minutes, overtime_start, work_days, created_by)
				values ('EMPLOYEE', ?, date '2020-01-01', time '08:00', time '17:00', 0, time '17:00',
				    'MON,TUE,WED,THU,FRI,SAT,SUN', 'test')
				""", employeeId);
	}

	protected ResultActions postAttlog(String sn, String body) throws Exception {
		return mockMvc.perform(post("/iclock/cdata").param("SN", sn)
			.param("table", "ATTLOG")
			.param("Stamp", "1")
			.contentType(MediaType.TEXT_PLAIN)
			.content(body));
	}

	/** Satu baris ATTLOG: PIN, waktu lokal mesin, status, verifikasi (1 = sidik jari). */
	protected static String line(String pin, String localTime) {
		return pin + "\t" + localTime + "\t0\t1";
	}

	protected static String lines(String... lines) {
		return String.join("\n", lines) + "\n";
	}

	protected static String uniquePin() {
		return String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L));
	}

	protected static String uniqueSerial() {
		return unique("SN").toUpperCase();
	}

	protected long scanCount(long deviceId) {
		return jdbc.queryForObject("select count(*) from attendance_scans where device_id = ?", Long.class, deviceId);
	}

	protected static long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

}
