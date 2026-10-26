package com.biometricattendance.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.biometricattendance.AttendanceFixtures;

/**
 * K-103: scan di cabang yang tidak diizinkan tertahan sampai ditinjau (CB-03, CB-04, CB-05, CB-13, CB-14).
 * Karyawan ditempatkan di cabang A, mesin ada di cabang B (tidak diizinkan).
 */
class HeldScanTests extends AttendanceFixtures {

	private static final String DATE = "2026-10-05";

	private record Setup(long home, long other, long employee, String pin, String sn, long device) {
	}

	private Setup setup() throws Exception {
		long home = createBranch();
		long other = createBranch();
		String pin = uniquePin();
		long employee = createEmployee(home, pin);
		addFullRule(employee);
		String sn = uniqueSerial();
		long device = createDevice(other, sn, ACCEPT_FROM);
		return new Setup(home, other, employee, pin, sn, device);
	}

	private void postHeldDay(Setup s) throws Exception {
		postAttlog(s.sn(), lines(line(s.pin(), DATE + " 08:00:00"), line(s.pin(), DATE + " 17:00:00")))
			.andExpect(status().isOk());
	}

	private List<Long> scanIds(Setup s) {
		return jdbc.queryForList("select id from attendance_scans where device_id = ? order by scanned_at",
				Long.class, s.device());
	}

	private ResultActions daily(Setup s) throws Exception {
		return mockMvc.perform(get("/api/attendance/daily").param("date", DATE).param("branchId", "" + s.home()).with(HR));
	}

	private ResultActions review(List<Long> ids, String decision, String reason) throws Exception {
		String reasonJson = reason == null ? "" : ",\"reason\":\"" + reason + "\"";
		return mockMvc.perform(post("/api/attendance/held-scans/review").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"scanIds\":" + ids + ",\"decision\":\"" + decision + "\"" + reasonJson + "}"));
	}

	/** CB-03 */
	@Test
	void heldScanIsNotCountedInRecap() throws Exception {
		Setup s = setup();
		postHeldDay(s);

		daily(s).andExpect(status().isOk())
			.andExpect(jsonPath("$.rows[0].day.result.status").value("TIDAK_HADIR"))
			.andExpect(jsonPath("$.rows[0].day.result.flags", hasItem("ADA_SCAN_TERTAHAN")))
			.andExpect(jsonPath("$.rows[0].day.scans", hasSize(2)))
			.andExpect(jsonPath("$.rows[0].day.scans[0].review").value("BELUM_DITINJAU"))
			.andExpect(jsonPath("$.rows[0].day.scans[0].counted").value(false));
	}

	/** CB-04 */
	@Test
	void acceptedScanCountsAndRawScanUnchanged() throws Exception {
		Setup s = setup();
		postHeldDay(s);
		List<Long> ids = scanIds(s);
		List<Map<String, Object>> before = jdbc
			.queryForList("select flags, scanned_at from attendance_scans where device_id = ? order by scanned_at", s.device());

		review(ids, "DITERIMA", "  Dipinjamkan ke cabang lain  ").andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].status").value("DITERIMA"))
			.andExpect(jsonPath("$[0].reason").value("Dipinjamkan ke cabang lain"));

		daily(s).andExpect(jsonPath("$.rows[0].day.result.status").value("LENGKAP"))
			.andExpect(jsonPath("$.rows[0].day.scans[0].review").value("DITERIMA"))
			.andExpect(jsonPath("$.rows[0].day.scans[0].counted").value(true));
		assertThat(jdbc.queryForList(
				"select flags, scanned_at from attendance_scans where device_id = ? order by scanned_at", s.device()))
			.isEqualTo(before);
		assertThat(jdbc.queryForObject("""
				select count(*) from audit_logs where action = 'SCAN_REVIEW' and entity_id in (?, ?)
				""", Long.class, "" + ids.get(0), "" + ids.get(1))).isEqualTo(2);
	}

	/** CB-05 */
	@Test
	void decisionCanBeChanged() throws Exception {
		Setup s = setup();
		postHeldDay(s);
		List<Long> ids = scanIds(s);
		review(ids, "DITERIMA", "Awalnya diterima").andExpect(status().isOk());
		review(ids, "DIABAIKAN", "Ternyata salah").andExpect(status().isOk())
			.andExpect(jsonPath("$[0].status").value("DIABAIKAN"));

		daily(s).andExpect(jsonPath("$.rows[0].day.result.status").value("TIDAK_HADIR"))
			.andExpect(jsonPath("$.rows[0].day.result.flags").value(org.hamcrest.Matchers.not(hasItem("ADA_SCAN_TERTAHAN"))))
			.andExpect(jsonPath("$.rows[0].day.scans[0].review").value("DIABAIKAN"))
			.andExpect(jsonPath("$.rows[0].day.scans[0].counted").value(false));
		String beforeData = jdbc.queryForObject("""
				select before_data from audit_logs where action = 'SCAN_REVIEW' and entity_id = ?
				  and after_data like '%DIABAIKAN%'
				""", String.class, "" + ids.get(0));
		assertThat(beforeData).contains("\"decision\":\"DITERIMA\"");
	}

	@Test
	void reviewRequiresReason() throws Exception {
		Setup s = setup();
		postHeldDay(s);
		List<Long> ids = scanIds(s);

		for (String reason : new String[] { null, "", "   " }) {
			review(ids, "DITERIMA", reason).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Alasan wajib diisi."));
		}
		assertThat(jdbc.queryForObject("select count(*) from scan_reviews where scan_id = ?", Long.class, ids.get(0)))
			.isZero();
	}

	/** CB-13 dan Review Focus 5: satu scan tak tertahan menggagalkan seluruh batch. */
	@Test
	void batchWithUnheldScanIsRejectedWhole() throws Exception {
		Setup s = setup();
		postHeldDay(s);
		String homeSn = uniqueSerial();
		long homeDevice = createDevice(s.home(), homeSn, ACCEPT_FROM);
		postAttlog(homeSn, lines(line(s.pin(), DATE + " 09:00:00"))).andExpect(status().isOk());
		long unheld = jdbc.queryForObject("select id from attendance_scans where device_id = ?", Long.class, homeDevice);
		List<Long> ids = new ArrayList<>(scanIds(s));
		ids.add(unheld);

		review(ids, "DITERIMA", "Uji").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Scan ini tidak tertahan karena cabang."));
		assertThat(jdbc.queryForObject("select count(*) from scan_reviews where scan_id in (?, ?, ?)", Long.class,
				ids.get(0), ids.get(1), ids.get(2))).isZero();
		assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'SCAN_REVIEW' and entity_id = ?",
				Long.class, "" + ids.get(0))).isZero();
	}

	@Test
	void unknownScanIsNotFound() throws Exception {
		review(List.of(999_999_999L), "DITERIMA", "Uji").andExpect(status().isNotFound());
	}

	/** CB-14 */
	@Test
	void concurrentReviewOneWins() throws Exception {
		Setup s = setup();
		postHeldDay(s);
		List<Long> ids = scanIds(s).subList(0, 1);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (String decision : new String[] { "DITERIMA", "DIABAIKAN" }) {
				Callable<Integer> task = () -> {
					go.await();
					return review(ids, decision, "Bersamaan").andReturn().getResponse().getStatus();
				};
				results.add(pool.submit(task));
			}
			go.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> f : results) {
				statuses.add(f.get());
			}
			assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(jdbc.queryForObject("select count(*) from scan_reviews where scan_id = ?", Long.class, ids.get(0)))
			.isEqualTo(1);
	}

	@Test
	void activeCorrectionStillWins() throws Exception {
		Setup s = setup();
		postHeldDay(s);
		mockMvc.perform(post("/api/attendance/corrections").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"employeeId\":" + s.employee() + ",\"date\":\"" + DATE
					+ "\",\"checkIn\":\"08:30\",\"checkOut\":\"16:30\",\"reason\":\"Koreksi uji\"}"))
			.andExpect(status().isCreated());

		daily(s).andExpect(jsonPath("$.rows[0].day.result.status").value("LENGKAP"))
			.andExpect(jsonPath("$.rows[0].day.result.checkIn.source").value("KOREKSI"))
			.andExpect(jsonPath("$.rows[0].day.result.flags", hasItem("ADA_SCAN_TERTAHAN")));
	}

	@Test
	void listFiltersByStatus() throws Exception {
		Setup s = setup();
		postHeldDay(s);
		List<Long> ids = scanIds(s);
		review(ids.subList(0, 1), "DIABAIKAN", "Bukan karyawan ini").andExpect(status().isOk());
		String id0 = "" + ids.get(0);
		String id1 = "" + ids.get(1);

		mockMvc.perform(get("/api/attendance/held-scans").param("from", DATE).param("to", DATE).with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.scan.id == " + id0 + ")]", hasSize(1)))
			.andExpect(jsonPath("$[?(@.scan.id == " + id1 + ")]", hasSize(1)));
		mockMvc.perform(get("/api/attendance/held-scans").param("from", DATE).param("to", DATE)
			.param("status", "BELUM_DITINJAU").with(HR))
			.andExpect(jsonPath("$[?(@.scan.id == " + id0 + ")]", hasSize(0)))
			.andExpect(jsonPath("$[?(@.scan.id == " + id1 + ")].status").value(hasItem("BELUM_DITINJAU")))
			.andExpect(jsonPath("$[?(@.scan.id == " + id1 + ")].deviceBranchName").isNotEmpty())
			.andExpect(jsonPath("$[?(@.scan.id == " + id1 + ")].employeeName").isNotEmpty());
		mockMvc.perform(get("/api/attendance/held-scans").param("from", DATE).param("to", DATE)
			.param("status", "DIABAIKAN").with(HR))
			.andExpect(jsonPath("$[?(@.scan.id == " + id0 + ")].reason").value(hasItem("Bukan karyawan ini")))
			.andExpect(jsonPath("$[?(@.scan.id == " + id1 + ")]", hasSize(0)));
	}

	@Test
	void dashboardCountsPending() throws Exception {
		Setup s = setup();
		postHeldDay(s);
		long pendingBefore = ((Number) com.jayway.jsonpath.JsonPath.read(mockMvc
			.perform(get("/api/dashboard/summary").param("date", DATE).with(HR)).andReturn().getResponse()
			.getContentAsString(), "$.heldScans")).longValue();
		review(scanIds(s).subList(0, 1), "DIABAIKAN", "Uji").andExpect(status().isOk());

		long pendingAfter = ((Number) com.jayway.jsonpath.JsonPath.read(mockMvc
			.perform(get("/api/dashboard/summary").param("date", DATE).with(HR)).andReturn().getResponse()
			.getContentAsString(), "$.heldScans")).longValue();
		assertThat(pendingBefore).isGreaterThanOrEqualTo(2);
		assertThat(pendingAfter).isEqualTo(pendingBefore - 1);
	}

	private String counts(Setup s) throws Exception {
		return mockMvc.perform(get("/api/dashboard/summary").param("date", DATE).with(HR)).andReturn().getResponse()
			.getContentAsString();
	}

	private int homeCount(Setup s, String field) throws Exception {
		List<Number> values = com.jayway.jsonpath.JsonPath.read(counts(s),
				"$.branches[?(@.branchId == " + s.home() + ")].counts." + field);
		return values.get(0).intValue();
	}

	/** Scan tertahan yang belum ditinjau dihitung perlu ditinjau; yang sudah diputuskan tidak. */
	@Test
	void heldScanNeedsReviewOnlyUntilDecided() throws Exception {
		for (String decision : new String[] { "DITERIMA", "DIABAIKAN" }) {
			Setup s = setup();
			postHeldDay(s);
			assertThat(homeCount(s, "needsReview")).isEqualTo(1);

			review(scanIds(s), decision, "Uji").andExpect(status().isOk());
			assertThat(homeCount(s, "needsReview")).as(decision).isZero();
		}
	}

	/** CABANG_BERBEDA hanya informasi (spec 2.2): tidak membuat hari perlu ditinjau. */
	@Test
	void additionalBranchScanDoesNotNeedReview() throws Exception {
		Setup s = setup();
		mockMvc.perform(put("/api/employees/" + s.employee() + "/branches").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"extraBranchIds\":[" + s.other() + "]}")).andExpect(status().isOk());
		postHeldDay(s);

		daily(s).andExpect(jsonPath("$.rows[0].day.result.status").value("LENGKAP"))
			.andExpect(jsonPath("$.rows[0].day.scanFlags", hasItem("CABANG_BERBEDA")));
		assertThat(homeCount(s, "needsReview")).isZero();
	}

	/** Tanpa aturan kerja, scan tertahan bukan kehadiran. */
	@Test
	void heldScanWithoutRuleIsNotPresence() throws Exception {
		long home = createBranch();
		long other = createBranch();
		String pin = uniquePin();
		createEmployee(home, pin);
		String sn = uniqueSerial();
		createDevice(other, sn, ACCEPT_FROM);
		postAttlog(sn, lines(line(pin, DATE + " 08:00:00"))).andExpect(status().isOk());

		List<Number> present = com.jayway.jsonpath.JsonPath.read(mockMvc
			.perform(get("/api/dashboard/summary").param("date", DATE).with(HR)).andReturn().getResponse()
			.getContentAsString(), "$.branches[?(@.branchId == " + home + ")].counts.present");
		assertThat(present.get(0).intValue()).isZero();
	}

	@Test
	void nullScanIdIsBadRequest() throws Exception {
		mockMvc.perform(post("/api/attendance/held-scans/review").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"scanIds\":[null],\"decision\":\"DITERIMA\",\"reason\":\"Uji\"}"))
			.andExpect(status().isBadRequest());
	}

}
