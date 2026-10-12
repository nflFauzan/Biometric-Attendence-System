package com.biometricattendance.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.biometricattendance.IntegrationTest;
import com.biometricattendance.audit.AuditLog;
import com.biometricattendance.audit.AuditLogRepository;
import com.jayway.jsonpath.JsonPath;

/** Kalender libur: dikelola HR/Finance dan Owner (K-049), setiap perubahan tercatat di audit log. */
class HolidayTests extends IntegrationTest {

	private static final RequestPostProcessor HR = user("hr-libur").roles("HR_FINANCE");

	@Autowired
	private AuditLogRepository auditLogs;

	@Test
	void holidayLifecycleIsAudited() throws Exception {
		long id = idOf(create("2031-05-01", "Hari Buruh").andExpect(status().isCreated()));

		mockMvc.perform(get("/api/holidays").param("year", "2031").with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].name", hasItem("Hari Buruh")));
		mockMvc.perform(put("/api/holidays/" + id).with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"date\":\"2031-05-02\",\"name\":\"Cuti bersama\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.date").value("2031-05-02"));
		mockMvc.perform(delete("/api/holidays/" + id).with(HR).with(csrf())).andExpect(status().isNoContent());

		List<AuditLog> logs = auditLogs.findByEntityTypeAndEntityIdOrderByIdAsc("HOLIDAY", String.valueOf(id));
		assertThat(logs).extracting(AuditLog::getAction).containsExactly("CREATE", "UPDATE", "DELETE");
		assertThat(logs).extracting(AuditLog::getActorUsername).containsOnly("hr-libur");
		assertThat(logs.get(2).getBeforeData()).contains("Cuti bersama");
	}

	@Test
	void sameDateCannotBeRecordedTwice() throws Exception {
		create("2032-01-01", "Tahun Baru").andExpect(status().isCreated());
		create("2032-01-01", "Tahun Baru lagi").andExpect(status().isConflict());
	}

	@Test
	void listOnlyReturnsRequestedYear() throws Exception {
		create("2033-12-31", "Akhir tahun").andExpect(status().isCreated());
		create("2034-01-01", "Awal tahun").andExpect(status().isCreated());

		mockMvc.perform(get("/api/holidays").param("year", "2033").with(HR))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].date").value("2033-12-31"));
	}

	@Test
	void missingFieldsAreRejected() throws Exception {
		mockMvc.perform(post("/api/holidays").with(HR).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.date").value("Tanggal wajib diisi"))
			.andExpect(jsonPath("$.errors.name").value("Nama wajib diisi"));
	}

	private ResultActions create(String date, String name) throws Exception {
		return mockMvc.perform(post("/api/holidays").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"date\":\"" + date + "\",\"name\":\"" + name + "\"}"));
	}

	private static long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

}
