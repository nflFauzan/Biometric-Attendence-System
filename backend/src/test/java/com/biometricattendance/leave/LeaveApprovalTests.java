package com.biometricattendance.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.biometricattendance.AttendanceFixtures;

/** Persetujuan bertahap HR lalu Owner dan riwayat keputusan (spec 2026-10-24 bagian 5, TC-19). Data sintetis. */
class LeaveApprovalTests extends AttendanceFixtures {

	private static final RequestPostProcessor HR_2 = user("hr-kedua").roles("HR_FINANCE");

	@MockitoBean
	HrReviewerCounter hrCounter;

	@BeforeEach
	void otherHrExists() {
		when(hrCounter.countOtherActiveHr(org.mockito.ArgumentMatchers.any())).thenReturn(1L);
	}

	/** TC-19. */
	@Test
	void doubleApprovalNeedsHrThenOwner() throws Exception {
		long employee = createEmployee(createBranch(), null);
		addFullRule(employee);
		long type = createType(true);
		String recap = "/api/attendance/employees/" + employee + "?from=2026-10-05&to=2026-10-05";
		long id = idOf(mockMvc.perform(submitFor(employee, type, "2026-10-05", "2026-10-05").with(HR)));

		mockMvc.perform(decide(id, "approve", "Data lengkap").with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PERLU_TINJAU_OWNER"))
			.andExpect(jsonPath("$.nextStage").value("OWNER"))
			.andExpect(jsonPath("$.approvals.length()").value(1))
			.andExpect(jsonPath("$.approvals[0].stage").value("HR"))
			.andExpect(jsonPath("$.approvals[0].approved").value(true))
			.andExpect(jsonPath("$.approvals[0].decidedBy").value("hr-absensi"))
			.andExpect(jsonPath("$.approvals[0].method").value("WEB"))
			.andExpect(jsonPath("$.approvals[0].recordedBy").value("hr-absensi"))
			.andExpect(jsonPath("$.approvals[0].hasEvidence").value(false))
			.andExpect(jsonPath("$.decidedBy").doesNotExist());
		mockMvc.perform(get(recap).with(HR))
			.andExpect(jsonPath("$.days[0].result.flags[0]").value("IZIN_MENUNGGU"))
			.andExpect(jsonPath("$.days[0].leave.status").value("PERLU_TINJAU_OWNER"));

		mockMvc.perform(decide(id, "approve", "Setuju").with(OWNER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DISETUJUI"))
			.andExpect(jsonPath("$.nextStage").doesNotExist())
			.andExpect(jsonPath("$.approvals.length()").value(2))
			.andExpect(jsonPath("$.approvals[1].stage").value("OWNER"))
			.andExpect(jsonPath("$.approvals[1].decidedBy").value("owner-absensi"))
			.andExpect(jsonPath("$.decidedBy").value("owner-absensi"));
		mockMvc.perform(get(recap).with(HR))
			.andExpect(jsonPath("$.days[0].result.status").value("IZIN"))
			.andExpect(jsonPath("$.days[0].leave.status").value("DISETUJUI"));

		assertThat(auditActions(id)).containsExactly("CREATE", "STAGE_APPROVE", "APPROVE");
	}

	/** K-125: Owner boleh memutus langsung tanpa tahap HR; dicatat sebagai satu baris tahap Owner. */
	@Test
	void ownerApprovesDoubleApprovalTypeDirectlyAtHrStage() throws Exception {
		long id = idOf(mockMvc
			.perform(submitFor(createEmployee(createBranch(), null), createType(true), "2026-10-05", "2026-10-05").with(HR)));

		mockMvc.perform(decide(id, "approve", "Langsung").with(OWNER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DISETUJUI"))
			.andExpect(jsonPath("$.approvals.length()").value(1))
			.andExpect(jsonPath("$.approvals[0].stage").value("OWNER"))
			.andExpect(jsonPath("$.approvals[0].approved").value(true))
			.andExpect(jsonPath("$.approvals[0].method").value("WEB"))
			.andExpect(jsonPath("$.decidedBy").value("owner-absensi"));
		assertThat(auditActions(id)).containsExactly("CREATE", "APPROVE");
	}

	@Test
	void ownerRejectsDoubleApprovalTypeDirectlyAtHrStage() throws Exception {
		long id = idOf(mockMvc
			.perform(submitFor(createEmployee(createBranch(), null), createType(true), "2026-10-05", "2026-10-05").with(HR)));

		mockMvc.perform(decide(id, "reject", "Tidak ada pengganti").with(OWNER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DITOLAK"))
			.andExpect(jsonPath("$.approvals.length()").value(1))
			.andExpect(jsonPath("$.approvals[0].stage").value("OWNER"))
			.andExpect(jsonPath("$.approvals[0].approved").value(false))
			.andExpect(jsonPath("$.decisionNote").value("Tidak ada pengganti"));
	}

	@Test
	void ownerDirectApprovalStillRequiresSupportingDocument() throws Exception {
		long id = idOf(mockMvc.perform(
				submitFor(createEmployee(createBranch(), null), createType(true, true), "2026-10-05", "2026-10-05")
					.with(HR)));

		mockMvc.perform(decide(id, "approve", null).with(OWNER))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("wajib surat pendukung")));
		mockMvc.perform(get("/api/leave-requests/" + id).with(HR))
			.andExpect(jsonPath("$.status").value("PERLU_TINJAU_HR"))
			.andExpect(jsonPath("$.approvals.length()").value(0));
	}

	/** Tahap HR dan keputusan langsung Owner menulis baris tahap berbeda; @Version yang menahan keduanya. */
	@Test
	void hrAndOwnerDecidingAtTheSameTimeLetOnlyOneWin() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long id = idOf(mockMvc.perform(submitFor(employee, createType(true), "2026-11-09", "2026-11-09").with(HR)));

		RequestPostProcessor[] deciders = { HR, OWNER };
		java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
		List<Integer> statuses = concurrently(2, () -> mockMvc
			.perform(decide(id, "approve", null).with(deciders[next.getAndIncrement()]))
			.andReturn()
			.getResponse()
			.getStatus());

		// Bila HR selesai lebih dulu, Owner sah memutus tahap Owner (dua 200, baris HR lalu OWNER). Selain itu yang
		// kalah mendapat 409. Tidak boleh ada error lain, dan jumlah baris sama dengan jumlah keputusan yang sukses.
		assertThat(statuses).allMatch(s -> s == 200 || s == 409).contains(200);
		long ok = statuses.stream().filter(s -> s == 200).count();
		List<String> stages = jdbc.queryForList(
				"select stage from leave_approvals where leave_request_id = ? order by id", String.class, id);
		assertThat(stages).hasSize((int) ok);
		if (ok == 2) {
			assertThat(stages).containsExactly("HR", "OWNER");
		}
		mockMvc.perform(get("/api/leave-requests/" + id).with(HR))
			.andExpect(jsonPath("$.status").value(ok == 2 || stages.equals(List.of("OWNER")) ? "DISETUJUI"
					: "PERLU_TINJAU_OWNER"));
	}

	@Test
	void hrCannotApproveOwnerStage() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long id = idOf(mockMvc.perform(submitFor(employee, createType(true), "2026-10-05", "2026-10-05").with(HR)));
		mockMvc.perform(decide(id, "approve", null).with(HR)).andExpect(status().isOk());

		mockMvc.perform(decide(id, "approve", null).with(HR_2)).andExpect(status().isForbidden());
		mockMvc.perform(decide(id, "reject", "Tidak").with(HR_2)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/leave-requests/" + id).with(HR))
			.andExpect(jsonPath("$.status").value("PERLU_TINJAU_OWNER"))
			.andExpect(jsonPath("$.approvals.length()").value(1));
	}

	@Test
	void singleApprovalTypeIsDoneByHrOrOwner() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long type = createType(false);
		long byOwner = idOf(mockMvc.perform(submitFor(employee, type, "2026-10-05", "2026-10-05").with(HR)));

		mockMvc.perform(decide(byOwner, "approve", null).with(OWNER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DISETUJUI"))
			.andExpect(jsonPath("$.approvals.length()").value(1))
			.andExpect(jsonPath("$.approvals[0].stage").value("HR"))
			.andExpect(jsonPath("$.approvals[0].decidedBy").value("owner-absensi"))
			.andExpect(jsonPath("$.decidedBy").value("owner-absensi"));

		long byHr = idOf(mockMvc.perform(submitFor(employee, type, "2026-10-12", "2026-10-12").with(HR)));
		mockMvc.perform(decide(byHr, "approve", null).with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DISETUJUI"))
			.andExpect(jsonPath("$.approvals.length()").value(1));
		assertThat(auditActions(byHr)).containsExactly("CREATE", "APPROVE");
	}

	@Test
	void rejectionAtEitherStageEndsAsRejected() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long type = createType(true);

		long atHr = idOf(mockMvc.perform(submitFor(employee, type, "2026-10-05", "2026-10-05").with(HR)));
		mockMvc.perform(decide(atHr, "reject", null).with(HR)).andExpect(status().isBadRequest());
		mockMvc.perform(decide(atHr, "reject", "Bentrok jadwal").with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DITOLAK"))
			.andExpect(jsonPath("$.approvals.length()").value(1))
			.andExpect(jsonPath("$.approvals[0].stage").value("HR"))
			.andExpect(jsonPath("$.approvals[0].approved").value(false))
			.andExpect(jsonPath("$.approvals[0].note").value("Bentrok jadwal"));

		long atOwner = idOf(mockMvc.perform(submitFor(employee, type, "2026-10-12", "2026-10-12").with(HR)));
		mockMvc.perform(decide(atOwner, "approve", null).with(HR)).andExpect(status().isOk());
		mockMvc.perform(decide(atOwner, "reject", "Tidak disetujui").with(OWNER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DITOLAK"))
			.andExpect(jsonPath("$.approvals.length()").value(2))
			.andExpect(jsonPath("$.approvals[0].stage").value("HR"))
			.andExpect(jsonPath("$.approvals[0].approved").value(true))
			.andExpect(jsonPath("$.approvals[1].stage").value("OWNER"))
			.andExpect(jsonPath("$.approvals[1].approved").value(false))
			.andExpect(jsonPath("$.decisionNote").value("Tidak disetujui"));
	}

	/** K-079 berlaku di setiap tahap: pemohon yang juga HR tidak bisa memutus pengajuannya sendiri. */
	@Test
	void requesterCannotDecideAnyStageOfOwnRequest() throws Exception {
		long hrEmployee = createEmployee(createBranch(), null);
		String username = unique("akun-");
		mockMvc.perform(post("/api/users").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"" + username + "\",\"password\":\"rahasia-uji-123\",\"role\":\"HR_FINANCE\","
					+ "\"employeeId\":" + hrEmployee + "}"))
			.andExpect(status().isCreated());
		RequestPostProcessor self = user(username).roles("HR_FINANCE");
		long id = idOf(mockMvc.perform(submitFor(hrEmployee, createType(true), "2026-10-05", "2026-10-05").with(HR)));

		mockMvc.perform(decide(id, "approve", null).with(self)).andExpect(status().isForbidden());
		mockMvc.perform(decide(id, "approve", null).with(HR_2)).andExpect(status().isOk());
		mockMvc.perform(decide(id, "approve", null).with(self)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/leave-requests/" + id).with(HR))
			.andExpect(jsonPath("$.status").value("PERLU_TINJAU_OWNER"));
	}

	/** Flag keputusan dihitung server dengan aturan yang sama dengan approve/reject (K-079). */
	@Test
	void ownerSeesCanDecideAtHrStageOfDoubleApprovalType() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long id = idOf(mockMvc.perform(submitFor(employee, createType(true), "2026-10-05", "2026-10-05").with(HR)));
		String url = "/api/leave-requests/" + id;

		mockMvc.perform(get(url).with(HR_2)).andExpect(jsonPath("$.canDecide").value(true));
		mockMvc.perform(get(url).with(OWNER))
			.andExpect(jsonPath("$.canDecide").value(true))
			.andExpect(jsonPath("$.canRecordOwnerDecision").value(false));
	}

	@Test
	void requesterHrSeesNoDecisionFlagsOnOwnRequest() throws Exception {
		long hrEmployee = createEmployee(createBranch(), null);
		String username = unique("akun-");
		mockMvc.perform(post("/api/users").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"" + username + "\",\"password\":\"rahasia-uji-123\",\"role\":\"HR_FINANCE\","
					+ "\"employeeId\":" + hrEmployee + "}"))
			.andExpect(status().isCreated());
		RequestPostProcessor self = user(username).roles("HR_FINANCE");
		long id = idOf(mockMvc.perform(submitFor(hrEmployee, createType(true), "2026-10-05", "2026-10-05").with(HR)));
		String url = "/api/leave-requests/" + id;

		mockMvc.perform(get(url).with(self))
			.andExpect(jsonPath("$.canDecide").value(false))
			.andExpect(jsonPath("$.canRecordOwnerDecision").value(false));
		mockMvc.perform(decide(id, "approve", null).with(HR_2)).andExpect(status().isOk());
		mockMvc.perform(get(url).with(self))
			.andExpect(jsonPath("$.canDecide").value(false))
			.andExpect(jsonPath("$.canRecordOwnerDecision").value(false));
	}

	@Test
	void flagsAtOwnerStageFollowWhoMayDecideOrRecord() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long id = idOf(mockMvc.perform(submitFor(employee, createType(true), "2026-10-05", "2026-10-05").with(HR)));
		mockMvc.perform(decide(id, "approve", null).with(HR)).andExpect(status().isOk());
		String url = "/api/leave-requests/" + id;

		mockMvc.perform(get(url).with(HR_2))
			.andExpect(jsonPath("$.canDecide").value(false))
			.andExpect(jsonPath("$.canRecordOwnerDecision").value(true));
		mockMvc.perform(get(url).with(OWNER))
			.andExpect(jsonPath("$.canDecide").value(true))
			.andExpect(jsonPath("$.canRecordOwnerDecision").value(false));
		mockMvc.perform(decide(id, "approve", null).with(OWNER)).andExpect(status().isOk());
		mockMvc.perform(get(url).with(OWNER))
			.andExpect(jsonPath("$.canDecide").value(false))
			.andExpect(jsonPath("$.canRecordOwnerDecision").value(false));
	}

	@Test
	void concurrentStageApprovalsRecordOneStage() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long id = idOf(mockMvc.perform(submitFor(employee, createType(false), "2026-11-09", "2026-11-09").with(HR)));

		RequestPostProcessor[] approvers = { HR, HR_2 };
		java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
		List<Integer> statuses = concurrently(2, () -> mockMvc
			.perform(decide(id, "approve", null).with(approvers[next.getAndIncrement()]))
			.andReturn()
			.getResponse()
			.getStatus());

		assertThat(statuses).containsExactlyInAnyOrder(200, 409);
		assertThat(jdbc.queryForObject("select count(*) from leave_approvals where leave_request_id = ?", Long.class,
				id)).isEqualTo(1L);
	}

	@Test
	void pendingListDependsOnRole() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long doubleType = createType(true);
		long singleType = createType(false);
		long doubleAtHr = idOf(mockMvc.perform(submitFor(employee, doubleType, "2026-10-05", "2026-10-05").with(HR)));
		long singleAtHr = idOf(mockMvc.perform(submitFor(employee, singleType, "2026-10-06", "2026-10-06").with(HR)));
		long doubleAtOwner = idOf(mockMvc.perform(submitFor(employee, doubleType, "2026-10-07", "2026-10-07").with(HR)));
		mockMvc.perform(decide(doubleAtOwner, "approve", null).with(HR)).andExpect(status().isOk());

		String hrIds = mockMvc.perform(get("/api/leave-requests/pending").with(HR))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(ids(hrIds)).contains(doubleAtHr, singleAtHr).doesNotContain(doubleAtOwner);
		assertThat(statusOf(hrIds, singleAtHr)).isEqualTo("PERLU_TINJAU_HR");

		String ownerIds = mockMvc.perform(get("/api/leave-requests/pending").with(OWNER))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(ids(ownerIds)).contains(singleAtHr, doubleAtOwner).doesNotContain(doubleAtHr);
		assertThat(statusOf(ownerIds, doubleAtOwner)).isEqualTo("PERLU_TINJAU_OWNER");
		assertThat(statusOf(ownerIds, singleAtHr)).isEqualTo("PERLU_TINJAU_HR");
	}

	/** Bila tak ada HR/Finance lain yang bisa meninjau, tahap HR boleh diputus Owner, jadi harus muncul di daftarnya. */
	@Test
	void ownerPendingListIncludesHrStageWhenNoOtherHrCanReview() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long doubleType = createType(true);
		long soleHr = idOf(mockMvc.perform(submitFor(employee, doubleType, "2026-10-05", "2026-10-05").with(HR)));
		when(hrCounter.countOtherActiveHr(employee)).thenReturn(0L);

		String ownerJson = mockMvc.perform(get("/api/leave-requests/pending").with(OWNER))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(ids(ownerJson)).contains(soleHr);
		assertThat(statusOf(ownerJson, soleHr)).isEqualTo("PERLU_TINJAU_HR");

		when(hrCounter.countOtherActiveHr(employee)).thenReturn(1L);
		String afterJson = mockMvc.perform(get("/api/leave-requests/pending").with(OWNER))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(ids(afterJson)).doesNotContain(soleHr);
	}

	@Test
	void listIncludesApprovals() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long id = idOf(mockMvc.perform(submitFor(employee, createType(true), "2026-10-05", "2026-10-05").with(HR)));
		mockMvc.perform(decide(id, "approve", null).with(HR)).andExpect(status().isOk());

		mockMvc.perform(get("/api/leave-requests?employeeId=" + employee).with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(id))
			.andExpect(jsonPath("$[0].nextStage").value("OWNER"))
			.andExpect(jsonPath("$[0].approvals.length()").value(1))
			.andExpect(jsonPath("$[0].approvals[0].stage").value("HR"));
	}

	// ---- Helper ----

	private static List<Long> ids(String json) {
		List<Integer> raw = com.jayway.jsonpath.JsonPath.read(json, "$.items[*].id");
		return raw.stream().map(Integer::longValue).toList();
	}

	private static String statusOf(String json, long id) {
		List<String> found = com.jayway.jsonpath.JsonPath.read(json, "$.items[?(@.id==" + id + ")].status");
		return found.get(0);
	}

	private long createType(boolean ownerApproval) throws Exception {
		return createType(ownerApproval, false);
	}

	private long createType(boolean ownerApproval, boolean requiresDocument) throws Exception {
		return idOf(mockMvc.perform(post("/api/leave-types").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + unique("Jenis ") + "\",\"requiresDocument\":" + requiresDocument
					+ ",\"active\":true,"
					+ "\"requiresOwnerApproval\":" + ownerApproval + ",\"letterTemplate\":\"UMUM\"}"))
			.andExpect(status().isCreated()));
	}

	private static MockHttpServletRequestBuilder submitFor(Long employee, long type, String start, String end) {
		return post("/api/leave-requests").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"employeeId\":" + employee + ",\"leaveTypeId\":" + type + ",\"startDate\":\"" + start
					+ "\",\"endDate\":\"" + end + "\",\"reason\":\"Dicatat HR\"}");
	}

	private static MockHttpServletRequestBuilder decide(long id, String action, String note) {
		return post("/api/leave-requests/" + id + "/" + action).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(note == null ? "{}" : "{\"note\":\"" + note + "\"}");
	}

	private List<String> auditActions(long requestId) {
		return jdbc.queryForList(
				"select action from audit_logs where entity_type = 'LEAVE_REQUEST' and entity_id = ? order by id",
				String.class, String.valueOf(requestId));
	}

	private static <T> List<T> concurrently(int threads, Callable<T> task) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<T>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					start.await();
					return task.call();
				}));
			}
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get());
			}
			return results;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
