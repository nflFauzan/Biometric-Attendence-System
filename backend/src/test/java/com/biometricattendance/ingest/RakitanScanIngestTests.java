package com.biometricattendance.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Penerimaan scan alat rakitan (docs/protokol-alat-rakitan.md §5): balasan hanya setelah tersimpan, ID scan
 * mencegah duplikat, satu scan rusak tidak menahan scan lain, dan batas 200 scan per kiriman.
 */
class RakitanScanIngestTests extends RakitanFixtures {

	@Test
	void scansAreSavedAndAcknowledged() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		long employee = createEmployee(branch, pin);
		Rakitan rakitan = createRakitan(branch);
		String id1 = uuid();
		String id2 = uuid();

		postScans(rakitan.key(), batch(scan(id1, pin, "2026-10-17T08:01:02+07:00"),
				scan(id2, pin, "2026-10-17T17:00:00+07:00")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accepted", Matchers.contains(id1, id2)))
			.andExpect(jsonPath("$.saved").value(2))
			.andExpect(jsonPath("$.duplicates").value(0));

		List<Map<String, Object>> rows = jdbc.queryForList(
				"select employee_id, scanned_at, device_scan_id, raw_line, flags from attendance_scans "
						+ "where device_id = ? order by scanned_at", rakitan.id());
		assertThat(rows).hasSize(2);
		assertThat(((Number) rows.get(0).get("employee_id")).longValue()).isEqualTo(employee);
		assertThat(((Timestamp) rows.get(0).get("scanned_at")).toInstant())
			.isEqualTo(Instant.parse("2026-10-17T01:01:02Z"));
		assertThat(rows.get(0).get("device_scan_id")).isEqualTo(UUID.fromString(id1));
		assertThat((String) rows.get(0).get("raw_line")).contains(id1);
		assertThat(rows.get(0).get("flags")).isNull();
	}

	@Test
	void resendWithSameIdsIsStoredOnce() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		createEmployee(branch, pin);
		Rakitan rakitan = createRakitan(branch);
		String id1 = uuid();
		String id2 = uuid();
		String body = batch(scan(id1, pin, "2026-10-17T08:00:00+07:00"), scan(id2, pin, "2026-10-17T17:00:00+07:00"));

		postScans(rakitan.key(), body).andExpect(status().isOk());
		postScans(rakitan.key(), body).andExpect(status().isOk())
			.andExpect(jsonPath("$.accepted", Matchers.contains(id1, id2)))
			.andExpect(jsonPath("$.saved").value(0))
			.andExpect(jsonPath("$.duplicates").value(2));

		assertThat(scanCount(rakitan.id())).isEqualTo(2);
	}

	/** Jam mesin disetel ulang di tengah antrean: waktu berubah, ID tetap sama → tetap satu baris. */
	@Test
	void sameIdWithDifferentTimeIsStillOneRow() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		createEmployee(branch, pin);
		Rakitan rakitan = createRakitan(branch);
		String id = uuid();

		postScans(rakitan.key(), batch(scan(id, pin, "2026-10-17T08:00:00+07:00"))).andExpect(status().isOk());
		postScans(rakitan.key(), batch(scan(id, pin, "2026-10-17T08:05:00+07:00"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.duplicates").value(1));

		assertThat(scanCount(rakitan.id())).isEqualTo(1);
	}

	@Test
	void oneBadScanDoesNotBlockOthers() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		createEmployee(branch, pin);
		Rakitan rakitan = createRakitan(branch);
		String id1 = uuid();
		String bad = uuid();
		String id3 = uuid();

		postScans(rakitan.key(), batch(scan(id1, pin, "2026-10-17T08:00:00+07:00"),
				scan(bad, pin, "2026-10-17T12:00:00"), scan(id3, pin, "2026-10-17T17:00:00+07:00")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accepted", Matchers.containsInAnyOrder(id1, bad, id3)))
			.andExpect(jsonPath("$.saved").value(2))
			.andExpect(jsonPath("$.rejected").value(1));

		assertThat(scanCount(rakitan.id())).isEqualTo(2);
		assertThat(jdbc.queryForList("select reason from rejected_scan_lines where device_id = ?", String.class,
				rakitan.id()))
			.containsExactly("Waktu harus ISO-8601 dengan offset");
	}

	@Test
	void scansBeforeAcceptFromAreIgnoredButAccepted() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		createEmployee(branch, pin);
		Rakitan rakitan = createRakitan(branch);
		mockMvc.perform(put("/api/devices/" + rakitan.id()).with(OWNER).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + rakitan.name() + "\",\"branchId\":" + branch
					+ ",\"active\":true,\"acceptScansFrom\":\"2026-10-01T00:00:00Z\"}"))
			.andExpect(status().isOk());
		String id = uuid();

		postScans(rakitan.key(), batch(scan(id, pin, "2026-09-30T08:00:00+07:00"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.accepted", Matchers.contains(id)))
			.andExpect(jsonPath("$.ignored").value(1))
			.andExpect(jsonPath("$.saved").value(0));
		assertThat(scanCount(rakitan.id())).isZero();
	}

	@Test
	void unknownPinIsSavedWithFlag() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());

		postScans(rakitan.key(), batch(scan(uuid(), uniquePin(), "2026-10-17T08:00:00+07:00")))
			.andExpect(status().isOk());

		assertThat(jdbc.queryForObject("select flags from attendance_scans where device_id = ?", String.class,
				rakitan.id()))
			.isEqualTo("PIN_TIDAK_DIKENAL");
	}

	@Test
	void moreThan200ScansIs413() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());
		String pin = uniquePin();
		List<String> scans = new ArrayList<>();
		for (int i = 0; i < 201; i++) {
			scans.add(scan(uuid(), pin, "2026-10-17T08:00:00+07:00"));
		}

		postScans(rakitan.key(), batch(scans.toArray(String[]::new))).andExpect(status().isContentTooLarge())
			.andExpect(content().json("{\"error\":\"BATCH_TERLALU_BESAR\"}"));
		assertThat(scanCount(rakitan.id())).isZero();
	}

	@Test
	void missingScansArrayIs400() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());
		postScans(rakitan.key(), "{}").andExpect(status().isBadRequest())
			.andExpect(content().json("{\"error\":\"FORMAT_SALAH\"}"));
	}

	@Test
	void wrongKeyAndInactiveDeviceSaveNothing() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		Rakitan rakitan = createRakitan(branch);
		String body = batch(scan(uuid(), pin, "2026-10-17T08:00:00+07:00"));

		postScans("salah", body).andExpect(status().isUnauthorized())
			.andExpect(content().json("{\"error\":\"KUNCI_DITOLAK\"}"));
		deactivate(rakitan, branch);
		postScans(rakitan.key(), body).andExpect(status().isForbidden())
			.andExpect(content().json("{\"error\":\"MESIN_NONAKTIF\"}"));

		assertThat(scanCount(rakitan.id())).isZero();
	}

	@Test
	void emptyBatchIsOk() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());
		postScans(rakitan.key(), "{\"scans\":[]}").andExpect(status().isOk())
			.andExpect(content().json(
					"{\"accepted\":[],\"saved\":0,\"duplicates\":0,\"ignored\":0,\"rejected\":0}"));
	}

	private static String uuid() {
		return UUID.randomUUID().toString();
	}

	private static String scan(String id, String pin, String time) {
		return "{\"id\":\"" + id + "\",\"pin\":\"" + pin + "\",\"time\":\"" + time + "\",\"verify\":\"FP\"}";
	}

	private static String batch(String... scans) {
		return "{\"scans\":[" + String.join(",", scans) + "]}";
	}

}
