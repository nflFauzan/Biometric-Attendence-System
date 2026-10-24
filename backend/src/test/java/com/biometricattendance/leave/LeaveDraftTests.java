package com.biometricattendance.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.biometricattendance.AttendanceFixtures;

/** Draft pengajuan izin, isian templat surat, dan kirim (spec 2026-10-24 §5 dan §7). Data sintetis. */
class LeaveDraftTests extends AttendanceFixtures {

	private static final byte[] PDF = "%PDF-1.4\n% surat uji sintetis\n".getBytes(StandardCharsets.US_ASCII);
	private static final String ME = "/api/me/leave-requests";

	@Test
	void employeeSavesIncompleteDraftThenSubmits() throws Exception {
		RequestPostProcessor me = employeeAccount(createEmployee(createBranch(), null));
		long type = createType("IZIN", false);

		long id = idOf(mockMvc.perform(json(post(ME), "{\"draft\":true,\"leaveTypeId\":" + type + "}").with(me))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.submittedAt").doesNotExist())
			.andExpect(jsonPath("$.createdAt").exists())
			.andExpect(jsonPath("$.verificationToken").doesNotExist()));

		mockMvc.perform(json(put(ME + "/" + id), body(type, "2026-10-05", "2026-10-06", "Urusan keluarga")).with(me))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.reason").value("Urusan keluarga"))
			.andExpect(jsonPath("$.calendarDays").value(2));

		mockMvc.perform(post(ME + "/" + id + "/submit").with(csrf()).with(me))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PERLU_TINJAU_HR"))
			.andExpect(jsonPath("$.submittedAt").exists())
			.andExpect(jsonPath("$.letterTemplate").value("IZIN"))
			.andExpect(jsonPath("$.requiresOwnerApproval").value(false))
			.andExpect(jsonPath("$.verificationToken").doesNotExist());

		assertThat(jdbc.queryForObject("select verification_token from leave_requests where id = ?", String.class, id))
			.matches("[0-9a-f]{32}");
		assertThat(auditActions(id)).containsExactly("CREATE", "UPDATE", "SUBMIT");
	}

	@Test
	void submitValidatesTemplateFields() throws Exception {
		RequestPostProcessor me = employeeAccount(createEmployee(createBranch(), null));
		long cuti = createType("CUTI", true);

		long noAddress = idOf(mockMvc.perform(json(post(ME), "{\"draft\":true,\"leaveTypeId\":" + cuti
				+ ",\"startDate\":\"2026-10-05\",\"endDate\":\"2026-10-05\",\"reason\":\"Liburan\","
				+ "\"contactPhone\":\"0812 3456 7890\"}").with(me)).andExpect(status().isCreated()));
		mockMvc.perform(post(ME + "/" + noAddress + "/submit").with(csrf()).with(me))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Alamat selama cuti")));

		long badPhone = idOf(mockMvc.perform(json(post(ME), "{\"draft\":true,\"leaveTypeId\":" + cuti
				+ ",\"startDate\":\"2026-10-12\",\"endDate\":\"2026-10-12\",\"reason\":\"Liburan\","
				+ "\"contactPhone\":\"abc\",\"leaveAddress\":\"Jl. Uji 1\"}").with(me)).andExpect(status().isCreated()));
		mockMvc.perform(post(ME + "/" + badPhone + "/submit").with(csrf()).with(me))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("No. HP")));

		// Tanpa alasan, kirim langsung ditolak untuk semua templat.
		mockMvc.perform(json(post(ME), "{\"leaveTypeId\":" + cuti
				+ ",\"startDate\":\"2026-10-19\",\"endDate\":\"2026-10-19\"}").with(me))
			.andExpect(status().isBadRequest());

		// Draft yang dilengkapi lalu berhasil dikirim.
		mockMvc.perform(json(put(ME + "/" + noAddress), "{\"leaveTypeId\":" + cuti
				+ ",\"startDate\":\"2026-10-05\",\"endDate\":\"2026-10-05\",\"reason\":\"Liburan\","
				+ "\"contactPhone\":\"+62 812-3456-7890\",\"leaveAddress\":\"Jl. Uji 1\",\"delegateTo\":\"Budi\"}")
			.with(me)).andExpect(status().isOk());
		mockMvc.perform(post(ME + "/" + noAddress + "/submit").with(csrf()).with(me))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.leaveAddress").value("Jl. Uji 1"))
			.andExpect(jsonPath("$.delegateTo").value("Budi"))
			.andExpect(jsonPath("$.requiresOwnerApproval").value(true));
	}

	@Test
	void draftsDoNotBlockOverlapOrRecapUntilSubmitted() throws Exception {
		long active = createEmployee(createBranch(), null);
		RequestPostProcessor me = employeeAccount(active);
		long type = createType("UMUM", false);
		mockMvc.perform(json(post("/api/leave-requests"), "{\"employeeId\":" + active + ",\"leaveTypeId\":" + type
				+ ",\"startDate\":\"2026-10-05\",\"endDate\":\"2026-10-07\",\"reason\":\"Sudah ada\"}").with(HR))
			.andExpect(status().isCreated());

		long id = idOf(mockMvc.perform(json(post(ME), body(type, "2026-10-05", "2026-10-07", "Tumpang tindih")
				.replace("{", "{\"draft\":true,")).with(me)).andExpect(status().isCreated()));
		mockMvc.perform(post(ME + "/" + id + "/submit").with(csrf()).with(me)).andExpect(status().isConflict());

		// Karyawan yang hanya punya draft tidak punya penanda izin di rekap.
		long drafter = createEmployee(createBranch(), null);
		addFullRule(drafter);
		RequestPostProcessor drafterMe = employeeAccount(drafter);
		long draft = idOf(mockMvc.perform(json(post(ME), body(type, "2026-10-05", "2026-10-05", "Belum dikirim")
				.replace("{", "{\"draft\":true,")).with(drafterMe)).andExpect(status().isCreated()));
		String recap = "/api/attendance/employees/" + drafter + "?from=2026-10-05&to=2026-10-06";
		mockMvc.perform(get(recap).with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.days[0].result.status").value("TIDAK_HADIR"))
			.andExpect(jsonPath("$.days[0].result.flags").value(org.hamcrest.Matchers.not(
					org.hamcrest.Matchers.hasItem("IZIN_MENUNGGU"))))
			.andExpect(jsonPath("$.days[0].leave").doesNotExist());

		mockMvc.perform(post(ME + "/" + draft + "/submit").with(csrf()).with(drafterMe)).andExpect(status().isOk());
		mockMvc.perform(get(recap).with(HR))
			.andExpect(jsonPath("$.days[0].result.flags[0]").value("IZIN_MENUNGGU"));
	}

	@Test
	void typeSettingsAreFrozenAtSubmit() throws Exception {
		RequestPostProcessor me = employeeAccount(createEmployee(createBranch(), null));
		long type = createType("IZIN", false);
		long id = idOf(mockMvc.perform(json(post(ME), body(type, "2026-10-05", "2026-10-05", "Uji")).with(me))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.requiresOwnerApproval").value(false)));

		mockMvc.perform(json(put("/api/leave-types/" + type), "{\"name\":\"" + unique("Beku ")
				+ "\",\"requiresDocument\":false,\"active\":true,\"requiresOwnerApproval\":true,"
				+ "\"letterTemplate\":\"SAKIT\"}").with(OWNER)).andExpect(status().isOk());

		mockMvc.perform(get(ME + "/" + id).with(me))
			.andExpect(jsonPath("$.requiresOwnerApproval").value(false))
			.andExpect(jsonPath("$.letterTemplate").value("IZIN"));
	}

	@Test
	void onlyDraftsCanBeEditedOrDeleted() throws Exception {
		RequestPostProcessor me = employeeAccount(createEmployee(createBranch(), null));
		long type = createType("UMUM", false);
		long submitted = idOf(mockMvc.perform(json(post(ME), body(type, "2026-10-05", "2026-10-05", "Kirim")).with(me))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PERLU_TINJAU_HR")));
		mockMvc.perform(json(put(ME + "/" + submitted), body(type, "2026-10-05", "2026-10-05", "Ubah")).with(me))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Hanya draft yang bisa diubah"));
		mockMvc.perform(delete(ME + "/" + submitted).with(csrf()).with(me))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Hanya draft yang bisa dihapus"));
		mockMvc.perform(post(ME + "/" + submitted + "/submit").with(csrf()).with(me)).andExpect(status().isConflict());

		long draft = idOf(mockMvc.perform(json(post(ME), body(type, "2026-10-19", "2026-10-19", "Draft")
				.replace("{", "{\"draft\":true,")).with(me)).andExpect(status().isCreated()));
		mockMvc.perform(multipart(ME + "/" + draft + "/attachments")
			.file(new MockMultipartFile("file", "surat.pdf", "application/octet-stream", PDF))
			.with(csrf())
			.with(me)).andExpect(status().isCreated());
		String key = jdbc.queryForObject("select storage_key from leave_attachments where leave_request_id = ?",
				String.class, draft);
		assertThat(key).isNotBlank();

		mockMvc.perform(delete(ME + "/" + draft).with(csrf()).with(me))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));
		mockMvc.perform(get(ME + "/" + draft).with(me)).andExpect(status().isNotFound());
		assertThat(auditActions(draft)).containsExactly("CREATE", "DELETE");
		assertThat(jdbc.queryForObject("select count(*) from leave_attachments where leave_request_id = ?",
				Long.class, draft)).isZero();
	}

	@Test
	void managerRecordsOnBehalfStraightToHrReview() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long type = createType("UMUM", false);
		mockMvc.perform(json(post("/api/leave-requests"), "{\"draft\":true,\"employeeId\":" + employee
				+ ",\"leaveTypeId\":" + type
				+ ",\"startDate\":\"2026-10-05\",\"endDate\":\"2026-10-05\",\"reason\":\"Dicatat HR\"}").with(HR))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PERLU_TINJAU_HR"))
			.andExpect(jsonPath("$.submittedBy").value("hr-absensi"))
			.andExpect(jsonPath("$.submittedAt").exists());
	}

	@Test
	void othersCannotTouchMyDraft() throws Exception {
		RequestPostProcessor mine = employeeAccount(createEmployee(createBranch(), null));
		RequestPostProcessor other = employeeAccount(createEmployee(createBranch(), null));
		long type = createType("UMUM", false);
		long id = idOf(mockMvc.perform(json(post(ME), body(type, "2026-10-05", "2026-10-05", "Milik saya")
				.replace("{", "{\"draft\":true,")).with(mine)).andExpect(status().isCreated()));

		mockMvc.perform(json(put(ME + "/" + id), body(type, "2026-10-05", "2026-10-05", "Dibajak")).with(other))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete(ME + "/" + id).with(csrf()).with(other)).andExpect(status().isNotFound());
		mockMvc.perform(post(ME + "/" + id + "/submit").with(csrf()).with(other)).andExpect(status().isNotFound());
		mockMvc.perform(get(ME + "/" + id).with(mine)).andExpect(jsonPath("$.status").value("DRAFT"));
	}

	@Test
	void draftsAreHiddenFromManagerListAndPending() throws Exception {
		long employee = createEmployee(createBranch(), null);
		RequestPostProcessor me = employeeAccount(employee);
		long type = createType("UMUM", false);
		long id = idOf(mockMvc.perform(json(post(ME), "{\"draft\":true,\"leaveTypeId\":" + type + "}").with(me))
			.andExpect(status().isCreated()));

		mockMvc.perform(get("/api/leave-requests?employeeId=" + employee).with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/leave-requests/" + id).with(HR)).andExpect(status().isNotFound());
		mockMvc.perform(get(ME).with(me))
			.andExpect(jsonPath("$[0].id").value(id))
			.andExpect(jsonPath("$[0].leaveTypeId").value(type));
	}

	@Test
	void attachingToUntypedDraftWorksAndDoesNotNotifyManagers() throws Exception {
		RequestPostProcessor me = employeeAccount(createEmployee(createBranch(), null));
		long id = idOf(mockMvc.perform(json(post(ME), "{\"draft\":true}").with(me)).andExpect(status().isCreated()));
		var stream = mockMvc.perform(get("/api/dashboard/stream").with(HR))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted())
			.andReturn();

		mockMvc.perform(multipart(ME + "/" + id + "/attachments")
			.file(new MockMultipartFile("file", "surat.pdf", "application/octet-stream", PDF))
			.with(csrf())
			.with(me)).andExpect(status().isCreated());

		assertThat(stream.getResponse().getContentAsString()).doesNotContain("ATTACHMENT_ADDED");
	}

	// ---- Helper ----

	private long createType(String template, boolean ownerApproval) throws Exception {
		return idOf(mockMvc.perform(json(post("/api/leave-types"), "{\"name\":\"" + unique("Jenis ")
				+ "\",\"requiresDocument\":false,\"active\":true,\"requiresOwnerApproval\":" + ownerApproval
				+ ",\"letterTemplate\":\"" + template + "\"}").with(OWNER)).andExpect(status().isCreated()));
	}

	private RequestPostProcessor employeeAccount(long employeeId) throws Exception {
		String username = unique("akun-");
		mockMvc.perform(json(post("/api/users"), "{\"username\":\"" + username
				+ "\",\"password\":\"rahasia-uji-123\",\"role\":\"EMPLOYEE\",\"employeeId\":" + employeeId + "}")
			.with(OWNER)).andExpect(status().isCreated());
		return user(username).roles("EMPLOYEE");
	}

	private static String body(long type, String start, String end, String reason) {
		return "{\"leaveTypeId\":" + type + ",\"startDate\":\"" + start + "\",\"endDate\":\"" + end
				+ "\",\"reason\":\"" + reason + "\"}";
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String content) {
		return request.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(content);
	}

	private List<String> auditActions(long requestId) {
		return jdbc.queryForList(
				"select action from audit_logs where entity_type = 'LEAVE_REQUEST' and entity_id = ? order by id",
				String.class, String.valueOf(requestId));
	}

}
