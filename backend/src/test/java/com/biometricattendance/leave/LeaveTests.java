package com.biometricattendance.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.biometricattendance.AttendanceFixtures;
import com.jayway.jsonpath.JsonPath;

/** Fase 4: izin dan cuti (docs/rencana-fase-4.md, K-070–K-082). Data sintetis. */
class LeaveTests extends AttendanceFixtures {

	private static final byte[] PDF = "%PDF-1.4\n% surat uji sintetis\n".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] PNG = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0 };

	// ---- Alur utama ----

	@Test
	void employeeSubmitsOwnRequestAndHrApprovesIt() throws Exception {
		long employee = createEmployee(createBranch(), null);
		RequestPostProcessor me = employeeAccount(employee);
		long type = createLeaveType(false);

		// employeeId di body diabaikan: karyawan selalu diambil dari akun (K-073).
		var submitted = mockMvc.perform(post("/api/me/leave-requests").with(me)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"employeeId\":1,\"leaveTypeId\":" + type
					+ ",\"startDate\":\"2026-10-05\",\"endDate\":\"2026-10-07\",\"reason\":\"Acara keluarga\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.employeeId").value(employee))
			.andExpect(jsonPath("$.status").value("MENUNGGU"))
			.andExpect(jsonPath("$.calendarDays").value(3));
		long id = idOf(submitted);

		mockMvc.perform(get("/api/leave-requests/pending").with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.hasItem((int) id)))
			.andExpect(jsonPath("$.items[0].id").value((int) id))
			.andExpect(jsonPath("$.items[0].employeeId").value((int) employee));

		mockMvc.perform(decide(id, "approve", "Silakan").with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DISETUJUI"))
			.andExpect(jsonPath("$.decidedBy").value("hr-absensi"))
			.andExpect(jsonPath("$.decisionNote").value("Silakan"));

		mockMvc.perform(get("/api/me/leave-requests").with(me))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(id))
			.andExpect(jsonPath("$[0].status").value("DISETUJUI"));
		assertThat(auditActions(id)).containsExactly("CREATE", "APPROVE");
	}

	@Test
	void hrCanRecordLeaveOnBehalfOfEmployee() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long type = createLeaveType(false);

		mockMvc.perform(submitFor(employee, type, "2026-10-05", "2026-10-05").with(HR))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.employeeId").value(employee))
			.andExpect(jsonPath("$.submittedBy").value("hr-absensi"));

		mockMvc.perform(submitFor(null, type, "2026-10-06", "2026-10-06").with(HR))
			.andExpect(status().isBadRequest());
	}

	/** K-074: penolakan wajib alasan; pengajuan yang sudah diputus tidak bisa diputus lagi. */
	@Test
	void rejectionNeedsReasonAndDecidedRequestsCannotBeDecidedAgain() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long id = idOf(mockMvc.perform(submitFor(employee, createLeaveType(false), "2026-10-05", "2026-10-05").with(HR)));

		mockMvc.perform(decide(id, "reject", null).with(HR)).andExpect(status().isBadRequest());
		mockMvc.perform(decide(id, "reject", " ").with(HR)).andExpect(status().isBadRequest());
		mockMvc.perform(decide(id, "reject", "Bentrok jadwal produksi").with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DITOLAK"))
			.andExpect(jsonPath("$.decisionNote").value("Bentrok jadwal produksi"));

		mockMvc.perform(decide(id, "approve", null).with(OWNER)).andExpect(status().isConflict());
		mockMvc.perform(decide(id, "cancel", "x").with(OWNER)).andExpect(status().isConflict());
		assertThat(auditActions(id)).containsExactly("CREATE", "REJECT");
	}

	@Test
	void approvedLeaveCanBeCancelledByManagerWithReasonButNotByEmployee() throws Exception {
		long employee = createEmployee(createBranch(), null);
		RequestPostProcessor me = employeeAccount(employee);
		long type = createLeaveType(false);
		long pending = idOf(mockMvc.perform(submitMine(type, "2026-10-01", "2026-10-01").with(me)));
		mockMvc.perform(decide(pending, "cancel", null, true).with(me))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DIBATALKAN"))
			.andExpect(jsonPath("$.cancelledBy").exists());

		long approved = idOf(mockMvc.perform(submitMine(type, "2026-10-05", "2026-10-06").with(me)));
		mockMvc.perform(decide(approved, "approve", null).with(HR)).andExpect(status().isOk());
		mockMvc.perform(decide(approved, "cancel", null, true).with(me)).andExpect(status().isConflict());
		mockMvc.perform(decide(approved, "cancel", null).with(HR)).andExpect(status().isBadRequest());
		mockMvc.perform(decide(approved, "cancel", "Ternyata masuk kerja").with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DIBATALKAN"))
			.andExpect(jsonPath("$.cancelReason").value("Ternyata masuk kerja"));
	}

	@Test
	void invalidDatesAndInactiveTypesAreRejected() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long type = createLeaveType(false);

		mockMvc.perform(submitFor(employee, type, "2026-10-07", "2026-10-05").with(HR)).andExpect(status().isBadRequest());
		// Batas teknis 366 hari kalender (K-080): 366 hari masih boleh, 367 hari ditolak.
		mockMvc.perform(submitFor(employee, type, "2026-01-01", "2027-01-02").with(HR)).andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/leave-types/" + type).with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + unique("Nonaktif ") + "\",\"requiresDocument\":false,\"active\":false}"))
			.andExpect(status().isOk());
		mockMvc.perform(submitFor(employee, type, "2026-10-05", "2026-10-05").with(HR)).andExpect(status().isBadRequest());
	}

	// ---- Tumpang tindih (K-075) ----

	@Test
	void overlappingActiveRequestsAreRejectedButRejectedOrCancelledOnesDoNotBlock() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long type = createLeaveType(false);
		long first = idOf(mockMvc.perform(submitFor(employee, type, "2026-10-05", "2026-10-07").with(HR)));

		mockMvc.perform(submitFor(employee, type, "2026-10-07", "2026-10-09").with(HR))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("bertabrakan")));
		mockMvc.perform(decide(first, "approve", null).with(HR)).andExpect(status().isOk());
		mockMvc.perform(submitFor(employee, type, "2026-10-03", "2026-10-05").with(HR)).andExpect(status().isConflict());

		// Hari yang bersebelahan bukan tumpang tindih.
		long next = idOf(mockMvc.perform(submitFor(employee, type, "2026-10-08", "2026-10-08").with(HR))
			.andExpect(status().isCreated()));
		mockMvc.perform(decide(next, "reject", "Tidak bisa").with(HR)).andExpect(status().isOk());
		mockMvc.perform(submitFor(employee, type, "2026-10-08", "2026-10-09").with(HR)).andExpect(status().isCreated());

		mockMvc.perform(decide(first, "cancel", "Batal").with(HR)).andExpect(status().isOk());
		mockMvc.perform(submitFor(employee, type, "2026-10-05", "2026-10-07").with(HR)).andExpect(status().isCreated());

		// Karyawan lain pada tanggal yang sama tidak terpengaruh.
		long other = createEmployee(createBranch(), null);
		mockMvc.perform(submitFor(other, type, "2026-10-05", "2026-10-07").with(HR)).andExpect(status().isCreated());
	}

	@Test
	void concurrentOverlappingSubmissionsSaveOnlyOne() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long type = createLeaveType(false);

		List<Integer> statuses = concurrently(4,
				() -> mockMvc.perform(submitFor(employee, type, "2026-11-02", "2026-11-04").with(HR))
					.andReturn()
					.getResponse()
					.getStatus());

		assertThat(statuses).containsOnly(201, 409);
		assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
		assertThat(jdbc.queryForObject("select count(*) from leave_requests where employee_id = ?", Long.class,
				employee)).isEqualTo(1);
	}

	@Test
	void concurrentDecisionsOnSameRequestOnlyOneWins() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long id = idOf(mockMvc.perform(submitFor(employee, createLeaveType(false), "2026-11-09", "2026-11-09").with(HR)));

		List<Integer> statuses = concurrently(3,
				() -> mockMvc.perform(decide(id, "approve", null).with(HR)).andReturn().getResponse().getStatus());

		assertThat(statuses).containsOnly(200, 409);
		assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
		assertThat(auditActions(id)).containsExactly("CREATE", "APPROVE");
	}

	// ---- Hak akses (K-073, K-079) ----

	@Test
	void employeesCannotSeeOrTouchOtherEmployeesRequests() throws Exception {
		long branch = createBranch();
		long alice = createEmployee(branch, null);
		long bob = createEmployee(branch, null);
		RequestPostProcessor aliceLogin = employeeAccount(alice);
		RequestPostProcessor bobLogin = employeeAccount(bob);
		long id = idOf(mockMvc.perform(submitMine(createLeaveType(false), "2026-10-05", "2026-10-05").with(aliceLogin)));
		long attachment = idOf(mockMvc.perform(upload("/api/me/leave-requests/" + id, "surat.pdf", PDF).with(aliceLogin))
			.andExpect(status().isCreated()));

		mockMvc.perform(get("/api/me/leave-requests/" + id).with(bobLogin)).andExpect(status().isNotFound());
		mockMvc.perform(decide(id, "cancel", null, true).with(bobLogin)).andExpect(status().isNotFound());
		mockMvc.perform(upload("/api/me/leave-requests/" + id, "x.pdf", PDF).with(bobLogin))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/me/leave-requests/" + id + "/attachments/" + attachment).with(bobLogin))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/me/leave-requests").with(bobLogin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/me/leave-requests/" + id + "/attachments/" + attachment).with(aliceLogin))
			.andExpect(status().isOk())
			.andExpect(content().bytes(PDF));
	}

	@Test
	void managersCannotDecideTheirOwnRequest() throws Exception {
		long hrEmployee = createEmployee(createBranch(), null);
		String username = createAccount(hrEmployee, "HR_FINANCE");
		RequestPostProcessor hrLogin = user(username).roles("HR_FINANCE");
		long id = idOf(mockMvc.perform(submitMine(createLeaveType(false), "2026-10-05", "2026-10-05").with(hrLogin)));

		mockMvc.perform(decide(id, "approve", null).with(hrLogin)).andExpect(status().isForbidden());
		mockMvc.perform(decide(id, "reject", "x").with(hrLogin)).andExpect(status().isForbidden());
		mockMvc.perform(decide(id, "approve", null).with(OWNER)).andExpect(status().isOk());
	}

	// ---- Lampiran dan wajib surat (K-077, K-078) ----

	@Test
	void typeRequiringDocumentCannotBeApprovedWithoutAttachment() throws Exception {
		long employee = createEmployee(createBranch(), null);
		RequestPostProcessor me = employeeAccount(employee);
		long id = idOf(mockMvc.perform(submitMine(createLeaveType(true), "2026-10-05", "2026-10-06").with(me))
			.andExpect(jsonPath("$.requiresDocument").value(true)));

		mockMvc.perform(decide(id, "approve", null).with(HR))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("wajib surat")));

		mockMvc.perform(upload("/api/me/leave-requests/" + id, "C:\\Users\\budi\\surat dokter.pdf", PDF).with(me))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.originalName").value("surat dokter.pdf"))
			.andExpect(jsonPath("$.contentType").value("application/pdf"))
			.andExpect(jsonPath("$.sizeBytes").value(PDF.length));
		mockMvc.perform(decide(id, "approve", null).with(HR)).andExpect(status().isOk());

		// Setelah diputus, lampiran tidak bisa ditambah lagi.
		mockMvc.perform(upload("/api/leave-requests/" + id, "lain.png", PNG).with(HR)).andExpect(status().isConflict());
	}

	@Test
	void attachmentsAreValidatedByContentSizeAndCount() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long id = idOf(mockMvc.perform(submitFor(employee, createLeaveType(false), "2026-10-05", "2026-10-05").with(HR)));
		String base = "/api/leave-requests/" + id;

		// File program yang diganti namanya menjadi .pdf ditolak karena isinya bukan PDF.
		mockMvc.perform(upload(base, "surat.pdf", "MZ\u0090\u0000program".getBytes(StandardCharsets.ISO_8859_1)).with(HR))
			.andExpect(status().isBadRequest());
		mockMvc.perform(upload(base, "kosong.pdf", new byte[0]).with(HR)).andExpect(status().isBadRequest());
		byte[] big = new byte[(int) AttachmentStorage.MAX_BYTES + 1];
		System.arraycopy(PDF, 0, big, 0, PDF.length);
		mockMvc.perform(upload(base, "besar.pdf", big).with(HR)).andExpect(status().isPayloadTooLarge());

		for (int i = 0; i < LeaveService.MAX_ATTACHMENTS; i++) {
			mockMvc.perform(upload(base, "foto" + i + ".png", PNG).with(HR)).andExpect(status().isCreated());
		}
		mockMvc.perform(upload(base, "lagi.png", PNG).with(HR)).andExpect(status().isBadRequest());

		mockMvc.perform(get(base).with(HR))
			.andExpect(jsonPath("$.attachments.length()").value(LeaveService.MAX_ATTACHMENTS))
			.andExpect(jsonPath("$.attachments[0].contentType").value("image/png"));
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_logs where entity_type = 'LEAVE_ATTACHMENT' and action = 'UPLOAD'"
						+ " and after_data like ?",
				Long.class, "%\"leaveRequestId\":" + id + ",%")).isEqualTo(LeaveService.MAX_ATTACHMENTS);
	}

	@Test
	void downloadIsForcedAsAttachmentAndManagerViewsAreAudited() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long id = idOf(mockMvc.perform(submitFor(employee, createLeaveType(false), "2026-10-05", "2026-10-05").with(HR)));
		long attachment = idOf(mockMvc.perform(upload("/api/leave-requests/" + id, "surat.pdf", PDF).with(HR)));

		mockMvc.perform(get("/api/leave-requests/" + id + "/attachments/" + attachment).with(OWNER))
			.andExpect(status().isOk())
			.andExpect(content().contentType("application/pdf"))
			.andExpect(content().bytes(PDF))
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
		mockMvc.perform(get("/api/leave-requests/" + id + "/attachments/" + (attachment + 999_999)).with(OWNER))
			.andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_logs where entity_type = 'LEAVE_ATTACHMENT' and action = 'VIEW'"
						+ " and entity_id = ?",
				Long.class, String.valueOf(attachment))).isEqualTo(1);
	}

	// ---- Rekap harian (K-076) dan pemakaian (K-072) ----

	@Test
	void onlyApprovedLeaveChangesDailyRecap() throws Exception {
		long employee = createEmployee(createBranch(), null);
		addFullRule(employee);
		long type = createLeaveType(false);
		String recap = "/api/attendance/employees/" + employee + "?from=2026-10-05&to=2026-10-06";

		mockMvc.perform(get(recap).with(HR)).andExpect(jsonPath("$.days[0].result.status").value("TIDAK_HADIR"));

		long id = idOf(mockMvc.perform(submitFor(employee, type, "2026-10-05", "2026-10-05").with(HR)));
		mockMvc.perform(get(recap).with(HR))
			.andExpect(jsonPath("$.days[0].result.status").value("TIDAK_HADIR"))
			.andExpect(jsonPath("$.days[0].result.flags[0]").value("IZIN_MENUNGGU"))
			.andExpect(jsonPath("$.days[0].leave.status").value("MENUNGGU"))
			.andExpect(jsonPath("$.days[1].leave").doesNotExist());

		mockMvc.perform(decide(id, "approve", null).with(HR)).andExpect(status().isOk());
		mockMvc.perform(get(recap).with(HR))
			.andExpect(jsonPath("$.days[0].result.status").value("IZIN"))
			.andExpect(jsonPath("$.days[0].leave.requestId").value(id))
			.andExpect(jsonPath("$.days[0].leave.status").value("DISETUJUI"))
			.andExpect(jsonPath("$.days[1].result.status").value("TIDAK_HADIR"));

		mockMvc.perform(decide(id, "cancel", "Batal").with(HR)).andExpect(status().isOk());
		mockMvc.perform(get(recap).with(HR))
			.andExpect(jsonPath("$.days[0].result.status").value("TIDAK_HADIR"))
			.andExpect(jsonPath("$.days[0].leave").doesNotExist());
	}

	@Test
	void scansDuringApprovedLeaveAreKeptAndFlagged() throws Exception {
		long branch = createBranch();
		String sn = uniqueSerial();
		createDevice(branch, sn, ACCEPT_FROM);
		String pin = uniquePin();
		long employee = createEmployee(branch, pin);
		addFullRule(employee);
		long id = idOf(mockMvc.perform(submitFor(employee, createLeaveType(false), "2026-10-07", "2026-10-07").with(HR)));
		mockMvc.perform(decide(id, "approve", null).with(HR)).andExpect(status().isOk());

		postAttlog(sn, lines(line(pin, "2026-10-07 08:00:00"), line(pin, "2026-10-07 17:00:00")))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/attendance/employees/" + employee + "?from=2026-10-07&to=2026-10-07").with(HR))
			.andExpect(jsonPath("$.days[0].result.status").value("LENGKAP"))
			.andExpect(jsonPath("$.days[0].result.flags").value(org.hamcrest.Matchers.hasItem("HADIR_SAAT_IZIN")))
			.andExpect(jsonPath("$.days[0].scans.length()").value(2));
	}

	@Test
	void dashboardCountsApprovedLeaveAndPendingRequests() throws Exception {
		long employee = createEmployee(createBranch(), null);
		addFullRule(employee);
		long type = createLeaveType(false);
		long approved = idOf(mockMvc.perform(submitFor(employee, type, "2026-12-01", "2026-12-01").with(HR)));
		mockMvc.perform(decide(approved, "approve", null).with(HR)).andExpect(status().isOk());
		long pendingBefore = jdbc.queryForObject("select count(*) from leave_requests where status = 'MENUNGGU'",
				Long.class);
		mockMvc.perform(submitFor(employee, type, "2026-12-02", "2026-12-02").with(HR)).andExpect(status().isCreated());

		MvcResult summary = mockMvc.perform(get("/api/dashboard/summary?date=2026-12-01").with(HR))
			.andExpect(status().isOk())
			.andReturn();
		String json = summary.getResponse().getContentAsString();
		assertThat(((Number) JsonPath.read(json, "$.totals.onLeave")).intValue()).isGreaterThanOrEqualTo(1);
		assertThat(((Number) JsonPath.read(json, "$.pendingLeaveRequests")).longValue())
			.isGreaterThanOrEqualTo(pendingBefore + 1);
	}

	@Test
	void usageCountsApprovedDaysPerTypeClippedToTheYear() throws Exception {
		long employee = createEmployee(createBranch(), null);
		addFullRule(employee);
		long type = createLeaveType(false);
		long id = idOf(mockMvc.perform(submitFor(employee, type, "2026-12-30", "2027-01-02").with(HR)));
		mockMvc.perform(decide(id, "approve", null).with(HR)).andExpect(status().isOk());
		mockMvc.perform(submitFor(employee, type, "2026-11-02", "2026-11-02").with(HR)).andExpect(status().isCreated());

		mockMvc.perform(get("/api/leave-requests/usage?employeeId=" + employee + "&year=2026").with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.types.length()").value(1))
			.andExpect(jsonPath("$.types[0].leaveTypeId").value(type))
			.andExpect(jsonPath("$.types[0].requests").value(1))
			.andExpect(jsonPath("$.types[0].calendarDays").value(2))
			.andExpect(jsonPath("$.types[0].workingDays").value(2))
			.andExpect(jsonPath("$.pendingRequests").value(1));
		mockMvc.perform(get("/api/leave-requests/usage?employeeId=" + employee + "&year=2027").with(HR))
			.andExpect(jsonPath("$.types[0].calendarDays").value(2));

		mockMvc.perform(get("/api/leave-requests/" + id).with(HR))
			.andExpect(jsonPath("$.calendarDays").value(4))
			.andExpect(jsonPath("$.workingDays").value(4));
	}

	/** Hari kerja tidak ditebak bila aturan hari kerja belum diatur untuk karyawan itu. */
	@Test
	void workingDaysAreUnknownWhenWorkDaysAreNotConfigured() throws Exception {
		long employee = createEmployee(createBranch(), null);
		long id = idOf(mockMvc.perform(submitFor(employee, createLeaveType(false), "2019-06-03", "2019-06-04").with(HR)));

		mockMvc.perform(get("/api/leave-requests/" + id).with(HR))
			.andExpect(jsonPath("$.calendarDays").value(2))
			.andExpect(jsonPath("$.workingDays").doesNotExist());
	}

	// ---- Jenis izin (K-070) ----

	@Test
	void leaveTypesAreManagedByOwnerAndEmployeesSeeOnlyActiveOnes() throws Exception {
		String name = unique("Cuti Uji ");
		long id = idOf(mockMvc.perform(post("/api/leave-types").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + name + "\",\"requiresDocument\":true,\"paid\":null,\"active\":true}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.paid").doesNotExist()));
		mockMvc.perform(post("/api/leave-types").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + name.toUpperCase() + "\",\"requiresDocument\":false,\"active\":true}"))
			.andExpect(status().isConflict());

		mockMvc.perform(put("/api/leave-types/" + id).with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + name + "\",\"requiresDocument\":true,\"paid\":true,\"active\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paid").value(true));

		long employee = createEmployee(createBranch(), null);
		mockMvc.perform(get("/api/leave-types").with(employeeAccount(employee)))
			.andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
		mockMvc.perform(get("/api/leave-types").with(HR)).andExpect(jsonPath("$[?(@.id == " + id + ")]").isNotEmpty());
		assertThat(jdbc.queryForList("select action from audit_logs where entity_type = 'LEAVE_TYPE' and entity_id = ?"
				+ " order by id", String.class, String.valueOf(id))).containsExactly("CREATE", "UPDATE");
	}

	@Test
	void seededExampleTypesExist() {
		assertThat(jdbc.queryForList("select name from leave_types", String.class)).contains("Sakit", "Cuti", "Izin");
	}

	// ---- Notifikasi (K-081) ----

	@Test
	void managersAreNotifiedLiveWhenAnEmployeeSubmits() throws Exception {
		long employee = createEmployee(createBranch(), null);
		RequestPostProcessor me = employeeAccount(employee);
		long type = createLeaveType(false);
		MvcResult stream = mockMvc.perform(get("/api/dashboard/stream").with(HR))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted())
			.andReturn();

		long id = idOf(mockMvc.perform(submitMine(type, "2026-10-05", "2026-10-05").with(me)));

		String events = stream.getResponse().getContentAsString();
		assertThat(events).contains("\"type\":\"LEAVE\"").contains("\"action\":\"SUBMITTED\"")
			.contains("\"requestId\":" + id);
	}

	// ---- Helper ----

	private long createLeaveType(boolean requiresDocument) throws Exception {
		return idOf(mockMvc.perform(post("/api/leave-types").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + unique("Jenis ") + "\",\"requiresDocument\":" + requiresDocument
					+ ",\"active\":true}"))
			.andExpect(status().isCreated()));
	}

	private String createAccount(long employeeId, String role) throws Exception {
		String username = unique("akun-");
		mockMvc.perform(post("/api/users").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"" + username + "\",\"password\":\"rahasia-uji-123\",\"role\":\"" + role
					+ "\",\"employeeId\":" + employeeId + "}"))
			.andExpect(status().isCreated());
		return username;
	}

	private RequestPostProcessor employeeAccount(long employeeId) throws Exception {
		return user(createAccount(employeeId, "EMPLOYEE")).roles("EMPLOYEE");
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder submitMine(long type,
			String start, String end) {
		return post("/api/me/leave-requests").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"leaveTypeId\":" + type + ",\"startDate\":\"" + start + "\",\"endDate\":\"" + end
					+ "\",\"reason\":\"Keperluan uji\"}");
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder submitFor(Long employee,
			long type, String start, String end) {
		return post("/api/leave-requests").with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{" + (employee == null ? "" : "\"employeeId\":" + employee + ",") + "\"leaveTypeId\":" + type
					+ ",\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\",\"reason\":\"Dicatat HR\"}");
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder decide(long id,
			String action, String note) {
		return decide(id, action, note, false);
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder decide(long id,
			String action, String note, boolean mine) {
		String base = mine ? "/api/me/leave-requests/" : "/api/leave-requests/";
		return post(base + id + "/" + action).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(note == null ? "{}" : "{\"note\":\"" + note + "\"}");
	}

	private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder upload(
			String requestPath, String fileName, byte[] bytes) {
		return (org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder) multipart(
				requestPath + "/attachments")
			.file(new MockMultipartFile("file", fileName, "application/octet-stream", bytes))
			.with(csrf());
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
