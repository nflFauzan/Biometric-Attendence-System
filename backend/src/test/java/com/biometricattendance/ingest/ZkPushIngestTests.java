package com.biometricattendance.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.biometricattendance.AttendanceFixtures;

/**
 * Penerimaan scan lewat adapter ZK-Push, dengan format kiriman seperti tangkapan X401. Membuktikan: OK hanya
 * setelah tersimpan, tidak ada scan ganda (termasuk kirim ulang dan request bersamaan), mesin tak terdaftar
 * ditolak, riwayat lama diabaikan (K-057), dan baris rusak tidak hilang (K-062).
 */
class ZkPushIngestTests extends AttendanceFixtures {

	@Test
	void handshakeReturnsOptionsForRegisteredDeviceOnly() throws Exception {
		long branch = createBranch();
		String sn = uniqueSerial();
		createDevice(branch, sn, ACCEPT_FROM);

		mockMvc.perform(get("/iclock/cdata").param("SN", sn).param("options", "all").param("pushver", "2.32"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("GET OPTION FROM: " + sn)))
			.andExpect(content().string(containsString("TransFlag=TransData AttLog")))
			.andExpect(content().string(containsString("TimeZone=7")))
			.andExpect(content().string(containsString("ATTLOGStamp=None")));
	}

	@Test
	void scansAreStoredWithBranchTimezoneAndAcknowledged() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		long employee = createEmployee(branch, pin);
		String sn = uniqueSerial();
		long device = createDevice(branch, sn, ACCEPT_FROM);

		postAttlog(sn, lines(line(pin, "2026-10-05 08:01:02"), line(pin, "2026-10-05 17:00:00")))
			.andExpect(status().isOk())
			.andExpect(content().string("OK"));

		List<Map<String, Object>> rows = jdbc.queryForList(
				"select employee_id, scanned_at, device_local_time, raw_line, flags from attendance_scans "
						+ "where device_id = ? order by scanned_at", device);
		assertThat(rows).hasSize(2);
		assertThat(((Number) rows.get(0).get("employee_id")).longValue()).isEqualTo(employee);
		// 08:01:02 WIB = 01:01:02 UTC
		assertThat(((Timestamp) rows.get(0).get("scanned_at")).toInstant())
			.isEqualTo(Instant.parse("2026-10-05T01:01:02Z"));
		assertThat(rows.get(0).get("raw_line")).isEqualTo(line(pin, "2026-10-05 08:01:02"));
		assertThat(rows.get(0).get("flags")).isNull();
	}

	/** ATT-005: kiriman ulang batch yang sama (mesin tidak menerima OK) tidak menambah baris. */
	@Test
	void resentBatchIsStoredOnce() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		createEmployee(branch, pin);
		String sn = uniqueSerial();
		long device = createDevice(branch, sn, ACCEPT_FROM);
		String body = lines(line(pin, "2026-10-06 08:00:00"), line(pin, "2026-10-06 17:00:00"));

		postAttlog(sn, body).andExpect(content().string("OK"));
		postAttlog(sn, body).andExpect(content().string("OK"));
		postAttlog(sn, lines(line(pin, "2026-10-06 17:00:00"), line(pin, "2026-10-06 18:00:00")))
			.andExpect(content().string("OK"));

		assertThat(scanCount(device)).isEqualTo(3);
	}

	/** Dua request identik yang datang bersamaan tetap menghasilkan satu baris per scan (unique constraint). */
	@Test
	void concurrentIdenticalRequestsStoreEachScanOnce() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		createEmployee(branch, pin);
		String sn = uniqueSerial();
		long device = createDevice(branch, sn, ACCEPT_FROM);
		String body = lines(line(pin, "2026-10-07 08:00:00"), line(pin, "2026-10-07 12:00:00"),
				line(pin, "2026-10-07 17:00:00"));

		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			Callable<Integer> send = () -> postAttlog(sn, body).andReturn().getResponse().getStatus();
			List<Future<Integer>> results = pool.invokeAll(List.of(send, send, send, send));
			for (Future<Integer> result : results) {
				assertThat(result.get()).isEqualTo(200);
			}
		}
		finally {
			pool.shutdown();
		}
		assertThat(scanCount(device)).isEqualTo(3);
	}

	/** X-02: scan yang ditahan mesin selama offline tiba belakangan dengan waktu scan aslinya. */
	@Test
	void scansHeldWhileOfflineKeepTheirOriginalTime() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		createEmployee(branch, pin);
		String sn = uniqueSerial();
		long device = createDevice(branch, sn, ACCEPT_FROM);

		postAttlog(sn, lines(line(pin, "2026-10-08 07:55:00"), line(pin, "2026-10-08 12:00:00"),
				line(pin, "2026-10-08 13:00:00"))).andExpect(content().string("OK"));

		List<Map<String, Object>> rows = jdbc.queryForList(
				"select scanned_at, received_at from attendance_scans where device_id = ? order by scanned_at", device);
		assertThat(rows).hasSize(3);
		assertThat(((Timestamp) rows.get(0).get("scanned_at")).toInstant())
			.isEqualTo(Instant.parse("2026-10-08T00:55:00Z"));
		assertThat(((Timestamp) rows.get(0).get("received_at")).toInstant())
			.isAfter(((Timestamp) rows.get(2).get("scanned_at")).toInstant());
	}

	/** K-067: mesin tidak terdaftar tidak mendapat OK (scan tetap di mesin) dan nomor serinya ditampilkan. */
	@Test
	void unregisteredDeviceIsRejectedAndListedForRegistration() throws Exception {
		String sn = uniqueSerial();
		String pin = uniquePin();
		postAttlog(sn, lines(line(pin, "2026-10-05 08:00:00"))).andExpect(status().isUnauthorized())
			.andExpect(content().string("UNKNOWN DEVICE"));
		mockMvc.perform(get("/iclock/getrequest").param("SN", sn)).andExpect(status().isUnauthorized());

		assertThat(jdbc.queryForObject("select count(*) from attendance_scans where device_pin = ?", Long.class, pin))
			.isZero();
		mockMvc.perform(get("/api/devices/unregistered-attempts").with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].serialNumber", hasItem(sn)));
	}

	@Test
	void inactiveDeviceIsRejected() throws Exception {
		long branch = createBranch();
		String sn = uniqueSerial();
		long device = createDevice(branch, sn, ACCEPT_FROM);
		jdbc.update("update devices set active = false where id = ?", device);

		postAttlog(sn, lines(line(uniquePin(), "2026-10-05 08:00:00"))).andExpect(status().isUnauthorized());
		assertThat(scanCount(device)).isZero();
	}

	/** K-057: riwayat sebelum "Terima scan sejak" dibalas OK, tidak disimpan, dan dihitung. */
	@Test
	void historyBeforeAcceptDateIsAcknowledgedButNotStored() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		createEmployee(branch, pin);
		String sn = uniqueSerial();
		long device = createDevice(branch, sn, Instant.parse("2026-10-01T00:00:00Z"));

		postAttlog(sn, lines(line(uniquePin(), "2020-01-10 12:25:42"), line(pin, "2026-09-30 08:00:00"),
				line(pin, "2026-10-05 08:00:00"))).andExpect(content().string("OK"));

		assertThat(scanCount(device)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select ignored_scan_count from devices where id = ?", Long.class, device))
			.isEqualTo(2);
	}

	/** K-062: baris rusak disimpan apa adanya; baris valid di kiriman yang sama tetap tersimpan dan di-ACK. */
	@Test
	void brokenLineIsKeptAndValidLinesInSameBatchAreStored() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		createEmployee(branch, pin);
		String sn = uniqueSerial();
		long device = createDevice(branch, sn, ACCEPT_FROM);

		postAttlog(sn, lines("baris-rusak", line(pin, "2026-10-05 08:00:00"))).andExpect(content().string("OK"));

		assertThat(scanCount(device)).isEqualTo(1);
		assertThat(jdbc.queryForList("select raw_line, reason from rejected_scan_lines where device_id = ?", device))
			.singleElement()
			.satisfies(row -> {
				assertThat(row.get("raw_line")).isEqualTo("baris-rusak");
				assertThat(row.get("reason")).isEqualTo("Kolom kurang: butuh PIN dan waktu");
			});
	}

	/** K-065 + K-068: PIN tak dikenal disimpan dengan penanda, lalu terhubung saat PIN diisi di karyawan. */
	@Test
	void unknownPinIsStoredFlaggedAndLinkedWhenPinIsAssigned() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		String sn = uniqueSerial();
		long device = createDevice(branch, sn, ACCEPT_FROM);
		postAttlog(sn, lines(line(pin, "2026-10-05 08:00:00"))).andExpect(content().string("OK"));
		assertThat(jdbc.queryForMap("select employee_id, flags from attendance_scans where device_id = ?", device))
			.containsEntry("employee_id", null)
			.containsEntry("flags", "PIN_TIDAK_DIKENAL");

		String number = unique("E");
		long employee = idOf(mockMvc.perform(post("/api/employees").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(employeeJson(branch, number, null))).andExpect(status().isCreated()));
		updateEmployeePin(employee, branch, number, pin).andExpect(status().isOk())
			.andExpect(jsonPath("$.devicePin").value(pin));

		Map<String, Object> row = jdbc.queryForMap("select employee_id, flags from attendance_scans where device_id = ?",
				device);
		assertThat(((Number) row.get("employee_id")).longValue()).isEqualTo(employee);
		assertThat(row.get("flags")).isNull();
		assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'LINK_SCANS' and entity_id = ?",
				Long.class, String.valueOf(employee))).isEqualTo(1);
	}

	@Test
	void duplicatePinIsRejected() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		createEmployee(branch, pin);
		mockMvc.perform(post("/api/employees").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(employeeJson(branch, unique("E"), pin))).andExpect(status().isConflict());
	}

	@Test
	void futureTimeAndOtherBranchAreFlagged() throws Exception {
		long homeBranch = createBranch();
		long otherBranch = createBranch();
		String pin = uniquePin();
		createEmployee(homeBranch, pin);
		String sn = uniqueSerial();
		long device = createDevice(otherBranch, sn, ACCEPT_FROM);

		postAttlog(sn, lines(line(pin, "2026-10-05 08:00:00"), line(pin, "2099-01-01 08:00:00")))
			.andExpect(content().string("OK"));

		assertThat(jdbc.queryForList("select flags from attendance_scans where device_id = ? order by scanned_at",
				String.class, device))
			.containsExactly("CABANG_BERBEDA", "CABANG_BERBEDA,WAKTU_DI_MASA_DEPAN");
	}

	/** K-063: tabel selain ATTLOG (data user, operasi) dibalas OK tanpa disimpan. */
	@Test
	void otherTablesAreAcknowledgedButNotStored() throws Exception {
		long branch = createBranch();
		String sn = uniqueSerial();
		long device = createDevice(branch, sn, ACCEPT_FROM);

		mockMvc.perform(post("/iclock/cdata").param("SN", sn).param("table", "OPERLOG").content("OPLOG 4\t0\t2026"))
			.andExpect(content().string("OK"));
		assertThat(scanCount(device)).isZero();
	}

	@Test
	void heartbeatMarksDeviceOnlineWithFirmwareAndStampIsReturnedInHandshake() throws Exception {
		long branch = createBranch();
		String sn = uniqueSerial();
		long device = createDevice(branch, sn, ACCEPT_FROM);

		mockMvc.perform(get("/iclock/getrequest").param("SN", sn)
			.param("INFO", "Ver 6.5.4(build 162),183,512,16951,172.35.3.102,10,-1,0,0,101"))
			.andExpect(content().string("OK"));
		mockMvc.perform(post("/iclock/cdata").param("SN", sn).param("table", "ATTLOG").param("Stamp", "860765154")
			.content(line(uniquePin(), "2026-10-05 08:00:00"))).andExpect(content().string("OK"));

		mockMvc.perform(get("/api/devices").with(HR))
			.andExpect(jsonPath("$[?(@.id == " + device + ")].online").value(true))
			.andExpect(jsonPath("$[?(@.id == " + device + ")].firmware").value("Ver 6.5.4(build 162)"));
		mockMvc.perform(get("/iclock/cdata").param("SN", sn))
			.andExpect(content().string(containsString("ATTLOGStamp=860765154")));
	}

	@Test
	void missingSerialNumberIsBadRequest() throws Exception {
		mockMvc.perform(get("/iclock/getrequest")).andExpect(status().isBadRequest());
	}

	@Test
	void oversizedBodyIsRejected() throws Exception {
		long branch = createBranch();
		String sn = uniqueSerial();
		long device = createDevice(branch, sn, ACCEPT_FROM);
		postAttlog(sn, "x".repeat(1024 * 1024 + 1)).andExpect(status().isPayloadTooLarge());
		assertThat(scanCount(device)).isZero();
	}

}
