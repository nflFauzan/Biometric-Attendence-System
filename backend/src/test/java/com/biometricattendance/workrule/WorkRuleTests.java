package com.biometricattendance.workrule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
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

/**
 * Aturan kerja lewat API: versi bertanggal berlaku (K-050), prioritas per kolom (K-046), audit, dan simulasi
 * perhitungan. Setiap test memakai karyawan/kategori baru agar tidak saling memengaruhi; aturan Global
 * sengaja tidak dibuat di sini karena berlaku untuk semua test.
 */
class WorkRuleTests extends IntegrationTest {

	private static final RequestPostProcessor OWNER = user("owner-aturan").roles("OWNER");
	private static final RequestPostProcessor HR = user("hr-aturan").roles("HR_FINANCE");
	private static final String ALL_DAYS = "[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\","
			+ "\"SATURDAY\",\"SUNDAY\"]";

	@Autowired
	private AuditLogRepository auditLogs;

	private static LocalDate today() {
		return LocalDate.now(ZoneId.of("Asia/Jakarta"));
	}

	@Test
	void ownerCreatesRuleVersionAndItIsAudited() throws Exception {
		long employeeId = createEmployee(1);
		long id = idOf(createRule(employeeRule(employeeId, today().plusDays(10), "\"workStart\":\"09:00\""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.scope").value("EMPLOYEE"))
			.andExpect(jsonPath("$.workStart").value(startsWith("09:00")))
			.andExpect(jsonPath("$.workEnd").isEmpty())
			.andExpect(jsonPath("$.createdBy").value("owner-aturan"))
			.andExpect(jsonPath("$.locked").value(false)));

		List<AuditLog> logs = auditLogs.findByEntityTypeAndEntityIdOrderByIdAsc("WORK_RULE", String.valueOf(id));
		assertThat(logs).hasSize(1);
		assertThat(logs.get(0).getAction()).isEqualTo("CREATE");
		assertThat(logs.get(0).getActorUsername()).isEqualTo("owner-aturan");
	}

	@Test
	void hrFinanceCanReadRulesButNotChangeThem() throws Exception {
		long employeeId = createEmployee(1);
		mockMvc.perform(get("/api/work-rules").param("scope", "EMPLOYEE")
			.param("employeeId", String.valueOf(employeeId))
			.with(HR)).andExpect(status().isOk());
		mockMvc.perform(post("/api/work-rules").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(employeeRule(employeeId, today().plusDays(10), "\"workStart\":\"09:00\"")))
			.andExpect(status().isForbidden());
	}

	/** K-050: versi yang tanggal berlakunya sudah tiba terkunci. */
	@Test
	void versionAlreadyInEffectCannotBeChangedOrDeleted() throws Exception {
		long employeeId = createEmployee(1);
		long id = idOf(createRule(employeeRule(employeeId, today(), "\"workStart\":\"09:00\""))
			.andExpect(jsonPath("$.locked").value(true)));

		mockMvc.perform(put("/api/work-rules/" + id).with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(employeeRule(employeeId, today().plusDays(1), "\"workStart\":\"10:00\"")))
			.andExpect(status().isConflict());
		mockMvc.perform(delete("/api/work-rules/" + id).with(OWNER).with(csrf())).andExpect(status().isConflict());
	}

	@Test
	void futureVersionCanBeChangedAndDeletedWithAuditTrail() throws Exception {
		long employeeId = createEmployee(1);
		LocalDate date = today().plusDays(30);
		long id = idOf(createRule(employeeRule(employeeId, date, "\"workStart\":\"09:00\"")));

		mockMvc.perform(put("/api/work-rules/" + id).with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(employeeRule(employeeId, date, "\"workStart\":\"09:30\"")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.workStart").value(startsWith("09:30")));
		mockMvc.perform(delete("/api/work-rules/" + id).with(OWNER).with(csrf())).andExpect(status().isNoContent());

		List<AuditLog> logs = auditLogs.findByEntityTypeAndEntityIdOrderByIdAsc("WORK_RULE", String.valueOf(id));
		assertThat(logs).extracting(AuditLog::getAction).containsExactly("CREATE", "UPDATE", "DELETE");
		assertThat(logs.get(1).getBeforeData()).contains("09:00");
		assertThat(logs.get(1).getAfterData()).contains("09:30");
	}

	@Test
	void backdatedVersionIsRejected() throws Exception {
		long employeeId = createEmployee(1);
		createRule(employeeRule(employeeId, today().minusDays(1), "\"workStart\":\"09:00\""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void duplicateEffectiveDateIsConflict() throws Exception {
		long employeeId = createEmployee(1);
		LocalDate date = today().plusDays(5);
		createRule(employeeRule(employeeId, date, "\"workStart\":\"09:00\"")).andExpect(status().isCreated());
		createRule(employeeRule(employeeId, date, "\"workStart\":\"10:00\"")).andExpect(status().isConflict());
	}

	@Test
	void invalidRuleValuesAreRejected() throws Exception {
		long employeeId = createEmployee(1);
		LocalDate date = today().plusDays(5);
		createRule(employeeRule(employeeId, date, "\"breakStart\":\"12:00\"")).andExpect(status().isBadRequest());
		createRule(employeeRule(employeeId, date, "\"workStart\":\"17:00\",\"workEnd\":\"08:00\""))
			.andExpect(status().isBadRequest());
		createRule(employeeRule(employeeId, date, "\"note\":\"kosong\"")).andExpect(status().isBadRequest());
		createRule(employeeRule(employeeId, date, "\"dailyWage\":-1")).andExpect(status().isBadRequest());
		createRule("{\"scope\":\"GLOBAL\",\"employeeId\":" + employeeId + ",\"effectiveFrom\":\"" + date
				+ "\",\"workStart\":\"08:00\"}")
			.andExpect(status().isBadRequest());
	}

	/** K-046: tiap nilai diambil dari level paling spesifik dan asalnya ditampilkan. */
	@Test
	void effectiveRuleShowsSourceOfEachValue() throws Exception {
		long categoryId = createCategory();
		long employeeId = createEmployee(categoryId);
		LocalDate date = today().plusDays(3);
		createRule("{\"scope\":\"CATEGORY\",\"categoryId\":" + categoryId + ",\"effectiveFrom\":\"" + date
				+ "\",\"workStart\":\"08:00\",\"lateToleranceMinutes\":15}")
			.andExpect(status().isCreated());
		createRule(employeeRule(employeeId, date, "\"workStart\":\"09:00\"")).andExpect(status().isCreated());

		mockMvc.perform(get("/api/work-rules/effective").param("employeeId", String.valueOf(employeeId))
			.param("date", date.toString())
			.with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.values.workStart").value(startsWith("09:00")))
			.andExpect(jsonPath("$.sources.WORK_START.level").value("EMPLOYEE"))
			.andExpect(jsonPath("$.values.lateToleranceMinutes").value(15))
			.andExpect(jsonPath("$.sources.LATE_TOLERANCE.level").value("CATEGORY"));
	}

	/** AC-05 / TC-06 lewat API: 15.00–18.00 = 2 jam normal dan 1 jam kandidat lembur. */
	@Test
	void previewCalculatesRegularWorkAndOvertimeCandidate() throws Exception {
		long employeeId = createEmployee(1);
		LocalDate date = today().plusDays(2);
		createRule(employeeRule(employeeId, date, fullRule("08:00"))).andExpect(status().isCreated());

		preview(employeeId, date, "\"15:00\",\"18:00\"").andExpect(status().isOk())
			.andExpect(jsonPath("$.result.status").value("LENGKAP"))
			.andExpect(jsonPath("$.result.regularWorkSeconds").value(7200))
			.andExpect(jsonPath("$.result.overtimeCandidateSeconds").value(3600))
			.andExpect(jsonPath("$.result.flags", hasItem("ISTIRAHAT_BELUM_DIATUR")))
			.andExpect(jsonPath("$.timezone").value("Asia/Jakarta"));
	}

	/** Perubahan aturan di tengah periode tidak mengubah hasil tanggal sebelum tanggal berlakunya. */
	@Test
	void ruleChangeMidPeriodOnlyAffectsDatesFromItsEffectiveDate() throws Exception {
		long employeeId = createEmployee(1);
		LocalDate first = today().plusDays(5);
		LocalDate change = first.plusDays(7);
		createRule(employeeRule(employeeId, first, fullRule("08:00"))).andExpect(status().isCreated());
		createRule(employeeRule(employeeId, change, fullRule("09:00"))).andExpect(status().isCreated());

		preview(employeeId, change.minusDays(1), "\"08:30\",\"17:00\"")
			.andExpect(jsonPath("$.result.lateSeconds").value(1800));
		preview(employeeId, change, "\"08:30\",\"17:00\"").andExpect(jsonPath("$.result.lateSeconds").value(0));
	}

	@Test
	void previewOnHolidayIsFlaggedForReview() throws Exception {
		long employeeId = createEmployee(1);
		LocalDate date = today().plusDays(400);
		createRule(employeeRule(employeeId, date, fullRule("08:00"))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/holidays").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"date\":\"" + date + "\",\"name\":\"Libur uji\"}")).andExpect(status().isCreated());

		preview(employeeId, date, "\"08:00\",\"18:00\"").andExpect(jsonPath("$.holiday").value(true))
			.andExpect(jsonPath("$.result.dayType").value("HARI_LIBUR"))
			.andExpect(jsonPath("$.result.overtimeCandidateSeconds").isEmpty())
			.andExpect(jsonPath("$.result.flags", hasItem("PERLU_TINJAUAN_HARI_LIBUR")));
	}

	/** K-047: tanpa aturan, perhitungan ditolak dengan pesan yang menyebut parameter yang kurang. */
	@Test
	void previewWithoutRuleIsRejected() throws Exception {
		long employeeId = createEmployee(1);
		preview(employeeId, today().minusYears(10), "\"08:00\",\"17:00\"").andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(startsWith("Aturan belum diatur: jam masuk")));
	}

	private static String fullRule(String workStart) {
		return "\"workStart\":\"" + workStart + "\",\"workEnd\":\"17:00\",\"lateToleranceMinutes\":0,"
				+ "\"overtimeStart\":\"17:00\",\"workDays\":" + ALL_DAYS;
	}

	private static String employeeRule(long employeeId, LocalDate date, String fields) {
		return "{\"scope\":\"EMPLOYEE\",\"employeeId\":" + employeeId + ",\"effectiveFrom\":\"" + date + "\","
				+ fields + "}";
	}

	private ResultActions createRule(String json) throws Exception {
		return mockMvc.perform(
				post("/api/work-rules").with(OWNER).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions preview(long employeeId, LocalDate date, String scans) throws Exception {
		return mockMvc.perform(post("/api/calculation/daily-preview").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"employeeId\":" + employeeId + ",\"date\":\"" + date + "\",\"scanTimes\":[" + scans + "]}"));
	}

	private long createCategory() throws Exception {
		return idOf(mockMvc.perform(post("/api/employee-categories").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"" + unique("K") + "\",\"name\":\"Kategori uji\",\"active\":true}"))
			.andExpect(status().isCreated()));
	}

	private long createEmployee(long categoryId) throws Exception {
		long branchId = idOf(mockMvc.perform(post("/api/branches").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"" + unique("C") + "\",\"name\":\"Cabang uji\",\"timezone\":\"Asia/Jakarta\","
					+ "\"active\":true}"))
			.andExpect(status().isCreated()));
		return idOf(mockMvc.perform(post("/api/employees").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"employeeNumber\":\"" + unique("E") + "\",\"fullName\":\"Karyawan Uji\",\"branchId\":"
					+ branchId + ",\"categoryId\":" + categoryId + ",\"joinDate\":\"2026-01-05\",\"active\":true}"))
			.andExpect(status().isCreated()));
	}

	private static long idOf(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

}
