package com.biometricattendance.attendance;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.biometricattendance.AttendanceFixtures;

/** Daftar "Perlu Ditinjau" (FR-REV-001): hari yang butuh keputusan manusia, dari rekap yang sama dengan absensi. */
class ReviewQueueTests extends AttendanceFixtures {

	private static final String DATE = "2026-10-05";

	private record Setup(long branch, long employee, String pin, String sn) {
	}

	private Setup setup(boolean withRule) throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		long employee = createEmployee(branch, pin);
		if (withRule) {
			addFullRule(employee);
		}
		String sn = uniqueSerial();
		createDevice(branch, sn, ACCEPT_FROM);
		return new Setup(branch, employee, pin, sn);
	}

	private ResultActions queue(long branch) throws Exception {
		return mockMvc.perform(get("/api/attendance/review-queue").param("from", DATE)
			.param("to", DATE)
			.param("branchId", String.valueOf(branch))
			.with(HR));
	}

	@Test
	void incompleteDayIsListed() throws Exception {
		Setup s = setup(true);
		postAttlog(s.sn(), lines(line(s.pin(), DATE + " 08:00:00"))).andExpect(status().isOk());

		queue(s.branch()).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].employeeId").value(s.employee()))
			.andExpect(jsonPath("$[0].date").value(DATE))
			.andExpect(jsonPath("$[0].status").value("TIDAK_LENGKAP"))
			.andExpect(jsonPath("$[0].reasons", contains("Tidak lengkap")));
	}

	@Test
	void completeNormalDayIsNotListed() throws Exception {
		Setup s = setup(true);
		postAttlog(s.sn(), lines(line(s.pin(), DATE + " 08:00:00"), line(s.pin(), DATE + " 17:00:00")))
			.andExpect(status().isOk());

		queue(s.branch()).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void absentDayIsNotListed() throws Exception {
		Setup s = setup(true);
		queue(s.branch()).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void correctedDayLeavesTheQueue() throws Exception {
		Setup s = setup(true);
		postAttlog(s.sn(), lines(line(s.pin(), DATE + " 08:00:00"))).andExpect(status().isOk());
		queue(s.branch()).andExpect(jsonPath("$", hasSize(1)));

		mockMvc.perform(post("/api/attendance/corrections").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"employeeId\":" + s.employee() + ",\"date\":\"" + DATE
					+ "\",\"checkOut\":\"17:00\",\"reason\":\"Lupa scan pulang\"}"))
			.andExpect(status().isCreated());

		queue(s.branch()).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void heldScanDayIsListed() throws Exception {
		long home = createBranch();
		long other = createBranch();
		String pin = uniquePin();
		long employee = createEmployee(home, pin);
		addFullRule(employee);
		String sn = uniqueSerial();
		createDevice(other, sn, ACCEPT_FROM);
		postAttlog(sn, lines(line(pin, DATE + " 08:00:00"), line(pin, DATE + " 17:00:00")))
			.andExpect(status().isOk());

		queue(home).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].flags", hasItem("ADA_SCAN_TERTAHAN")))
			.andExpect(jsonPath("$[0].reasons", hasItem("Ada scan tertahan")));
	}

	@Test
	void missingWorkRuleIsListed() throws Exception {
		Setup s = setup(false);
		postAttlog(s.sn(), lines(line(s.pin(), DATE + " 08:00:00"), line(s.pin(), DATE + " 17:00:00")))
			.andExpect(status().isOk());

		queue(s.branch()).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].ruleMissing").value(true))
			.andExpect(jsonPath("$[0].reasons", hasItem("Aturan kerja belum diatur")));
	}

	/** Hari setelah tanggal keluar tidak masuk daftar, walau tanpa aturan kerja setiap hari akan terdaftar. */
	@Test
	void daysAfterEndDateAreNotListed() throws Exception {
		Setup s = setup(false);
		jdbc.update("update employees set active = false, end_date = ? where id = ?",
				java.time.LocalDate.parse("2026-10-07"), s.employee());

		mockMvc.perform(get("/api/attendance/review-queue").param("from", "2026-10-05")
			.param("to", "2026-10-11")
			.param("branchId", String.valueOf(s.branch()))
			.with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].date", contains("2026-10-07", "2026-10-06", "2026-10-05")));
	}

	/** Hari sebelum tanggal masuk tidak masuk daftar. */
	@Test
	void daysBeforeStartDateAreNotListed() throws Exception {
		Setup s = setup(false);
		jdbc.update("update employees set join_date = ? where id = ?", java.time.LocalDate.parse("2026-10-09"),
				s.employee());

		mockMvc.perform(get("/api/attendance/review-queue").param("from", "2026-10-05")
			.param("to", "2026-10-11")
			.param("branchId", String.valueOf(s.branch()))
			.with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].date", contains("2026-10-11", "2026-10-10", "2026-10-09")));
	}

	@Test
	void employeeCannotReadReviewQueue() throws Exception {
		mockMvc.perform(get("/api/attendance/review-queue")
			.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
				.user("karyawan-rq").roles("EMPLOYEE")))
			.andExpect(status().isForbidden());
	}

	@Test
	void invalidRangeIsRejected() throws Exception {
		mockMvc.perform(get("/api/attendance/review-queue").param("from", "2026-10-06").param("to", "2026-10-05").with(HR))
			.andExpect(status().isBadRequest());
	}

}
