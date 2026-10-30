package com.biometricattendance.overtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.biometricattendance.AttendanceFixtures;
import com.biometricattendance.attendance.AttendanceService;
import com.jayway.jsonpath.JsonPath;

/**
 * Validasi lembur (FR-REV-003, K-140): kandidat diambil dari rekap harian yang sudah ada; Owner atau Finance &
 * Accounting menyetujui seluruhnya, sebagian (catatan wajib), atau menolak (0 menit, catatan wajib).
 */
class OvertimeValidationTests extends AttendanceFixtures {

	private static final String DATE = "2026-10-05";

	@Autowired
	private OvertimeService overtime;

	@Autowired
	private AttendanceService attendance;

	@Autowired
	private DataSource dataSource;

	private record Setup(long branch, long employee, String pin, String sn) {
	}

	/** Aturan uji 08.00–17.00, lembur mulai 17.00; scan 08.00 dan 19.00 → kandidat 2 jam. */
	private Setup overtimeDay() throws Exception {
		return overtimeDay("19:00:00");
	}

	private Setup overtimeDay(String checkOut) throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		long employee = createEmployee(branch, pin);
		addFullRule(employee);
		String sn = uniqueSerial();
		createDevice(branch, sn, ACCEPT_FROM);
		postAttlog(sn, lines(line(pin, DATE + " 08:00:00"), line(pin, DATE + " " + checkOut))).andExpect(status().isOk());
		return new Setup(branch, employee, pin, sn);
	}

	private ResultActions list(long branch) throws Exception {
		return mockMvc.perform(get("/api/overtime").param("from", DATE)
			.param("to", DATE)
			.param("branchId", String.valueOf(branch))
			.with(HR));
	}

	private static String item(long employee, String date, long minutes, long expected, String note) {
		return "{\"employeeId\":" + employee + ",\"workDate\":\"" + date + "\",\"approvedMinutes\":" + minutes
				+ ",\"expectedCandidateSeconds\":" + expected
				+ (note == null ? "" : ",\"note\":\"" + note + "\"") + "}";
	}

	private ResultActions validate(String... items) throws Exception {
		return mockMvc.perform(post("/api/overtime/validations").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"items\":[" + String.join(",", items) + "]}"));
	}

	@Test
	void candidateAfterWorkEndIsListedAsNotValidated() throws Exception {
		Setup s = overtimeDay();
		String body = mockMvc
			.perform(get("/api/attendance/employees/" + s.employee()).param("from", DATE).param("to", DATE).with(HR))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long candidate = ((Number) JsonPath.read(body, "$.days[0].result.overtimeCandidateSeconds")).longValue();
		assertThat(candidate).isEqualTo(7200);

		list(s.branch()).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].employeeId").value(s.employee()))
			.andExpect(jsonPath("$[0].date").value(DATE))
			.andExpect(jsonPath("$[0].checkOut").value("19:00:00"))
			.andExpect(jsonPath("$[0].candidateSeconds").value(candidate))
			.andExpect(jsonPath("$[0].approvedSeconds").doesNotExist())
			.andExpect(jsonPath("$[0].status").value("BELUM_DIVALIDASI"));
	}

	@Test
	void dayWithoutCandidateIsNotListed() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		long employee = createEmployee(branch, pin);
		addFullRule(employee);
		String sn = uniqueSerial();
		createDevice(branch, sn, ACCEPT_FROM);
		postAttlog(sn, lines(line(pin, DATE + " 08:00:00"), line(pin, DATE + " 17:00:00"))).andExpect(status().isOk());

		list(branch).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void approveFullCandidate() throws Exception {
		Setup s = overtimeDay();
		validate(item(s.employee(), DATE, 120, 7200, null)).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].status").value("TERVALIDASI"))
			.andExpect(jsonPath("$[0].approvedSeconds").value(7200))
			.andExpect(jsonPath("$[0].validatedByName").value("hr-absensi"));

		list(s.branch()).andExpect(jsonPath("$[0].status").value("TERVALIDASI"))
			.andExpect(jsonPath("$[0].approvedSeconds").value(7200));
		Map<String, Object> audit = jdbc.queryForMap(
				"select a.actor_username, a.before_data, a.after_data from audit_logs a "
						+ "join overtime_validations v on a.entity_id = cast(v.id as varchar) "
						+ "where a.action = 'OVERTIME_VALIDATE' and a.entity_type = 'OVERTIME_VALIDATION' "
						+ "and v.employee_id = ?",
				s.employee());
		assertThat(audit.get("actor_username")).isEqualTo("hr-absensi");
		assertThat(audit.get("before_data")).isNull();
		assertThat(String.valueOf(audit.get("after_data"))).contains("7200");
	}

	@Test
	void reduceRequiresNote() throws Exception {
		Setup s = overtimeDay();
		validate(item(s.employee(), DATE, 60, 7200, null)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Catatan wajib bila lembur dikurangi atau ditolak"));
		validate(item(s.employee(), DATE, 60, 7200, "  ")).andExpect(status().isBadRequest());

		validate(item(s.employee(), DATE, 60, 7200, "Hanya 1 jam diminta atasan")).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].status").value("TERVALIDASI"))
			.andExpect(jsonPath("$[0].approvedSeconds").value(3600))
			.andExpect(jsonPath("$[0].note").value("Hanya 1 jam diminta atasan"));
	}

	/**
	 * Kandidat yang tidak habis dibagi 60 detik (pulang 19:00:37): menit penuh = floor(kandidat/60) berarti setuju
	 * seluruhnya, disimpan sebesar kandidat apa adanya tanpa catatan; kurang dari itu adalah pengurangan.
	 */
	@Test
	void fullApprovalOfCandidateWithSecondsStoresWholeCandidateWithoutNote() throws Exception {
		Setup s = overtimeDay("19:00:37");
		long candidate = ((Number) JsonPath.read(list(s.branch()).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$[0].candidateSeconds")).longValue();
		assertThat(candidate).isEqualTo(7237);

		validate(item(s.employee(), DATE, 120, candidate, null)).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].status").value("TERVALIDASI"))
			.andExpect(jsonPath("$[0].approvedSeconds").value(candidate));
		assertThat(overtime.validatedSeconds(range(s.branch()))).containsEntry(s.employee(),
				Map.of(LocalDate.parse(DATE), candidate));
	}

	@Test
	void reductionBelowWholeMinutesOfCandidateWithSecondsNeedsNote() throws Exception {
		Setup s = overtimeDay("19:00:37");
		validate(item(s.employee(), DATE, 119, 7237, null)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Catatan wajib bila lembur dikurangi atau ditolak"));
		validate(item(s.employee(), DATE, 121, 7237, "Lebih")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Lembur disetujui tidak boleh melebihi kandidat"));
		assertThat(rowCount(s.employee())).isZero();

		validate(item(s.employee(), DATE, 119, 7237, "Dikurangi")).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].approvedSeconds").value(7140));
	}

	@Test
	void fractionalMinutesAreRejected() throws Exception {
		Setup s = overtimeDay();
		validate("{\"employeeId\":" + s.employee() + ",\"workDate\":\"" + DATE
				+ "\",\"approvedMinutes\":1.5,\"expectedCandidateSeconds\":7200,\"note\":\"Sebagian\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors['items[0].approvedMinutes']")
				.value("Lembur disetujui harus bilangan bulat menit"));
		assertThat(rowCount(s.employee())).isZero();
	}

	@Test
	void cannotApproveMoreThanCandidate() throws Exception {
		Setup s = overtimeDay();
		validate(item(s.employee(), DATE, 121, 7200, "Lebih")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Lembur disetujui tidak boleh melebihi kandidat"));
		assertThat(rowCount(s.employee())).isZero();
	}

	@Test
	void rejectWithZeroMinutes() throws Exception {
		Setup s = overtimeDay();
		validate(item(s.employee(), DATE, 0, 7200, null)).andExpect(status().isBadRequest());

		validate(item(s.employee(), DATE, 0, 7200, "Tidak diminta lembur")).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].status").value("TERVALIDASI"))
			.andExpect(jsonPath("$[0].approvedSeconds").value(0));
	}

	@Test
	void validationCanBeChanged() throws Exception {
		Setup s = overtimeDay();
		validate(item(s.employee(), DATE, 120, 7200, null)).andExpect(status().isOk());
		validate(item(s.employee(), DATE, 90, 7200, "Diubah")).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].approvedSeconds").value(5400));
		assertThat(rowCount(s.employee())).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from audit_logs a join overtime_validations v "
				+ "on a.entity_id = cast(v.id as varchar) where a.action = 'OVERTIME_VALIDATE' and v.employee_id = ?",
				Long.class, s.employee())).isEqualTo(2);
	}

	@Test
	void correctionAfterValidationNeedsRevalidation() throws Exception {
		Setup s = overtimeDay();
		validate(item(s.employee(), DATE, 120, 7200, null)).andExpect(status().isOk());
		assertThat(overtime.validatedSeconds(range(s.branch()))).containsEntry(s.employee(),
				Map.of(LocalDate.parse(DATE), 7200L));

		mockMvc.perform(post("/api/attendance/corrections").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"employeeId\":" + s.employee() + ",\"date\":\"" + DATE
					+ "\",\"checkOut\":\"20:00\",\"reason\":\"Jam pulang sebenarnya\"}"))
			.andExpect(status().isCreated());

		list(s.branch()).andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].candidateSeconds").value(10800))
			.andExpect(jsonPath("$[0].approvedSeconds").value(7200))
			.andExpect(jsonPath("$[0].status").value("PERLU_VALIDASI_ULANG"));
		Map<Long, Map<LocalDate, Long>> validated = overtime.validatedSeconds(range(s.branch()));
		assertThat(validated.getOrDefault(s.employee(), Map.of())).doesNotContainKey(LocalDate.parse(DATE));

		validate(item(s.employee(), DATE, 180, 10800, null)).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].status").value("TERVALIDASI"));
		assertThat(overtime.validatedSeconds(range(s.branch()))).containsEntry(s.employee(),
				Map.of(LocalDate.parse(DATE), 10800L));
	}

	/** Hari yang sudah divalidasi tetap tampil walau kandidatnya hilang setelah koreksi, agar bisa divalidasi ulang. */
	@Test
	void validatedDayStaysListedWhenCandidateDisappears() throws Exception {
		Setup s = overtimeDay();
		validate(item(s.employee(), DATE, 120, 7200, null)).andExpect(status().isOk());

		mockMvc.perform(post("/api/attendance/corrections").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"employeeId\":" + s.employee() + ",\"date\":\"" + DATE
					+ "\",\"checkOut\":\"17:00\",\"reason\":\"Pulang tepat waktu\"}"))
			.andExpect(status().isCreated());

		list(s.branch()).andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].candidateSeconds").value(0))
			.andExpect(jsonPath("$[0].approvedSeconds").value(7200))
			.andExpect(jsonPath("$[0].status").value("PERLU_VALIDASI_ULANG"));

		validate(item(s.employee(), DATE, 0, 0, null)).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].status").value("TERVALIDASI"))
			.andExpect(jsonPath("$[0].approvedSeconds").value(0));
	}

	@Test
	void atMost200ItemsPerRequest() throws Exception {
		Setup s = overtimeDay();
		String[] items = new String[201];
		LocalDate date = LocalDate.parse(DATE);
		for (int i = 0; i < items.length; i++) {
			items[i] = item(s.employee(), date.minusDays(i).toString(), 0, 0, "x");
		}
		validate(items).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.items").value("Maksimal 200 lembur sekali validasi"));
		assertThat(rowCount(s.employee())).isZero();
	}

	@Test
	void staleExpectedCandidateIsRejected() throws Exception {
		Setup s = overtimeDay();
		validate(item(s.employee(), DATE, 60, 3600, null)).andExpect(status().isConflict());
		assertThat(rowCount(s.employee())).isZero();
	}

	@Test
	void futureDateIsRejected() throws Exception {
		Setup s = overtimeDay();
		String tomorrow = attendance.today().plusDays(1).toString();
		validate(item(s.employee(), tomorrow, 0, 0, "x")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Tanggal lembur tidak boleh di masa depan"));
	}

	@Test
	void batchIsAtomic() throws Exception {
		Setup a = overtimeDay();
		Setup b = overtimeDay();
		validate(item(a.employee(), DATE, 120, 7200, null), item(b.employee(), DATE, 30, 7200, null))
			.andExpect(status().isBadRequest());
		assertThat(rowCount(a.employee())).isZero();
		assertThat(rowCount(b.employee())).isZero();

		validate(item(a.employee(), DATE, 120, 7200, null), item(b.employee(), DATE, 30, 7200, "Sebagian"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)));
	}

	/**
	 * Dua validator benar-benar bersamaan: tabel dikunci dari koneksi lain sampai kedua permintaan sama-sama tertahan
	 * di INSERT, sehingga keduanya pasti sudah membaca "belum ada validasi". Tanpa kunci ini permintaan kedua bisa
	 * berjalan setelah yang pertama selesai, dan itu sah sebagai pengubahan validasi (bukan konflik).
	 */
	@Test
	void concurrentValidationOfSameDay() throws Exception {
		Setup s = overtimeDay();
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService holder = Executors.newSingleThreadExecutor();
		try {
			Future<?> lock = holder.submit(() -> {
				try (Connection c = dataSource.getConnection()) {
					c.setAutoCommit(false);
					try (Statement st = c.createStatement()) {
						st.execute("lock table overtime_validations in share row exclusive mode");
					}
					locked.countDown();
					release.await(30, TimeUnit.SECONDS);
					c.rollback();
				}
				return null;
			});
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
			Callable<Integer> task = () -> validate(item(s.employee(), DATE, 120, 7200, null)).andReturn()
				.getResponse()
				.getStatus();
			ExecutorService pool = Executors.newFixedThreadPool(2);
			try {
				Future<Integer> first = pool.submit(task);
				Future<Integer> second = pool.submit(task);
				waitForBlockedInserts(2);
				release.countDown();
				lock.get(10, TimeUnit.SECONDS);
				assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder(200, 409);
			}
			finally {
				pool.shutdownNow();
			}
		}
		finally {
			release.countDown();
			holder.shutdownNow();
		}
		assertThat(rowCount(s.employee())).isEqualTo(1);
	}

	/**
	 * Dua pengubahan bersamaan atas validasi yang SUDAH ADA: keduanya membaca versi yang sama sebelum UPDATE, lalu
	 * {@code @Version} membuat tepat satu sukses dan yang lain 409.
	 */
	@Test
	void concurrentUpdateOfExistingValidation() throws Exception {
		Setup s = overtimeDay();
		validate(item(s.employee(), DATE, 120, 7200, null)).andExpect(status().isOk());
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService holder = Executors.newSingleThreadExecutor();
		try {
			Future<?> lock = holder.submit(() -> {
				try (Connection c = dataSource.getConnection()) {
					c.setAutoCommit(false);
					try (Statement st = c.createStatement()) {
						// SELECT tetap jalan; UPDATE (RowExclusiveLock) tertahan sampai kunci dilepas.
						st.execute("lock table overtime_validations in share row exclusive mode");
					}
					locked.countDown();
					release.await(30, TimeUnit.SECONDS);
					c.rollback();
				}
				return null;
			});
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
			Callable<Integer> task = () -> validate(item(s.employee(), DATE, 60, 7200, "Dikurangi bersamaan"))
				.andReturn()
				.getResponse()
				.getStatus();
			ExecutorService pool = Executors.newFixedThreadPool(2);
			try {
				Future<Integer> first = pool.submit(task);
				Future<Integer> second = pool.submit(task);
				waitForBlockedInserts(2);
				release.countDown();
				lock.get(10, TimeUnit.SECONDS);
				assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder(200, 409);
			}
			finally {
				pool.shutdownNow();
			}
		}
		finally {
			release.countDown();
			holder.shutdownNow();
		}
		assertThat(rowCount(s.employee())).isEqualTo(1);
		Map<String, Object> row = jdbc.queryForMap(
				"select approved_seconds, version from overtime_validations where employee_id = ?", s.employee());
		assertThat(((Number) row.get("approved_seconds")).longValue()).isEqualTo(3600);
		assertThat(((Number) row.get("version")).longValue()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from audit_logs a join overtime_validations v "
				+ "on a.entity_id = cast(v.id as varchar) where a.action = 'OVERTIME_VALIDATE' and v.employee_id = ?",
				Long.class, s.employee())).isEqualTo(2);
	}

	/** Hari setelah tanggal keluar tidak tampil di daftar dan tidak dihitung sebagai lembur sah untuk laporan. */
	@Test
	void daysAfterEndDateAreIgnored() throws Exception {
		Setup s = overtimeDay();
		validate(item(s.employee(), DATE, 120, 7200, null)).andExpect(status().isOk());
		assertThat(overtime.validatedSeconds(range(s.branch()))).containsKey(s.employee());

		jdbc.update("update employees set end_date = ? where id = ?", LocalDate.parse(DATE).minusDays(1), s.employee());

		list(s.branch()).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
		assertThat(range(s.branch())).hasSize(1);
		assertThat(overtime.validatedSeconds(range(s.branch()))).doesNotContainKey(s.employee());
	}

	private void waitForBlockedInserts(int expected) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
		while (System.nanoTime() < deadline) {
			Long waiting = jdbc.queryForObject("""
					select count(*) from pg_locks l join pg_class c on c.oid = l.relation
					where c.relname = 'overtime_validations' and not l.granted
					""", Long.class);
			if (waiting != null && waiting >= expected) {
				return;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("Kedua permintaan tidak sampai ke INSERT dalam 20 detik");
	}

	@Test
	void employeeCannotValidateOvertime() throws Exception {
		Setup s = overtimeDay();
		mockMvc.perform(post("/api/overtime/validations").with(user("karyawan-ot").roles("EMPLOYEE"))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"items\":[" + item(s.employee(), DATE, 120, 7200, null) + "]}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/overtime").with(user("karyawan-ot").roles("EMPLOYEE")))
			.andExpect(status().isForbidden());
		assertThat(rowCount(s.employee())).isZero();
	}

	@Test
	void migrationEnforcesUniqueDayAndApprovedNotAboveCandidate() throws Exception {
		long employee = createEmployee(createBranch(), null);
		insertRow(employee, "2026-10-01", 3600, 3600);
		assertThatThrownBy(() -> insertRow(employee, "2026-10-01", 3600, 0))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ux_overtime_validations_day");
		assertThatThrownBy(() -> insertRow(employee, "2026-10-02", 3600, 3601))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("overtime_validations_check");
	}

	private List<com.biometricattendance.attendance.AttendanceDtos.EmployeeDays> range(long branch) {
		LocalDate date = LocalDate.parse(DATE);
		return attendance.range(date, date, branch, null, null);
	}

	private void insertRow(long employee, String date, long candidate, long approved) {
		jdbc.update("""
				insert into overtime_validations (employee_id, work_date, candidate_seconds, approved_seconds,
				    validated_by, validated_at)
				values (?, cast(? as date), ?, ?, 'test', now())
				""", employee, date, candidate, approved);
	}

	private long rowCount(long employee) {
		return jdbc.queryForObject("select count(*) from overtime_validations where employee_id = ?", Long.class,
				employee);
	}

}
