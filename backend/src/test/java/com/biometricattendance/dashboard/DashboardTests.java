package com.biometricattendance.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.biometricattendance.AttendanceFixtures;

/** Ringkasan dashboard (DASH-001–003, DASH-007) dan pemberitahuan real-time setelah scan tersimpan (DASH-006). */
class DashboardTests extends AttendanceFixtures {

	private static final String DATE = "2026-10-05";

	@Test
	void summaryCountsPresenceLatenessOvertimeAndReviewPerBranch() throws Exception {
		long branch = createBranch();
		String sn = uniqueSerial();
		createDevice(branch, sn, ACCEPT_FROM);
		String latePin = uniquePin();
		addFullRule(createEmployee(branch, latePin));
		addFullRule(createEmployee(branch, uniquePin()));
		String noRulePin = uniquePin();
		createEmployee(branch, noRulePin);

		postAttlog(sn, lines(line(latePin, DATE + " 08:10:00"), line(latePin, DATE + " 17:30:00"),
				line(noRulePin, DATE + " 08:00:00"), line(uniquePin(), DATE + " 08:00:00")));

		String counts = "$.branches[?(@.branchId == " + branch + ")].counts.";
		mockMvc.perform(get("/api/dashboard/summary").param("date", DATE).with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath(counts + "totalEmployees").value(3))
			.andExpect(jsonPath(counts + "present").value(2))
			.andExpect(jsonPath(counts + "late").value(1))
			.andExpect(jsonPath(counts + "overtimeCandidates").value(1))
			.andExpect(jsonPath(counts + "absent").value(1))
			.andExpect(jsonPath(counts + "ruleMissing").value(1))
			.andExpect(jsonPath(counts + "needsReview").value(1))
			.andExpect(jsonPath("$.unmatchedScans").value(greaterThanOrEqualTo(1)))
			.andExpect(jsonPath("$.devices[?(@.serialNumber == '" + sn + "')].online").value(true));
	}

	/** DASH-006: dashboard yang berlangganan menerima pemberitahuan begitu scan tersimpan (setelah commit). */
	@Test
	void subscribersAreNotifiedAfterScansAreSaved() throws Exception {
		long branch = createBranch();
		String sn = uniqueSerial();
		createDevice(branch, sn, ACCEPT_FROM);
		String pin = uniquePin();
		createEmployee(branch, pin);

		MvcResult stream = mockMvc.perform(get("/api/dashboard/stream").with(HR))
			.andExpect(request().asyncStarted())
			.andReturn();
		assertThat(stream.getResponse().getContentAsString()).contains("CONNECTED");

		// Kiriman yang ditolak (mesin tak terdaftar) tidak memicu pemberitahuan.
		postAttlog(uniqueSerial(), lines(line(pin, DATE + " 07:00:00")));
		assertThat(stream.getResponse().getContentAsString()).doesNotContain("\"type\":\"SCAN\"");

		postAttlog(sn, lines(line(pin, DATE + " 07:30:00")));
		assertThat(stream.getResponse().getContentAsString()).contains("\"type\":\"SCAN\"");
	}

}
