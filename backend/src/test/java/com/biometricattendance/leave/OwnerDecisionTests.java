package com.biometricattendance.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.biometricattendance.AttendanceFixtures;

/** Keputusan Owner yang diberikan di luar web dan dicatat HR dengan bukti (spec 2026-10-24 bagian 6). Data sintetis. */
class OwnerDecisionTests extends AttendanceFixtures {

	private static final ZoneId ZONE = ZoneId.of("Asia/Jakarta");
	private static final byte[] PDF = "%PDF-1.4\n% bukti uji sintetis\n".getBytes(StandardCharsets.US_ASCII);
	private static final RequestPostProcessor EMPLOYEE = user("karyawan-uji").roles("EMPLOYEE");

	@Test
	void hrRecordsOwnerApprovalGivenOnWhatsappWithEvidence() throws Exception {
		long id = atOwnerStage();

		mockMvc.perform(record(id, true, "WHATSAPP", now(), "Owner setuju lewat WA", file()).with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DISETUJUI"))
			.andExpect(jsonPath("$.nextStage").doesNotExist())
			.andExpect(jsonPath("$.decidedBy").value("hr-absensi"))
			.andExpect(jsonPath("$.approvals.length()").value(2))
			.andExpect(jsonPath("$.approvals[1].stage").value("OWNER"))
			.andExpect(jsonPath("$.approvals[1].approved").value(true))
			.andExpect(jsonPath("$.approvals[1].method").value("WHATSAPP"))
			.andExpect(jsonPath("$.approvals[1].decidedBy").value("OWNER"))
			.andExpect(jsonPath("$.approvals[1].decidedByName").value("Owner"))
			.andExpect(jsonPath("$.approvals[1].recordedBy").value("hr-absensi"))
			.andExpect(jsonPath("$.approvals[1].note").value("Owner setuju lewat WA"))
			.andExpect(jsonPath("$.approvals[1].hasEvidence").value(true));

		assertThat(auditActions(id)).containsExactly("CREATE", "STAGE_APPROVE", "OWNER_DECISION");
		assertThat(jdbc.queryForObject(
				"select signature_storage_key is null from leave_approvals where leave_request_id = ? and stage = 'OWNER'",
				Boolean.class, id)).isTrue();
	}

	@Test
	void whatsappAndWrittenDecisionsNeedEvidence() throws Exception {
		long id = atOwnerStage();

		for (String method : List.of("WHATSAPP", "TERTULIS")) {
			mockMvc.perform(record(id, true, method, now(), "Tanpa bukti", null).with(HR))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail")
					.value("Bukti wajib diunggah untuk keputusan lewat WhatsApp atau tertulis"));
		}
		mockMvc.perform(get("/api/leave-requests/" + id).with(HR))
			.andExpect(jsonPath("$.status").value("PERLU_TINJAU_OWNER"));

		mockMvc.perform(record(id, true, "LISAN", now(), "Disampaikan langsung", null).with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DISETUJUI"))
			.andExpect(jsonPath("$.approvals[1].method").value("LISAN"))
			.andExpect(jsonPath("$.approvals[1].hasEvidence").value(false));
	}

	@Test
	void webMethodAndInvalidEvidenceAreRejected() throws Exception {
		long id = atOwnerStage();

		mockMvc.perform(record(id, true, "WEB", now(), "Bukan untuk pencatatan", file()).with(HR))
			.andExpect(status().isBadRequest());
		mockMvc.perform(record(id, true, "LISAN", now(), "Bukti bukan dokumen", new MockMultipartFile("evidence",
				"x.exe", "application/octet-stream", "MZ-bukan-pdf".getBytes(StandardCharsets.US_ASCII))).with(HR))
			.andExpect(status().isBadRequest());
		mockMvc.perform(record(id, true, "LISAN", now(), " ", null).with(HR)).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/leave-requests/" + id).with(HR))
			.andExpect(jsonPath("$.status").value("PERLU_TINJAU_OWNER"))
			.andExpect(jsonPath("$.approvals.length()").value(1));
	}

	@Test
	void decisionTimeCannotBeInFutureOrBeforeHrStage() throws Exception {
		long id = atOwnerStage();

		mockMvc.perform(record(id, true, "LISAN", LocalDateTime.now(ZONE).plusHours(2), "Masa depan", null).with(HR))
			.andExpect(status().isBadRequest());
		mockMvc.perform(record(id, true, "LISAN", LocalDateTime.now(ZONE).minusDays(1), "Sebelum tahap HR", null)
			.with(HR)).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/leave-requests/" + id).with(HR))
			.andExpect(jsonPath("$.status").value("PERLU_TINJAU_OWNER"));
	}

	/** UI mengirim waktu presisi menit: menit yang sama dengan tahap HR (yang berdetik) harus sah. */
	@Test
	void decisionTimeInSameMinuteAsHrStageIsAccepted() throws Exception {
		long id = atOwnerStage();
		java.time.Instant hrAt = jdbc.queryForObject(
				"select decided_at from leave_approvals where leave_request_id = ? and stage = 'HR'",
				java.sql.Timestamp.class, id).toInstant();
		LocalDateTime sameMinute = LocalDateTime.ofInstant(hrAt, ZONE).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);

		mockMvc.perform(record(id, true, "LISAN", sameMinute, "Menit yang sama", null).with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DISETUJUI"));
	}

	@Test
	void ownerCannotUseRecordingEndpointAndOnlyOwnerStageQualifies() throws Exception {
		long id = atOwnerStage();
		mockMvc.perform(record(id, true, "LISAN", now(), "Owner mencoba", null).with(OWNER))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("Owner memutus langsung lewat tombol Setujui atau Tolak"));

		long employee = createEmployee(createBranch(), null);
		long atHr = idOf(mockMvc.perform(submit(employee, createType(), "2026-10-12").with(HR)));
		mockMvc.perform(record(atHr, true, "LISAN", now(), "Belum giliran Owner", null).with(HR))
			.andExpect(status().isConflict());
		mockMvc.perform(record(id, true, "LISAN", now(), "Pertama", null).with(HR)).andExpect(status().isOk());
		mockMvc.perform(record(id, true, "LISAN", now(), "Kedua", null).with(HR)).andExpect(status().isConflict());
	}

	@Test
	void requesterCannotRecordOwnDecision() throws Exception {
		long employee = createEmployee(createBranch(), null);
		String username = unique("akun-");
		mockMvc.perform(post("/api/users").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"" + username + "\",\"password\":\"rahasia-uji-123\",\"role\":\"HR_FINANCE\","
					+ "\"employeeId\":" + employee + "}"))
			.andExpect(status().isCreated());
		long id = idOf(mockMvc.perform(submit(employee, createType(), "2026-10-05").with(HR)));
		mockMvc.perform(decide(id, "approve").with(user("hr-kedua").roles("HR_FINANCE"))).andExpect(status().isOk());

		mockMvc.perform(record(id, true, "LISAN", now(), "Memutus sendiri", null)
			.with(user(username).roles("HR_FINANCE"))).andExpect(status().isForbidden());
	}

	@Test
	void recordedRejectionEndsAsRejected() throws Exception {
		long id = atOwnerStage();

		mockMvc.perform(record(id, false, "TERTULIS", now(), "Owner menolak lewat surat", file()).with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DITOLAK"))
			.andExpect(jsonPath("$.decisionNote").value("Owner menolak lewat surat"))
			.andExpect(jsonPath("$.decidedBy").value("hr-absensi"))
			.andExpect(jsonPath("$.approvals[1].approved").value(false))
			.andExpect(jsonPath("$.approvals[1].method").value("TERTULIS"));
	}

	@Test
	void evidenceDownloadIsAuditedAndHiddenFromEmployees() throws Exception {
		long id = atOwnerStage();
		mockMvc.perform(get("/api/leave-requests/" + id + "/approvals/OWNER/evidence").with(HR))
			.andExpect(status().isNotFound());
		mockMvc.perform(record(id, true, "WHATSAPP", now(), "Tangkapan layar WA", file()).with(HR))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/leave-requests/" + id + "/approvals/OWNER/evidence").with(HR))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(content().bytes(PDF));
		mockMvc.perform(get("/api/leave-requests/" + id + "/approvals/HR/evidence").with(HR))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/leave-requests/" + id + "/approvals/OWNER/evidence").with(EMPLOYEE))
			.andExpect(status().isForbidden());

		assertThat(jdbc.queryForObject(
				"select count(*) from audit_logs where action = 'VIEW' and entity_type = 'LEAVE_EVIDENCE'",
				Long.class)).isGreaterThanOrEqualTo(1L);
	}

	@Test
	void ownerPendingShowsRecentDecisionsRecordedByHrNewestFirst() throws Exception {
		long first = atOwnerStage();
		long second = atOwnerStage();
		mockMvc.perform(record(first, true, "WHATSAPP", now(), "WA", file()).with(HR)).andExpect(status().isOk());
		mockMvc.perform(record(second, false, "LISAN", now(), "Lisan", null).with(HR)).andExpect(status().isOk());
		jdbc.update("update leave_approvals set recorded_at = recorded_at - interval '1 hour' where leave_request_id = ?",
				first);

		String json = mockMvc.perform(get("/api/leave-requests/pending").with(OWNER))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<Integer> order = com.jayway.jsonpath.JsonPath.read(json, "$.recordedForOwner[*].requestId");
		assertThat(order.indexOf((int) second)).isLessThan(order.indexOf((int) first));
		assertThat(order.indexOf((int) first)).isNotNegative();
		List<String> methods = com.jayway.jsonpath.JsonPath.read(json,
				"$.recordedForOwner[?(@.requestId==" + first + ")].method");
		assertThat(methods).containsExactly("WHATSAPP");
		List<Boolean> approved = com.jayway.jsonpath.JsonPath.read(json,
				"$.recordedForOwner[?(@.requestId==" + second + ")].approved");
		assertThat(approved).containsExactly(false);
		List<String> recorder = com.jayway.jsonpath.JsonPath.read(json,
				"$.recordedForOwner[?(@.requestId==" + first + ")].recorderName");
		assertThat(recorder).containsExactly("HR/Finance");
		List<String> status = com.jayway.jsonpath.JsonPath.read(json,
				"$.recordedForOwner[?(@.requestId==" + first + ")].status");
		assertThat(status).containsExactly("DISETUJUI");
	}

	@Test
	void recordedNoticeExcludesWebDecisionsOldItemsAndNonOwners() throws Exception {
		long viaWeb = atOwnerStage();
		mockMvc.perform(post("/api/leave-requests/" + viaWeb + "/approve").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")).andExpect(status().isOk());
		long old = atOwnerStage();
		mockMvc.perform(record(old, true, "TERTULIS", now(), "Surat", file()).with(HR)).andExpect(status().isOk());
		jdbc.update("update leave_approvals set recorded_at = now() - interval '8 days' where leave_request_id = ?",
				old);
		long fresh = atOwnerStage();
		mockMvc.perform(record(fresh, true, "LISAN", now(), "Lisan", null).with(HR)).andExpect(status().isOk());
		jdbc.update("update leave_approvals set recorded_at = now() - interval '6 days' where leave_request_id = ?",
				fresh);

		String owner = mockMvc.perform(get("/api/leave-requests/pending").with(OWNER))
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<Integer> ids = com.jayway.jsonpath.JsonPath.read(owner, "$.recordedForOwner[*].requestId");
		assertThat(ids).contains((int) fresh).doesNotContain((int) viaWeb, (int) old);

		mockMvc.perform(get("/api/leave-requests/pending").with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recordedForOwner.length()").value(0));
	}

	@Test
	void recordedNoticeUsesLinkedEmployeeNameOfRecorder() throws Exception {
		long hrEmployee = createEmployee(createBranch(), null);
		String hrName = jdbc.queryForObject("select full_name from employees where id = ?", String.class, hrEmployee);
		String username = unique("hr-tertaut-");
		mockMvc.perform(post("/api/users").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"" + username + "\",\"password\":\"rahasia-uji-123\",\"role\":\"HR_FINANCE\","
					+ "\"employeeId\":" + hrEmployee + "}"))
			.andExpect(status().isCreated());
		long id = atOwnerStage();
		mockMvc.perform(record(id, true, "WHATSAPP", now(), "WA", file()).with(user(username).roles("HR_FINANCE")))
			.andExpect(status().isOk());

		String json = mockMvc.perform(get("/api/leave-requests/pending").with(OWNER))
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<String> recorder = com.jayway.jsonpath.JsonPath.read(json,
				"$.recordedForOwner[?(@.requestId==" + id + ")].recorderName");
		assertThat(recorder).containsExactly(hrName);
	}

	// ---- Helper ----

	private long atOwnerStage() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long id = idOf(mockMvc.perform(submit(employee, createType(), "2026-10-05").with(HR)));
		mockMvc.perform(decide(id, "approve").with(HR)).andExpect(status().isOk());
		return id;
	}

	private static LocalDateTime now() {
		return LocalDateTime.now(ZONE);
	}

	private static MockMultipartFile file() {
		return new MockMultipartFile("evidence", "bukti.pdf", "application/pdf", PDF);
	}

	private static MockMultipartHttpServletRequestBuilder record(long id, boolean approved, String method,
			LocalDateTime decidedAt, String note, MockMultipartFile evidence) {
		String json = "{\"approved\":" + approved + ",\"method\":\"" + method + "\",\"decidedAt\":\"" + decidedAt
				+ "\",\"note\":\"" + note + "\"}";
		MockMultipartHttpServletRequestBuilder builder = multipart("/api/leave-requests/" + id + "/owner-decision");
		builder.file(new MockMultipartFile("decision", "", "application/json", json.getBytes(StandardCharsets.UTF_8)));
		if (evidence != null) {
			builder.file(evidence);
		}
		builder.with(csrf());
		return builder;
	}

	private long createType() throws Exception {
		return idOf(mockMvc.perform(post("/api/leave-types").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + unique("Jenis ") + "\",\"requiresDocument\":false,\"active\":true,"
					+ "\"requiresOwnerApproval\":true,\"letterTemplate\":\"UMUM\"}"))
			.andExpect(status().isCreated()));
	}

	private static MockHttpServletRequestBuilder submit(Long employee, long type, String day) {
		return post("/api/leave-requests").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"employeeId\":" + employee + ",\"leaveTypeId\":" + type + ",\"startDate\":\"" + day
					+ "\",\"endDate\":\"" + day + "\",\"reason\":\"Dicatat HR\"}");
	}

	private static MockHttpServletRequestBuilder decide(long id, String action) {
		return post("/api/leave-requests/" + id + "/" + action).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}");
	}

	private List<String> auditActions(long requestId) {
		return jdbc.queryForList(
				"select action from audit_logs where entity_type = 'LEAVE_REQUEST' and entity_id = ? order by id",
				String.class, String.valueOf(requestId));
	}

}
