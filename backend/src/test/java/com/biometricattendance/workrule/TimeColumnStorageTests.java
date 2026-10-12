package com.biometricattendance.workrule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.biometricattendance.AttendanceFixtures;

/**
 * K-069 (regresi): jam tanpa zona waktu disimpan apa adanya. Sebelumnya hibernate.jdbc.time_zone=UTC membuat
 * 08:00 dari aplikasi tersimpan 01:00 di database (JVM WIB), dan 08:00 di database terbaca 15:00.
 */
class TimeColumnStorageTests extends AttendanceFixtures {

	@Test
	void ruleTimeFromApiIsStoredUnshifted() throws Exception {
		long employee = createEmployee(createBranch(), null);
		LocalDate date = LocalDate.now(ZoneId.of("Asia/Jakarta")).plusDays(20);
		mockMvc.perform(post("/api/work-rules").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"scope\":\"EMPLOYEE\",\"employeeId\":" + employee + ",\"effectiveFrom\":\"" + date
					+ "\",\"workStart\":\"08:00\"}"))
			.andExpect(status().isCreated());

		assertThat(jdbc.queryForObject("select work_start::text from work_rules where employee_id = ?", String.class,
				employee)).isEqualTo("08:00:00");
	}

	@Test
	void ruleTimeFromDatabaseIsReadUnshifted() throws Exception {
		long employee = createEmployee(createBranch(), null);
		addFullRule(employee);

		mockMvc.perform(get("/api/work-rules/effective").param("employeeId", String.valueOf(employee))
			.param("date", "2026-10-05")
			.with(HR))
			.andExpect(jsonPath("$.values.workStart").value(startsWith("08:00")))
			.andExpect(jsonPath("$.values.overtimeStart").value(startsWith("17:00")));
	}

	@Test
	void correctionTimeIsStoredUnshifted() throws Exception {
		long employee = createEmployee(createBranch(), null);
		mockMvc.perform(post("/api/attendance/corrections").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"employeeId\":" + employee + ",\"date\":\"2026-10-05\",\"checkIn\":\"08:00\","
					+ "\"reason\":\"Uji penyimpanan jam\"}"))
			.andExpect(status().isCreated());

		assertThat(jdbc.queryForObject("select check_in::text from attendance_corrections where employee_id = ?",
				String.class, employee)).isEqualTo("08:00:00");
	}

}
