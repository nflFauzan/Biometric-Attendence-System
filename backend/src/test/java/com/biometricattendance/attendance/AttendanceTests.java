package com.biometricattendance.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.biometricattendance.AttendanceFixtures;

/**
 * Rekap harian dari scan asli (K-058) dan koreksi absensi (K-059, K-066, TC-08). Tanggal uji 2026-10-05
 * (Senin); aturan uji 08.00–17.00, toleransi 0, lembur mulai 17.00.
 */
class AttendanceTests extends AttendanceFixtures {

	private static final String DATE = "2026-10-05";

	private record Setup(long branch, long employee, String pin, String sn, long device) {
	}

	private Setup setup(boolean withRule) throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		long employee = createEmployee(branch, pin);
		if (withRule) {
			addFullRule(employee);
		}
		String sn = uniqueSerial();
		long device = createDevice(branch, sn, ACCEPT_FROM);
		return new Setup(branch, employee, pin, sn, device);
	}

	@Test
	void dailyRecapIsCalculatedFromRawScans() throws Exception {
		Setup s = setup(true);
		postAttlog(s.sn(), lines(line(s.pin(), DATE + " 08:10:00"), line(s.pin(), DATE + " 12:00:00"),
				line(s.pin(), DATE + " 17:30:00")));

		daily(s.branch()).andExpect(status().isOk())
			.andExpect(jsonPath("$.rows", hasSize(1)))
			.andExpect(jsonPath("$.rows[0].devicePin").value(s.pin()))
			.andExpect(jsonPath("$.rows[0].day.result.status").value("LENGKAP"))
			.andExpect(jsonPath("$.rows[0].day.result.checkIn.time").value(startsWith("08:10")))
			.andExpect(jsonPath("$.rows[0].day.result.checkIn.source").value("SCAN"))
			.andExpect(jsonPath("$.rows[0].day.result.checkOut.time").value(startsWith("17:30")))
			.andExpect(jsonPath("$.rows[0].day.result.lateSeconds").value(600))
			.andExpect(jsonPath("$.rows[0].day.result.overtimeCandidateSeconds").value(1800))
			.andExpect(jsonPath("$.rows[0].day.scans", hasSize(3)));
	}

	/** ATT-010: rekap langsung berubah setelah scan baru tiba, tanpa proses hitung ulang terpisah. */
	@Test
	void recapReflectsNewScansImmediately() throws Exception {
		Setup s = setup(true);
		daily(s.branch()).andExpect(jsonPath("$.rows[0].day.result.status").value("TIDAK_HADIR"));
		postAttlog(s.sn(), lines(line(s.pin(), DATE + " 08:00:00")));
		daily(s.branch()).andExpect(jsonPath("$.rows[0].day.result.status").value("TIDAK_LENGKAP"));
	}

	/** K-047: tanpa aturan, rekap tidak gagal tetapi menampilkan aturan yang belum diatur. */
	@Test
	void recapWithoutRuleShowsWhatIsMissing() throws Exception {
		Setup s = setup(false);
		postAttlog(s.sn(), lines(line(s.pin(), DATE + " 08:00:00")));
		daily(s.branch()).andExpect(jsonPath("$.rows[0].day.result").isEmpty())
			.andExpect(jsonPath("$.rows[0].day.ruleMissing").value(startsWith("Aturan belum diatur")))
			.andExpect(jsonPath("$.rows[0].day.scans", hasSize(1)));
	}

	/** Batas hari mengikuti zona waktu cabang: 23.59 dan 00.01 WIB jatuh di dua tanggal berbeda. */
	@Test
	void dayBoundaryFollowsBranchTimezone() throws Exception {
		Setup s = setup(true);
		postAttlog(s.sn(), lines(line(s.pin(), "2026-10-05 23:59:00"), line(s.pin(), "2026-10-06 00:01:00")));

		mockMvc.perform(get("/api/attendance/employees/" + s.employee()).param("from", "2026-10-05")
			.param("to", "2026-10-06")
			.with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.days", hasSize(2)))
			.andExpect(jsonPath("$.days[0].scans", hasSize(1)))
			.andExpect(jsonPath("$.days[1].scans", hasSize(1)))
			.andExpect(jsonPath("$.days[0].result.status").value("TIDAK_LENGKAP"))
			.andExpect(jsonPath("$.timezone").value("Asia/Jakarta"));
	}

	/**
	 * TC-08: HR mengoreksi absensi. Scan asli tetap utuh, koreksi tercatat di audit dengan nilai sebelum/sesudah,
	 * pelaku, dan alasan, dan rekap memakai jam koreksi.
	 */
	@Test
	void tc08CorrectionKeepsRawScanAndIsAudited() throws Exception {
		Setup s = setup(true);
		postAttlog(s.sn(), lines(line(s.pin(), DATE + " 08:00:00")));
		Map<String, Object> rawBefore = jdbc.queryForMap(
				"select id, scanned_at, raw_line, device_pin from attendance_scans where device_id = ?", s.device());
		daily(s.branch()).andExpect(jsonPath("$.rows[0].day.result.status").value("TIDAK_LENGKAP"));

		long correctionId = idOf(correct(s.employee(), DATE, null, "17:00", "Lupa scan pulang, dikonfirmasi atasan")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.createdBy").value("hr-absensi")));

		daily(s.branch()).andExpect(jsonPath("$.rows[0].day.result.status").value("LENGKAP"))
			.andExpect(jsonPath("$.rows[0].day.result.checkIn.source").value("SCAN"))
			.andExpect(jsonPath("$.rows[0].day.result.checkOut.source").value("KOREKSI"))
			.andExpect(jsonPath("$.rows[0].day.correction.reason").value("Lupa scan pulang, dikonfirmasi atasan"));

		// Scan asli tidak berubah (K-019).
		assertThat(jdbc.queryForMap("select id, scanned_at, raw_line, device_pin from attendance_scans where device_id = ?",
				s.device())).isEqualTo(rawBefore);
		assertThat(scanCount(s.device())).isEqualTo(1);

		Map<String, Object> audit = jdbc.queryForMap(
				"select actor_username, action, before_data, after_data, reason from audit_logs "
						+ "where entity_type = 'ATTENDANCE_CORRECTION' and entity_id = ?", String.valueOf(correctionId));
		assertThat(audit.get("actor_username")).isEqualTo("hr-absensi");
		assertThat(audit.get("action")).isEqualTo("CORRECT");
		assertThat(audit.get("reason")).isEqualTo("Lupa scan pulang, dikonfirmasi atasan");
		assertThat((String) audit.get("before_data")).contains("TIDAK_LENGKAP").doesNotContain("KOREKSI");
		assertThat((String) audit.get("after_data")).contains("\"LENGKAP\"").contains("KOREKSI").contains("17:00");
	}

	/** K-066: koreksi baru menggantikan yang lama; riwayat tetap tersedia. */
	@Test
	void newCorrectionSupersedesOldOneAndHistoryIsKept() throws Exception {
		Setup s = setup(true);
		postAttlog(s.sn(), lines(line(s.pin(), DATE + " 08:30:00"), line(s.pin(), DATE + " 17:00:00")));
		correct(s.employee(), DATE, "08:00", null, "Mesin antre, scan terlambat").andExpect(status().isCreated());
		correct(s.employee(), DATE, "08:05", null, "Koreksi ulang sesuai CCTV").andExpect(status().isCreated());

		mockMvc.perform(get("/api/attendance/employees/" + s.employee()).param("from", DATE).param("to", DATE).with(HR))
			.andExpect(jsonPath("$.days[0].result.checkIn.time").value(startsWith("08:05")))
			.andExpect(jsonPath("$.days[0].result.lateSeconds").value(300))
			.andExpect(jsonPath("$.corrections", hasSize(2)))
			.andExpect(jsonPath("$.corrections[0].reason").value("Koreksi ulang sesuai CCTV"))
			.andExpect(jsonPath("$.corrections[0].supersededAt").isEmpty())
			.andExpect(jsonPath("$.corrections[1].supersededAt").isNotEmpty());
	}

	@Test
	void invalidCorrectionsAreRejected() throws Exception {
		Setup s = setup(true);
		String tomorrow = LocalDate.now(ZoneId.of("Asia/Jakarta")).plusDays(1).toString();
		correct(s.employee(), DATE, "08:00", "17:00", " ").andExpect(status().isBadRequest());
		correct(s.employee(), DATE, null, null, "Tanpa jam").andExpect(status().isBadRequest());
		correct(s.employee(), DATE, "17:00", "08:00", "Terbalik").andExpect(status().isBadRequest());
		correct(s.employee(), tomorrow, "08:00", null, "Belum terjadi").andExpect(status().isBadRequest());
		correct(999_999_999L, DATE, "08:00", null, "Tidak ada").andExpect(status().isNotFound());
	}

	@Test
	void unmatchedScansAreListed() throws Exception {
		long branch = createBranch();
		String sn = uniqueSerial();
		createDevice(branch, sn, ACCEPT_FROM);
		String pin = uniquePin();
		postAttlog(sn, lines(line(pin, DATE + " 08:00:00")));

		mockMvc.perform(get("/api/attendance/unmatched-scans").param("from", DATE).param("to", DATE).with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].devicePin", hasItem(pin)));
	}

	@Test
	void rangeIsLimited() throws Exception {
		Setup s = setup(true);
		mockMvc.perform(get("/api/attendance/employees/" + s.employee()).param("from", "2026-01-01")
			.param("to", "2026-12-31")
			.with(HR)).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/attendance/employees/" + s.employee()).param("from", "2026-10-06")
			.param("to", "2026-10-05")
			.with(HR)).andExpect(status().isBadRequest());
	}

	private ResultActions daily(long branch) throws Exception {
		return mockMvc.perform(get("/api/attendance/daily").param("date", DATE)
			.param("branchId", String.valueOf(branch))
			.with(HR));
	}

	private ResultActions correct(long employee, String date, String in, String out, String reason) throws Exception {
		List<String> fields = new ArrayList<>();
		fields.add("\"employeeId\":" + employee);
		fields.add("\"date\":\"" + date + "\"");
		if (in != null) {
			fields.add("\"checkIn\":\"" + in + "\"");
		}
		if (out != null) {
			fields.add("\"checkOut\":\"" + out + "\"");
		}
		fields.add("\"reason\":\"" + reason + "\"");
		return mockMvc.perform(post("/api/attendance/corrections").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{" + String.join(",", fields) + "}"));
	}

}
