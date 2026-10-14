# Rekan Kerja Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Semua pengguna yang login, terutama role Karyawan, bisa melihat absensi semua karyawan aktif per tanggal dan riwayat per orang per bulan, tanpa data sensitif.

**Architecture:** Paket backend baru `team` membungkus `AttendanceService` yang sudah ada dan menyalin hasilnya ke DTO berisi kolom yang diizinkan saja. Tidak ada schema baru. Frontend menambah dua halaman (`/rekan-kerja`, `/rekan-kerja/:id`) dan satu menu untuk role Karyawan.

**Tech Stack:** Spring Boot 4 / Java 21 / MockMvc + Testcontainers; React + Vite + TypeScript, TanStack Query, Vitest + Testing Library, shadcn/ui.

**Spec:** [`docs/rencana-rekan-kerja.md`](../../rencana-rekan-kerja.md)

## Global Constraints

- Hanya kolom di spec §2 yang boleh keluar dari endpoint `/api/team/**`. Dilarang mengirim `phone`, `devicePin`, kategori, jenis izin, ID pengajuan, alasan, scan, flag, koreksi, atau `TimeValue.source`.
- Endpoint `/api/team/**` (GET) terbuka untuk **semua role yang login** dan wajib masuk matriks `AccessControlTests` (CLAUDE.md).
- Tidak ada perubahan schema maupun migration.
- Error memakai ProblemDetail lewat `ApiException`, pesan dalam Bahasa Indonesia.
- Rentang riwayat maksimal 62 hari (`AttendanceService.MAX_RANGE_DAYS` + `requireRange`, sudah dipanggil di `forEmployee`).
- Panggilan API frontend lewat `src/lib/api.ts`. Komponen `src/components/ui` tidak diedit.
- Dokumentasi dalam Bahasa Indonesia.
- **Commit hanya dengan izin pengguna (CLAUDE.md).** Rencana ini tidak memuat langkah commit per task. Commit ditawarkan di Task 5.
- Jangan pernah melaporkan test lulus tanpa benar-benar menjalankannya.

## Review Focus

1. **Izin disetujui di hari kerja yang aturan kerjanya belum diatur**: harus tetap `IZIN`, bukan `BELUM_DIATUR`, sama seperti `dayStatus` di frontend pengelola. Test: `approvedLeaveWithoutRuleIsStillIzin` di Task 1.
2. **Kolom jabatan kosong (`position` null)**: halaman tidak boleh crash dan harus menampilkan "—". Test: data `position: null` di `TeamPages.test.tsx`, Task 3.
3. **Bulan berjalan pada halaman riwayat**: hari setelah hari ini tidak boleh diminta, karena akan tampil "Tidak hadir" palsu. `monthRange` memotong `to` di hari ini. Test: `team.test.ts`, Task 2.
4. **Pengelola tanpa data karyawan terhubung membuka `/api/team/**`**: tetap 200, karena endpoint tidak bergantung pada akun yang terhubung. Test: matriks di Task 1.
5. **Cari dengan huruf besar/kecil atau No. karyawan**: pencarian tidak peka huruf besar-kecil dan mencocokkan nama maupun nomor. Test: `TeamPages.test.tsx`, Task 3.

---

## File Structure

| File | Tanggung jawab |
| --- | --- |
| Create `backend/src/main/java/com/biometricattendance/team/TeamDtos.java` | Record respons yang hanya berisi kolom diizinkan, `TeamStatus`, dan pemetaan `DayRecord → TeamDay` |
| Create `backend/src/main/java/com/biometricattendance/team/TeamService.java` | Memanggil `AttendanceService`, menyaring karyawan aktif, dan menyusun daftar cabang |
| Create `backend/src/main/java/com/biometricattendance/team/TeamController.java` | `GET /api/team/attendance`, `GET /api/team/employees/{id}/attendance` |
| Modify `backend/src/main/java/com/biometricattendance/config/SecurityConfig.java` | GET `/api/team/**` → `authenticated()` |
| Create `backend/src/test/java/com/biometricattendance/team/TeamTests.java` | Perilaku dan uji kebocoran |
| Modify `backend/src/test/java/com/biometricattendance/security/AccessControlTests.java` | Matriks akses `/api/team/**` |
| Modify `frontend/src/lib/types.ts` | Tipe `Team*` |
| Create `frontend/src/lib/team.ts` + `team.test.ts` | Label/tone status, helper bulan |
| Create `frontend/src/components/TeamStatusBadges.tsx` | Badge status + "Terlambat", dipakai kedua halaman |
| Create `frontend/src/pages/TeamPage.tsx` | Tabel/kartu per tanggal |
| Create `frontend/src/pages/TeamMemberPage.tsx` | Riwayat per bulan + ringkasan |
| Modify `frontend/src/App.tsx`, `frontend/src/navigation.ts`, `frontend/src/navigation.test.ts` | Route dan menu |
| Create `frontend/src/pages/TeamPages.test.tsx` | Test halaman |
| Modify docs (Task 4) | K-083, K-084, Q-016, aturan-bisnis, arsitektur, README, status |

---

### Task 1: Backend `team` (DTO, service, controller, keamanan, test)

**Files:**
- Create: `backend/src/main/java/com/biometricattendance/team/TeamDtos.java`
- Create: `backend/src/main/java/com/biometricattendance/team/TeamService.java`
- Create: `backend/src/main/java/com/biometricattendance/team/TeamController.java`
- Modify: `backend/src/main/java/com/biometricattendance/config/SecurityConfig.java` (setelah baris `.requestMatchers("/api/auth/**", "/api/me/**").authenticated()`)
- Create: `backend/src/test/java/com/biometricattendance/team/TeamTests.java`
- Modify: `backend/src/test/java/com/biometricattendance/security/AccessControlTests.java` (sebelum `dashboardStreamRequiresManagerRole`)

**Interfaces:**
- Consumes: `AttendanceService.today()`, `daily(LocalDate, Long)` → `DailyAttendanceResponse(date, branchId, rows: List<DailyRow>)`, `forEmployee(Long, LocalDate, LocalDate)` → `EmployeeAttendanceResponse(..., days: List<DayRecord>, ...)`; `DayRecord(date, result: DailyResultResponse, ruleMissing, scans, correction, scanFlags, leave: LeaveInfo)`; `EmployeeRepository.findById`/`getReferenceById`; `BranchRepository.findAllByOrderByNameAsc()`; `Employee.isActive()/getPosition()`; `Branch.isActive()/getId()/getName()`.
- Produces (JSON, dipakai Task 2–3):
  - `TeamDailyResponse { date, branchId, branches: [{id, name}], rows: [TeamRow] }`
  - `TeamRow { employeeId, employeeNumber, fullName, branchId, branchName, position, day: TeamDay }`
  - `TeamDay { date, status, checkIn, checkOut, lateSeconds, overtimeSeconds }`. `status` bernilai `HADIR|TIDAK_LENGKAP|TIDAK_HADIR|IZIN|LIBUR|BELUM_DIATUR`; `checkIn`/`checkOut` berupa string `"HH:mm:ss"` atau null.
  - `TeamEmployeeResponse { employeeId, employeeNumber, fullName, branchName, position, from, to, days: [TeamDay] }`

- [ ] **Step 1: Write the failing tests**

`backend/src/test/java/com/biometricattendance/team/TeamTests.java`:

```java
package com.biometricattendance.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.biometricattendance.AttendanceFixtures;

/** Daftar rekan kerja untuk semua role (docs/rencana-rekan-kerja.md, K-083, K-084). Data sintetis. */
class TeamTests extends AttendanceFixtures {

	private static final String SCAN_DAY = "2026-10-05";
	private static final String LEAVE_DAY = "2026-10-06";
	private static final String CORRECTED_DAY = "2026-10-07";
	private static final String PENDING_DAY = "2026-10-08";
	private static final String SECRET_REASON = "Rahasia koreksi HR uji";
	private static final String LEAVE_REASON = "Alasan izin rahasia";

	private record Colleague(long branch, long employee, String pin, String leaveTypeName, RequestPostProcessor login) {
	}

	/**
	 * Satu karyawan dengan semua data sensitif terisi: No. HP, PIN, izin disetujui, koreksi beralasan, dan izin
	 * yang masih menunggu.
	 */
	private Colleague colleagueWithSensitiveData() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		long employee = createEmployee(branch, pin);
		addFullRule(employee);
		jdbc.update("update employees set phone = '081299990000', position = 'Staf gudang' where id = ?", employee);
		String sn = uniqueSerial();
		createDevice(branch, sn, ACCEPT_FROM);
		postAttlog(sn, lines(line(pin, SCAN_DAY + " 08:10:00"), line(pin, SCAN_DAY + " 17:30:00")))
			.andExpect(status().isOk());

		String typeName = unique("Sakit-uji-");
		long type = createLeaveType(typeName);
		approve(idOf(submitLeave(employee, type, LEAVE_DAY)));
		submitLeave(employee, type, PENDING_DAY);

		mockMvc.perform(post("/api/attendance/corrections").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"employeeId\":" + employee + ",\"date\":\"" + CORRECTED_DAY
					+ "\",\"checkIn\":\"08:00\",\"checkOut\":\"17:00\",\"reason\":\"" + SECRET_REASON + "\"}"))
			.andExpect(status().isCreated());

		return new Colleague(branch, employee, pin, typeName, employeeLogin(employee));
	}

	private long createLeaveType(String name) throws Exception {
		return idOf(mockMvc.perform(post("/api/leave-types").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + name + "\",\"requiresDocument\":false,\"active\":true}"))
			.andExpect(status().isCreated()));
	}

	private ResultActions submitLeave(long employee, long type, String day) throws Exception {
		return mockMvc.perform(post("/api/leave-requests").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"employeeId\":" + employee + ",\"leaveTypeId\":" + type + ",\"startDate\":\"" + day
					+ "\",\"endDate\":\"" + day + "\",\"reason\":\"" + LEAVE_REASON + "\"}"))
			.andExpect(status().isCreated());
	}

	private void approve(long requestId) throws Exception {
		mockMvc.perform(post("/api/leave-requests/" + requestId + "/approve").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"note\":\"Cepat sembuh\"}"))
			.andExpect(status().isOk());
	}

	private RequestPostProcessor employeeLogin(long employeeId) throws Exception {
		String username = unique("rekan-");
		mockMvc.perform(post("/api/users").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"" + username + "\",\"password\":\"rahasia-uji-123\",\"role\":\"EMPLOYEE\","
					+ "\"employeeId\":" + employeeId + "}"))
			.andExpect(status().isCreated());
		return user(username).roles("EMPLOYEE");
	}

	private ResultActions daily(RequestPostProcessor who, String date, long branch) throws Exception {
		return mockMvc.perform(get("/api/team/attendance").param("date", date)
			.param("branchId", String.valueOf(branch))
			.with(who)).andExpect(status().isOk());
	}

	private ResultActions history(RequestPostProcessor who, long employee, String from, String to) throws Exception {
		return mockMvc.perform(get("/api/team/employees/" + employee + "/attendance").param("from", from)
			.param("to", to)
			.with(who));
	}

	private static String body(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString();
	}

	@Test
	void employeeSeesColleagueAttendanceWithAllowedColumns() throws Exception {
		Colleague c = colleagueWithSensitiveData();
		// Rekan kerja lain (di cabang lain) yang membuka daftar.
		RequestPostProcessor viewer = employeeLogin(createEmployee(createBranch(), null));

		daily(viewer, SCAN_DAY, c.branch())
			.andExpect(jsonPath("$.date").value(SCAN_DAY))
			.andExpect(jsonPath("$.rows", hasSize(1)))
			.andExpect(jsonPath("$.rows[0].employeeId").value(c.employee()))
			.andExpect(jsonPath("$.rows[0].position").value("Staf gudang"))
			.andExpect(jsonPath("$.rows[0].day.status").value("HADIR"))
			.andExpect(jsonPath("$.rows[0].day.checkIn").value(startsWith("08:10")))
			.andExpect(jsonPath("$.rows[0].day.checkOut").value(startsWith("17:30")))
			.andExpect(jsonPath("$.rows[0].day.lateSeconds").value(600))
			.andExpect(jsonPath("$.rows[0].day.overtimeSeconds").value(1800))
			.andExpect(jsonPath("$.branches[?(@.id == " + c.branch() + ")]").exists());
	}

	@Test
	void responsesNeverContainSensitiveData() throws Exception {
		Colleague c = colleagueWithSensitiveData();
		String[] forbiddenKeys = { "\"phone\"", "\"devicePin\"", "\"categoryName\"", "\"leaveTypeName\"",
				"\"requestId\"", "\"reason\"", "\"scans\"", "\"flags\"", "\"scanFlags\"", "\"correction\"",
				"\"source\"", "\"leave\"", "\"ruleMissing\"", "\"joinDate\"" };
		String[] forbiddenValues = { "081299990000", c.pin(), c.leaveTypeName(), SECRET_REASON, LEAVE_REASON,
				"Cepat sembuh", "KOREKSI" };

		List<String> bodies = new ArrayList<>();
		for (String day : new String[] { SCAN_DAY, LEAVE_DAY, CORRECTED_DAY, PENDING_DAY }) {
			bodies.add(body(daily(c.login(), day, c.branch())));
		}
		bodies.add(body(history(c.login(), c.employee(), SCAN_DAY, PENDING_DAY).andExpect(status().isOk())));

		for (String body : bodies) {
			for (String key : forbiddenKeys) {
				assertThat(body).as("kunci %s tidak boleh ada", key).doesNotContain(key);
			}
			for (String value : forbiddenValues) {
				assertThat(body).as("nilai %s tidak boleh ada", value).doesNotContain(value);
			}
		}
	}

	@Test
	void statusesFollowRecapWithoutRevealingLeaveDetails() throws Exception {
		Colleague c = colleagueWithSensitiveData();

		history(c.login(), c.employee(), SCAN_DAY, PENDING_DAY).andExpect(status().isOk())
			.andExpect(jsonPath("$.days", hasSize(4)))
			.andExpect(jsonPath("$.days[0].status").value("HADIR"))
			// Izin disetujui → "Izin" tanpa jenis.
			.andExpect(jsonPath("$.days[1].status").value("IZIN"))
			.andExpect(jsonPath("$.days[1].checkIn").isEmpty())
			// Jam efektif setelah koreksi, tanpa keterangan sumber.
			.andExpect(jsonPath("$.days[2].status").value("HADIR"))
			.andExpect(jsonPath("$.days[2].checkIn").value(startsWith("08:00")))
			.andExpect(jsonPath("$.days[2].checkOut").value(startsWith("17:00")))
			// Izin menunggu tidak mengubah status (K-076).
			.andExpect(jsonPath("$.days[3].status").value("TIDAK_HADIR"));
	}

	@Test
	void presentDuringApprovedLeaveShowsHadir() throws Exception {
		Colleague c = colleagueWithSensitiveData();
		String sn = uniqueSerial();
		createDevice(c.branch(), sn, ACCEPT_FROM);
		postAttlog(sn, lines(line(c.pin(), LEAVE_DAY + " 08:00:00"), line(c.pin(), LEAVE_DAY + " 17:00:00")))
			.andExpect(status().isOk());

		assertThat(body(daily(c.login(), LEAVE_DAY, c.branch()))).contains("\"status\":\"HADIR\"")
			.doesNotContain("IZIN");
	}

	@Test
	void approvedLeaveWithoutRuleIsStillIzin() throws Exception {
		long branch = createBranch();
		long employee = createEmployee(branch, null);
		approve(idOf(submitLeave(employee, createLeaveType(unique("Cuti-uji-")), LEAVE_DAY)));

		daily(OWNER, LEAVE_DAY, branch).andExpect(jsonPath("$.rows[0].day.status").value("IZIN"));
		daily(OWNER, SCAN_DAY, branch).andExpect(jsonPath("$.rows[0].day.status").value("BELUM_DIATUR"));
	}

	@Test
	void inactiveEmployeesAreHiddenAndTheirHistoryIsNotFound() throws Exception {
		long branch = createBranch();
		long active = createEmployee(branch, null);
		long inactive = createEmployee(branch, null);
		jdbc.update("update employees set active = false where id = ?", inactive);

		daily(HR, SCAN_DAY, branch).andExpect(jsonPath("$.rows", hasSize(1)))
			.andExpect(jsonPath("$.rows[0].employeeId").value(active));
		history(HR, inactive, SCAN_DAY, SCAN_DAY).andExpect(status().isNotFound());
		history(HR, 999_999_999L, SCAN_DAY, SCAN_DAY).andExpect(status().isNotFound());
	}

	@Test
	void historyRangeIsLimited() throws Exception {
		long employee = createEmployee(createBranch(), null);
		history(HR, employee, "2026-01-01", "2026-06-30").andExpect(status().isBadRequest());
		history(HR, employee, "2026-10-10", "2026-10-01").andExpect(status().isBadRequest());
	}

}
```

Tambahkan ke `AccessControlTests.java`, tepat sebelum method `dashboardStreamRequiresManagerRole`:

```java
	/** Daftar rekan kerja dibuka semua role yang login (K-083); kolomnya dibatasi DTO, bukan role. */
	@ParameterizedTest
	@ValueSource(strings = { "/api/team/attendance", "/api/team/attendance?date=2026-10-05&branchId=" + MISSING })
	void teamAttendanceIsOpenToAllSignedInRoles(String url) throws Exception {
		mockMvc.perform(get(url).with(OWNER)).andExpect(status().isOk());
		mockMvc.perform(get(url).with(HR_FINANCE)).andExpect(status().isOk());
		mockMvc.perform(get(url).with(EMPLOYEE)).andExpect(status().isOk());
		mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
	}

	@Test
	void teamMemberHistoryRequiresLoginOnly() throws Exception {
		String url = "/api/team/employees/" + MISSING + "/attendance";
		mockMvc.perform(get(url).with(OWNER)).andExpect(status().isNotFound());
		mockMvc.perform(get(url).with(HR_FINANCE)).andExpect(status().isNotFound());
		mockMvc.perform(get(url).with(EMPLOYEE)).andExpect(status().isNotFound());
		mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
	}
```

(`OWNER`, `HR_FINANCE`, `EMPLOYEE`, dan `MISSING = 999_999_999L` sudah ada di kelas itu. `@ValueSource` dengan konkatenasi konstanta `long` berupa compile-time constant, jadi diperbolehkan.)

- [ ] **Step 2: Run tests to verify they fail**

Run (dari `backend/`): `./mvnw -q test -Dtest=TeamTests,AccessControlTests`
Expected: FAIL. `TeamTests` dan matriks `/api/team/**` mendapat 403 dari `anyRequest().denyAll()`.

- [ ] **Step 3: Write the implementation**

`backend/src/main/java/com/biometricattendance/team/TeamDtos.java`:

```java
package com.biometricattendance.team;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.biometricattendance.attendance.AttendanceDtos.DayRecord;
import com.biometricattendance.calculation.CalculationDtos.DailyResultResponse;
import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.calculation.DailyResult.TimeValue;
import com.biometricattendance.leave.LeaveStatus;

/**
 * Respons daftar rekan kerja (K-083). Sengaja berisi kolom yang diizinkan saja, sehingga data sensitif tidak bisa
 * ikut terkirim walaupun entity atau DTO pengelola bertambah kolom.
 */
public final class TeamDtos {

	private TeamDtos() {
	}

	/** Status sederhana untuk rekan kerja (K-084): izin tanpa jenis, tanpa flag peninjauan. */
	public enum TeamStatus {
		HADIR, TIDAK_LENGKAP, TIDAK_HADIR, IZIN, LIBUR, BELUM_DIATUR
	}

	/** Jam berupa {@link LocalTime} polos; {@link TimeValue#source()} tidak dikirim agar koreksi tidak terlihat. */
	public record TeamDay(LocalDate date, TeamStatus status, LocalTime checkIn, LocalTime checkOut, Long lateSeconds,
			Long overtimeSeconds) {

		static TeamDay from(DayRecord day) {
			DailyResultResponse r = day.result();
			TeamStatus status = statusOf(day);
			if (status == TeamStatus.IZIN || r == null) {
				return new TeamDay(day.date(), status, null, null, null, null);
			}
			return new TeamDay(day.date(), status, time(r.checkIn()), time(r.checkOut()), r.lateSeconds(),
					r.overtimeCandidateSeconds());
		}

		/** Urutannya sama dengan {@code dayStatus} di frontend pengelola: izin disetujui tanpa kehadiran diutamakan. */
		static TeamStatus statusOf(DayRecord day) {
			DailyResultResponse r = day.result();
			boolean approvedLeave = day.leave() != null && day.leave().status() == LeaveStatus.DISETUJUI;
			boolean noPresence = r == null && day.scans().isEmpty() && day.correction() == null;
			if (approvedLeave && ((r != null && r.status() == AttendanceStatus.IZIN) || noPresence)) {
				return TeamStatus.IZIN;
			}
			if (day.ruleMissing() != null || r == null) {
				return TeamStatus.BELUM_DIATUR;
			}
			return switch (r.status()) {
				case LENGKAP -> TeamStatus.HADIR;
				case TIDAK_LENGKAP -> TeamStatus.TIDAK_LENGKAP;
				case TIDAK_HADIR -> TeamStatus.TIDAK_HADIR;
				case TIDAK_ADA_SCAN -> TeamStatus.LIBUR;
				case IZIN -> TeamStatus.IZIN;
			};
		}

		private static LocalTime time(TimeValue value) {
			return value == null ? null : value.time();
		}

	}

	public record BranchOption(Long id, String name) {
	}

	public record TeamRow(Long employeeId, String employeeNumber, String fullName, Long branchId, String branchName,
			String position, TeamDay day) {
	}

	public record TeamDailyResponse(LocalDate date, Long branchId, List<BranchOption> branches, List<TeamRow> rows) {
	}

	public record TeamEmployeeResponse(Long employeeId, String employeeNumber, String fullName, String branchName,
			String position, LocalDate from, LocalDate to, List<TeamDay> days) {
	}

}
```

`backend/src/main/java/com/biometricattendance/team/TeamService.java`:

```java
package com.biometricattendance.team;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.attendance.AttendanceService;
import com.biometricattendance.branch.Branch;
import com.biometricattendance.branch.BranchRepository;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;
import com.biometricattendance.team.TeamDtos.BranchOption;
import com.biometricattendance.team.TeamDtos.TeamDailyResponse;
import com.biometricattendance.team.TeamDtos.TeamDay;
import com.biometricattendance.team.TeamDtos.TeamEmployeeResponse;
import com.biometricattendance.team.TeamDtos.TeamRow;

/** Absensi rekan kerja untuk semua role (K-083), dihitung dengan mesin rekap yang sama dengan pengelola. */
@Service
public class TeamService {

	private final AttendanceService attendance;
	private final EmployeeRepository employees;
	private final BranchRepository branches;

	public TeamService(AttendanceService attendance, EmployeeRepository employees, BranchRepository branches) {
		this.attendance = attendance;
		this.employees = employees;
		this.branches = branches;
	}

	public LocalDate today() {
		return attendance.today();
	}

	/**
	 * Karyawan aktif saja ({@code AttendanceService.daily} sudah menyaring {@code active = true}). Jabatan diambil
	 * dari entity yang sudah dimuat {@code daily} di transaksi yang sama, jadi tanpa query tambahan per baris.
	 */
	@Transactional(readOnly = true)
	public TeamDailyResponse daily(LocalDate date, Long branchId) {
		List<TeamRow> rows = attendance.daily(date, branchId)
			.rows()
			.stream()
			.map(r -> new TeamRow(r.employeeId(), r.employeeNumber(), r.fullName(), r.branchId(), r.branchName(),
					employees.getReferenceById(r.employeeId()).getPosition(), TeamDay.from(r.day())))
			.toList();
		return new TeamDailyResponse(date, branchId, branchOptions(), rows);
	}

	@Transactional(readOnly = true)
	public TeamEmployeeResponse forEmployee(Long employeeId, LocalDate from, LocalDate to) {
		Employee e = employees.findById(employeeId)
			.filter(Employee::isActive)
			.orElseThrow(() -> ApiException.notFound("Karyawan"));
		List<TeamDay> days = attendance.forEmployee(employeeId, from, to).days().stream().map(TeamDay::from).toList();
		return new TeamEmployeeResponse(e.getId(), e.getEmployeeNumber(), e.getFullName(), e.getBranch().getName(),
				e.getPosition(), from, to, days);
	}

	private List<BranchOption> branchOptions() {
		return branches.findAllByOrderByNameAsc()
			.stream()
			.filter(Branch::isActive)
			.map(b -> new BranchOption(b.getId(), b.getName()))
			.toList();
	}

}
```

> `Employee.isActive()` (baris 136) dan `Branch.isActive()` (baris 81) sudah ada. Sebelum menulis, periksa juga bahwa `Branch.getId()`/`getName()` serta `Employee.getEmployeeNumber()`/`getFullName()` ada.

`backend/src/main/java/com/biometricattendance/team/TeamController.java`:

```java
package com.biometricattendance.team;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.team.TeamDtos.TeamDailyResponse;
import com.biometricattendance.team.TeamDtos.TeamEmployeeResponse;

/** Absensi rekan kerja untuk semua pengguna yang login (K-083). */
@RestController
@RequestMapping("/api/team")
public class TeamController {

	private final TeamService team;

	public TeamController(TeamService team) {
		this.team = team;
	}

	@GetMapping("/attendance")
	TeamDailyResponse daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) Long branchId) {
		return team.daily(date == null ? team.today() : date, branchId);
	}

	/** Tanpa rentang: awal bulan berjalan sampai hari ini. */
	@GetMapping("/employees/{id}/attendance")
	TeamEmployeeResponse employee(@PathVariable Long id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		LocalDate end = to == null ? team.today() : to;
		return team.forEmployee(id, from == null ? end.withDayOfMonth(1) : from, end);
	}

}
```

`SecurityConfig.java`: sisipkan tepat setelah baris `.requestMatchers("/api/auth/**", "/api/me/**").authenticated()`:

```java
				// Daftar rekan kerja: semua role yang login; kolomnya dibatasi TeamDtos (K-083).
				.requestMatchers(HttpMethod.GET, "/api/team/**").authenticated()
```

- [ ] **Step 4: Run tests to verify they pass**

Run (dari `backend/`): `./mvnw -q test -Dtest=TeamTests,AccessControlTests`
Expected: PASS. Kalau `statusesFollowRecapWithoutRevealingLeaveDetails` gagal pada salah satu hari, baca `DailyAttendanceCalculator` untuk memastikan penyebabnya sebelum mengubah apa pun. Jangan mengubah ekspektasi tanpa alasan dari kode.

- [ ] **Step 5: Run the full backend suite**

Run (dari `backend/`): `./mvnw -q verify`
Expected: BUILD SUCCESS. Sebelumnya 226 test; sekarang bertambah 7 (`TeamTests`) + 3 (matriks).

---

### Task 2: Frontend tipe dan helper `team`

**Files:**
- Modify: `frontend/src/lib/types.ts` (tambahkan di akhir file)
- Create: `frontend/src/lib/team.ts`
- Test: `frontend/src/lib/team.test.ts`

**Interfaces:**
- Consumes: kontrak JSON Task 1; `Tone` dari `@/lib/attendance` (sudah diekspor, baris 25).
- Produces: tipe `TeamStatus`, `TeamDay`, `TeamRow`, `TeamDaily`, `TeamMember` (types.ts); `TEAM_STATUS: Record<TeamStatus, {label: string; tone: Tone}>`, `monthOf(iso: string): string`, `shiftMonth(month: string, delta: number): string`, `monthRange(month: string, today: string): {from: string; to: string}`, `monthLabel(month: string): string` (team.ts).

- [ ] **Step 1: Write the failing test**

`frontend/src/lib/team.test.ts`:

```ts
import { describe, expect, it } from 'vitest'

import { TEAM_STATUS, monthLabel, monthOf, monthRange, shiftMonth } from '@/lib/team'

describe('helper rekan kerja', () => {
  it('rentang bulan lalu penuh, bulan berjalan dipotong di hari ini', () => {
    expect(monthRange('2026-09', '2026-10-14')).toEqual({ from: '2026-09-01', to: '2026-09-30' })
    expect(monthRange('2026-10', '2026-10-14')).toEqual({ from: '2026-10-01', to: '2026-10-14' })
    expect(monthRange('2024-02', '2026-10-14')).toEqual({ from: '2024-02-01', to: '2024-02-29' })
  })

  it('geser bulan melewati batas tahun', () => {
    expect(shiftMonth('2026-01', -1)).toBe('2025-12')
    expect(shiftMonth('2026-12', 1)).toBe('2027-01')
    expect(monthOf('2026-10-14')).toBe('2026-10')
  })

  it('label bulan dalam Bahasa Indonesia', () => {
    expect(monthLabel('2026-10')).toBe('Oktober 2026')
  })

  it('izin hanya berlabel "Izin" tanpa jenis', () => {
    expect(TEAM_STATUS.IZIN.label).toBe('Izin')
    expect(TEAM_STATUS.HADIR.tone).toBe('success')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run (dari `frontend/`): `npx vitest run src/lib/team.test.ts`
Expected: FAIL, import `@/lib/team` tidak ditemukan.

- [ ] **Step 3: Write the implementation**

Tambahkan di akhir `frontend/src/lib/types.ts`:

```ts
/** Daftar rekan kerja (docs/rencana-rekan-kerja.md, K-083): hanya kolom yang diizinkan. */
export type TeamStatus = 'HADIR' | 'TIDAK_LENGKAP' | 'TIDAK_HADIR' | 'IZIN' | 'LIBUR' | 'BELUM_DIATUR'

export interface TeamDay {
  date: string
  status: TeamStatus
  checkIn: string | null
  checkOut: string | null
  lateSeconds: number | null
  overtimeSeconds: number | null
}

export interface TeamRow {
  employeeId: number
  employeeNumber: string
  fullName: string
  branchId: number
  branchName: string
  position: string | null
  day: TeamDay
}

export interface TeamDaily {
  date: string
  branchId: number | null
  branches: { id: number; name: string }[]
  rows: TeamRow[]
}

export interface TeamMember {
  employeeId: number
  employeeNumber: string
  fullName: string
  branchName: string
  position: string | null
  from: string
  to: string
  days: TeamDay[]
}
```

`frontend/src/lib/team.ts`:

```ts
import type { Tone } from '@/lib/attendance'
import type { TeamStatus } from '@/lib/types'

/** Label status rekan kerja (K-084). Izin sengaja tanpa jenis. */
export const TEAM_STATUS: Record<TeamStatus, { label: string; tone: Tone }> = {
  HADIR: { label: 'Hadir', tone: 'success' },
  TIDAK_LENGKAP: { label: 'Belum lengkap', tone: 'warning' },
  TIDAK_HADIR: { label: 'Tidak hadir', tone: 'danger' },
  IZIN: { label: 'Izin', tone: 'info' },
  LIBUR: { label: 'Libur', tone: 'neutral' },
  BELUM_DIATUR: { label: 'Belum diatur', tone: 'neutral' },
}

const MONTHS = ['Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni', 'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember']

const pad = (n: number) => String(n).padStart(2, '0')

/** "2026-10-14" → "2026-10". */
export function monthOf(iso: string): string {
  return iso.slice(0, 7)
}

export function shiftMonth(month: string, delta: number): string {
  const [year, m] = month.split('-').map(Number)
  const index = year * 12 + (m - 1) + delta
  return `${Math.floor(index / 12)}-${pad((index % 12) + 1)}`
}

/** Rentang satu bulan; bulan berjalan dipotong di hari ini agar hari mendatang tidak tampil "Tidak hadir". */
export function monthRange(month: string, today: string): { from: string; to: string } {
  const [year, m] = month.split('-').map(Number)
  const lastDay = new Date(Date.UTC(year, m, 0)).getUTCDate()
  const end = `${month}-${pad(lastDay)}`
  return { from: `${month}-01`, to: end > today ? today : end }
}

export function monthLabel(month: string): string {
  const [year, m] = month.split('-').map(Number)
  return `${MONTHS[m - 1]} ${year}`
}
```

- [ ] **Step 4: Run test to verify it passes**

Run (dari `frontend/`): `npx vitest run src/lib/team.test.ts`
Expected: PASS (4 test).

---

### Task 3: Halaman Rekan Kerja, route, dan menu

**Files:**
- Create: `frontend/src/components/TeamStatusBadges.tsx`
- Create: `frontend/src/pages/TeamPage.tsx`
- Create: `frontend/src/pages/TeamMemberPage.tsx`
- Modify: `frontend/src/App.tsx` (import gaya `@/pages/...` seperti baris 8–10; 2 route di dalam blok `AppLayout`, setelah route `izin-saya/:id`)
- Modify: `frontend/src/navigation.ts` (item menu + ikon `ContactRound` dari `lucide-react`)
- Modify: `frontend/src/navigation.test.ts`
- Test: `frontend/src/pages/TeamPages.test.tsx`

**Interfaces:**
- Consumes: `api<T>(path)` (`@/lib/api`); tipe dan helper Task 2; `formatTime` (null → "—", `"08:10:00"` → `"08.10"`), `formatDuration` (null → "—", 600 → "10 menit"), `formatDate`, `addDays(iso, n)`, `localDateIso()` (`@/lib/format`); `PageHeader` (prop `title`, `description`, `actions`); `EmptyState`/`ErrorAlert`/`SkeletonLines` (`@/components/feedback`); `fieldClass`/`selectClass` (`@/components/FormDialog`); `Badge` (varian `success|warning|danger|info|neutral`); `Button` (`variant="outline"|"ghost"`, `size="icon"`); `Input`; `Label`; `Table*`.
- Produces: route `/rekan-kerja` (query `?tanggal=YYYY-MM-DD`) dan `/rekan-kerja/:id` (query `?bulan=YYYY-MM`); komponen `TeamStatusBadges({ day: TeamDay })`.

- [ ] **Step 1: Write the failing tests**

`frontend/src/pages/TeamPages.test.tsx`:

```tsx
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { Me, TeamDaily, TeamMember } from '@/lib/types'
import { mockFetch } from '@/test/fetchMock'

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const employee: Me = { username: 'budi', role: 'EMPLOYEE', employeeId: 12, employeeName: 'Budi Santoso', mustChangePassword: false }

const daily: TeamDaily = {
  date: '2026-10-05',
  branchId: null,
  branches: [
    { id: 1, name: 'Pusat' },
    { id: 2, name: 'Cabang Timur' },
  ],
  rows: [
    {
      employeeId: 12, employeeNumber: 'K-012', fullName: 'Budi Santoso', branchId: 1, branchName: 'Pusat', position: 'Staf gudang',
      day: { date: '2026-10-05', status: 'HADIR', checkIn: '08:10:00', checkOut: '17:30:00', lateSeconds: 600, overtimeSeconds: 1800 },
    },
    {
      employeeId: 13, employeeNumber: 'K-013', fullName: 'Siti Aminah', branchId: 2, branchName: 'Cabang Timur', position: null,
      day: { date: '2026-10-05', status: 'IZIN', checkIn: null, checkOut: null, lateSeconds: null, overtimeSeconds: null },
    },
  ],
}

const dailyRoutes = {
  'GET /api/auth/me': () => ({ status: 200, body: employee }),
  'GET /api/team/attendance?date=2026-10-05': () => ({ status: 200, body: daily }),
}

describe('Halaman Rekan Kerja', () => {
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('karyawan melihat absensi rekan kerja; izin tampil tanpa jenis', async () => {
    mockFetch(dailyRoutes)
    renderAt('/rekan-kerja?tanggal=2026-10-05')

    const table = await screen.findByRole('table')
    const siti = within(table).getByText('Siti Aminah').closest('tr')!
    expect(within(siti).getByText('Izin')).toBeInTheDocument()
    // Jabatan kosong dan jam kosong tampil sebagai tanda strip, tidak crash.
    expect(within(siti).getAllByText('—').length).toBeGreaterThan(1)
    const budi = within(table).getByText('Budi Santoso').closest('tr')!
    expect(within(budi).getByText('Hadir')).toBeInTheDocument()
    expect(within(budi).getByText('Terlambat')).toBeInTheDocument()
    expect(within(budi).getByText('08.10')).toBeInTheDocument()
    expect(within(table).getByText('Budi Santoso').closest('a')).toHaveAttribute('href', '/rekan-kerja/12')
  })

  it('pencarian tidak peka huruf besar-kecil dan mencocokkan No. karyawan', async () => {
    mockFetch(dailyRoutes)
    renderAt('/rekan-kerja?tanggal=2026-10-05')
    const table = await screen.findByRole('table')

    fireEvent.change(screen.getByLabelText('Cari'), { target: { value: 'SITI' } })
    expect(within(table).queryByText('Budi Santoso')).not.toBeInTheDocument()
    expect(within(table).getByText('Siti Aminah')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Cari'), { target: { value: 'k-012' } })
    expect(within(table).getByText('Budi Santoso')).toBeInTheDocument()
    expect(within(table).queryByText('Siti Aminah')).not.toBeInTheDocument()
  })

  it('filter cabang memanggil API dengan branchId', async () => {
    const fetch = mockFetch({
      ...dailyRoutes,
      'GET /api/team/attendance?date=2026-10-05&branchId=2': () => ({ status: 200, body: { ...daily, branchId: 2, rows: [daily.rows[1]] } }),
    })
    renderAt('/rekan-kerja?tanggal=2026-10-05')
    await screen.findByRole('table')

    fireEvent.change(screen.getByLabelText('Cabang'), { target: { value: '2' } })
    await waitFor(() =>
      expect(fetch.mock.calls.some(([url]) => url === '/api/team/attendance?date=2026-10-05&branchId=2')).toBe(true),
    )
  })

  it('riwayat satu bulan beserta ringkasan', async () => {
    // Hanya Date yang dipalsukan, agar timer React Query tetap berjalan normal.
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-14T03:00:00Z'))
    const member: TeamMember = {
      employeeId: 13, employeeNumber: 'K-013', fullName: 'Siti Aminah', branchName: 'Cabang Timur', position: null,
      from: '2026-09-01', to: '2026-09-30',
      days: [
        { date: '2026-09-01', status: 'HADIR', checkIn: '08:20:00', checkOut: '17:00:00', lateSeconds: 1200, overtimeSeconds: 0 },
        { date: '2026-09-02', status: 'HADIR', checkIn: '07:55:00', checkOut: '17:00:00', lateSeconds: 0, overtimeSeconds: 0 },
        { date: '2026-09-03', status: 'IZIN', checkIn: null, checkOut: null, lateSeconds: null, overtimeSeconds: null },
        { date: '2026-09-04', status: 'TIDAK_HADIR', checkIn: null, checkOut: null, lateSeconds: null, overtimeSeconds: null },
      ],
    }
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: employee }),
      'GET /api/team/employees/13/attendance?from=2026-09-01&to=2026-09-30': () => ({ status: 200, body: member }),
    })
    renderAt('/rekan-kerja/13?bulan=2026-09')

    expect(await screen.findByRole('heading', { name: 'Siti Aminah' })).toBeInTheDocument()
    expect(screen.getByText('September 2026')).toBeInTheDocument()
    const summary = screen.getByRole('list', { name: 'Ringkasan bulan ini' })
    expect(within(summary).getByText('Hadir').nextSibling).toHaveTextContent('2')
    expect(within(summary).getByText('Terlambat').nextSibling).toHaveTextContent('1')
    expect(within(summary).getByText('Izin').nextSibling).toHaveTextContent('1')
    expect(within(summary).getByText('Tidak hadir').nextSibling).toHaveTextContent('1')
  })
})
```

Ubah `frontend/src/navigation.test.ts`. Ganti test karyawan menjadi:

```ts
  it('Karyawan melihat rekan kerja, profil, dan pengajuan izinnya sendiri', () => {
    expect(labels('EMPLOYEE')).toEqual(['Rekan Kerja', 'Profil Saya', 'Izin & Cuti Saya'])
  })
```

dan tambahkan di test Owner: `expect(labels('OWNER')).not.toContain('Rekan Kerja')`.

- [ ] **Step 2: Run tests to verify they fail**

Run (dari `frontend/`): `npx vitest run src/pages/TeamPages.test.tsx src/navigation.test.ts`
Expected: FAIL. Route belum ada (tabel tidak ditemukan) dan menu belum ada.

- [ ] **Step 3: Write the implementation**

`frontend/src/navigation.ts`: tambahkan `ContactRound` ke import `lucide-react`, lalu sisipkan item ini **sebelum** item `/profil`:

```ts
  { path: '/rekan-kerja', label: 'Rekan Kerja', roles: ['EMPLOYEE'], icon: ContactRound, group: 'Saya', mobile: true },
```

`frontend/src/components/TeamStatusBadges.tsx`:

```tsx
import { Badge } from '@/components/ui/badge'
import { TEAM_STATUS } from '@/lib/team'
import type { TeamDay } from '@/lib/types'

/** Status rekan kerja + penanda terlambat. Selalu berupa teks, tidak hanya warna (design-system §1). */
export function TeamStatusBadges({ day }: { day: TeamDay }) {
  const status = TEAM_STATUS[day.status]
  return (
    <span className="inline-flex flex-wrap gap-1.5">
      <Badge variant={status.tone}>{status.label}</Badge>
      {(day.lateSeconds ?? 0) > 0 && <Badge variant="warning">Terlambat</Badge>}
    </span>
  )
}
```

`frontend/src/pages/TeamPage.tsx`:

```tsx
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, RefreshCw } from 'lucide-react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { fieldClass, selectClass } from '@/components/FormDialog'
import { PageHeader } from '@/components/PageHeader'
import { TeamStatusBadges } from '@/components/TeamStatusBadges'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api } from '@/lib/api'
import { addDays, formatDuration, formatTime, localDateIso } from '@/lib/format'
import type { TeamDaily, TeamRow } from '@/lib/types'

const HEADERS = ['No.', 'Nama', 'Cabang', 'Jabatan', 'Status', 'Masuk', 'Pulang', 'Terlambat', 'Lembur']

/** Absensi semua karyawan aktif per tanggal untuk semua role (K-083), tanpa data sensitif. */
export function TeamPage() {
  const [params, setParams] = useSearchParams()
  const date = params.get('tanggal') ?? localDateIso()
  const [branchId, setBranchId] = useState('')
  const [search, setSearch] = useState('')
  const query = new URLSearchParams({ date, ...(branchId ? { branchId } : {}) })
  const { data, error, isPending, isFetching, refetch } = useQuery({
    queryKey: ['/api/team/attendance', date, branchId],
    queryFn: () => api<TeamDaily>(`/api/team/attendance?${query}`),
    refetchInterval: 60_000,
  })

  const setDate = (value: string) => setParams(value ? { tanggal: value } : {}, { replace: true })
  const needle = search.trim().toLowerCase()
  const rows = (data?.rows ?? []).filter(
    (r) => !needle || r.fullName.toLowerCase().includes(needle) || r.employeeNumber.toLowerCase().includes(needle),
  )
  const link = (row: TeamRow) => (
    <Link to={`/rekan-kerja/${row.employeeId}`} className="text-steel-700 underline-offset-4 hover:underline dark:text-foreground">
      {row.fullName}
    </Link>
  )

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Rekan Kerja"
        description="Absensi semua karyawan aktif. Diperbarui otomatis setiap menit."
        actions={
          <Button variant="outline" onClick={() => refetch()} disabled={isFetching}>
            <RefreshCw className={isFetching ? 'animate-spin' : undefined} />
            Muat ulang
          </Button>
        }
      />

      <div className="flex flex-wrap items-end gap-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="tanggal" className="font-semibold">
            Tanggal
          </Label>
          <div className="flex items-center gap-1">
            <Button variant="ghost" size="icon" aria-label="Hari sebelumnya" onClick={() => setDate(addDays(date, -1))}>
              <ChevronLeft />
            </Button>
            <Input id="tanggal" type="date" className={fieldClass} value={date} onChange={(e) => setDate(e.target.value)} />
            <Button variant="ghost" size="icon" aria-label="Hari berikutnya" onClick={() => setDate(addDays(date, 1))}>
              <ChevronRight />
            </Button>
          </div>
        </div>
        <div className="flex min-w-48 flex-col gap-1.5">
          <Label htmlFor="cabang" className="font-semibold">
            Cabang
          </Label>
          <select id="cabang" className={selectClass} value={branchId} onChange={(e) => setBranchId(e.target.value)}>
            <option value="">Semua cabang</option>
            {data?.branches.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </select>
        </div>
        <div className="flex min-w-56 flex-1 flex-col gap-1.5">
          <Label htmlFor="cari" className="font-semibold">
            Cari
          </Label>
          <Input id="cari" className={fieldClass} placeholder="Nama atau No. karyawan" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
      </div>

      {error && <ErrorAlert message={error.message} />}

      <section className="overflow-hidden rounded-lg border bg-card">
        {data && <p className="border-b px-4 py-2.5 text-xs text-muted-foreground tabular-nums">{rows.length} karyawan</p>}
        {isPending && <SkeletonLines />}
        {data && rows.length === 0 && <EmptyState message={needle ? 'Tidak ada yang cocok dengan pencarian.' : 'Belum ada karyawan aktif.'} />}
        {rows.length > 0 && (
          <>
            <div className="hidden md:block">
              <Table>
                <TableHeader>
                  <TableRow className="bg-steel-50 hover:bg-steel-50 dark:bg-secondary dark:hover:bg-secondary">
                    {HEADERS.map((header) => (
                      <TableHead key={header} scope="col" className="label-caps h-11 px-4 text-muted-foreground">
                        {header}
                      </TableHead>
                    ))}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow key={row.employeeId} className="hover:bg-accent/60">
                      <TableCell className="px-4 tabular-nums">{row.employeeNumber}</TableCell>
                      <TableCell className="px-4 font-semibold">{link(row)}</TableCell>
                      <TableCell className="px-4">{row.branchName}</TableCell>
                      <TableCell className="px-4">{row.position ?? '—'}</TableCell>
                      <TableCell className="px-4">
                        <TeamStatusBadges day={row.day} />
                      </TableCell>
                      <TableCell className="px-4 font-semibold tabular-nums">{formatTime(row.day.checkIn)}</TableCell>
                      <TableCell className="px-4 font-semibold tabular-nums">{formatTime(row.day.checkOut)}</TableCell>
                      <TableCell className="px-4 tabular-nums">{formatDuration(row.day.lateSeconds)}</TableCell>
                      <TableCell className="px-4 tabular-nums">{formatDuration(row.day.overtimeSeconds)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <ul className="divide-y md:hidden">
              {rows.map((row) => (
                <li key={row.employeeId} className="flex flex-col gap-2 p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="font-semibold break-words">{link(row)}</p>
                      <p className="text-sm text-muted-foreground">
                        {[row.employeeNumber, row.branchName, row.position].filter(Boolean).join(' · ')}
                      </p>
                    </div>
                    <TeamStatusBadges day={row.day} />
                  </div>
                  <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-base">
                    <dt className="text-muted-foreground">Masuk</dt>
                    <dd className="font-semibold tabular-nums">{formatTime(row.day.checkIn)}</dd>
                    <dt className="text-muted-foreground">Pulang</dt>
                    <dd className="font-semibold tabular-nums">{formatTime(row.day.checkOut)}</dd>
                    <dt className="text-muted-foreground">Terlambat</dt>
                    <dd className="tabular-nums">{formatDuration(row.day.lateSeconds)}</dd>
                    <dt className="text-muted-foreground">Lembur</dt>
                    <dd className="tabular-nums">{formatDuration(row.day.overtimeSeconds)}</dd>
                  </dl>
                </li>
              ))}
            </ul>
          </>
        )}
      </section>
    </div>
  )
}
```

> Halaman ini memakai query `?tanggal=`, jadi test bisa menentukan tanggal tanpa memalsukan jam. jsdom merender tabel desktop maupun kartu HP (class `hidden` tidak berpengaruh). Itu sebabnya test mencari elemen di dalam `table`.

`frontend/src/pages/TeamMemberPage.tsx`:

```tsx
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, ChevronLeft, ChevronRight } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { PageHeader } from '@/components/PageHeader'
import { TeamStatusBadges } from '@/components/TeamStatusBadges'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api } from '@/lib/api'
import { formatDate, formatDuration, formatTime, localDateIso } from '@/lib/format'
import { monthLabel, monthOf, monthRange, shiftMonth } from '@/lib/team'
import type { TeamDay, TeamMember } from '@/lib/types'

const HEADERS = ['Tanggal', 'Status', 'Masuk', 'Pulang', 'Terlambat', 'Lembur']

/** Riwayat absensi satu rekan kerja per bulan (K-083). */
export function TeamMemberPage() {
  const { id } = useParams()
  const [params, setParams] = useSearchParams()
  const today = localDateIso()
  const month = params.get('bulan') ?? monthOf(today)
  const { from, to } = monthRange(month, today)
  const isFuture = from > today
  const { data, error, isPending } = useQuery({
    queryKey: ['/api/team/employees', id, from, to],
    queryFn: () => api<TeamMember>(`/api/team/employees/${id}/attendance?from=${from}&to=${to}`),
    enabled: !isFuture,
  })
  const setMonth = (value: string) => setParams({ bulan: value }, { replace: true })

  const days = data?.days ?? []
  const count = (predicate: (d: TeamDay) => boolean) => days.filter(predicate).length
  const summary = [
    { label: 'Hadir', value: count((d) => d.status === 'HADIR') },
    { label: 'Terlambat', value: count((d) => (d.lateSeconds ?? 0) > 0) },
    { label: 'Tidak hadir', value: count((d) => d.status === 'TIDAK_HADIR') },
    { label: 'Izin', value: count((d) => d.status === 'IZIN') },
    { label: 'Belum lengkap', value: count((d) => d.status === 'TIDAK_LENGKAP') },
  ]
  const newestFirst = [...days].reverse()

  return (
    <div className="flex flex-col gap-5">
      <Link to="/rekan-kerja" className="inline-flex w-fit items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" />
        Rekan Kerja
      </Link>
      <PageHeader
        title={data?.fullName ?? 'Riwayat absensi'}
        description={data ? [data.employeeNumber, data.branchName, data.position].filter(Boolean).join(' · ') : undefined}
      />

      <div className="flex items-center gap-1">
        <Button variant="ghost" size="icon" aria-label="Bulan sebelumnya" onClick={() => setMonth(shiftMonth(month, -1))}>
          <ChevronLeft />
        </Button>
        <p className="min-w-40 text-center font-semibold">{monthLabel(month)}</p>
        <Button variant="ghost" size="icon" aria-label="Bulan berikutnya" onClick={() => setMonth(shiftMonth(month, 1))}>
          <ChevronRight />
        </Button>
      </div>

      {error && <ErrorAlert message={error.message} />}
      {isFuture && <EmptyState message="Bulan ini belum berjalan." />}

      {data && (
        <ul aria-label="Ringkasan bulan ini" className="grid grid-cols-2 gap-3 sm:grid-cols-5">
          {summary.map((item) => (
            <li key={item.label} className="rounded-lg border bg-card p-4">
              <p className="label-caps text-muted-foreground">{item.label}</p>
              <p className="font-heading text-2xl font-bold tabular-nums">{item.value}</p>
            </li>
          ))}
        </ul>
      )}

      {!isFuture && (
        <section className="overflow-hidden rounded-lg border bg-card">
          {isPending && <SkeletonLines />}
          {data && days.length === 0 && <EmptyState message="Belum ada data di bulan ini." />}
          {days.length > 0 && (
            <>
              <div className="hidden md:block">
                <Table>
                  <TableHeader>
                    <TableRow className="bg-steel-50 hover:bg-steel-50 dark:bg-secondary dark:hover:bg-secondary">
                      {HEADERS.map((header) => (
                        <TableHead key={header} scope="col" className="label-caps h-11 px-4 text-muted-foreground">
                          {header}
                        </TableHead>
                      ))}
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {newestFirst.map((day) => (
                      <TableRow key={day.date} className="hover:bg-accent/60">
                        <TableCell className="px-4">{formatDate(day.date)}</TableCell>
                        <TableCell className="px-4">
                          <TeamStatusBadges day={day} />
                        </TableCell>
                        <TableCell className="px-4 font-semibold tabular-nums">{formatTime(day.checkIn)}</TableCell>
                        <TableCell className="px-4 font-semibold tabular-nums">{formatTime(day.checkOut)}</TableCell>
                        <TableCell className="px-4 tabular-nums">{formatDuration(day.lateSeconds)}</TableCell>
                        <TableCell className="px-4 tabular-nums">{formatDuration(day.overtimeSeconds)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <ul className="divide-y md:hidden">
                {newestFirst.map((day) => (
                  <li key={day.date} className="flex flex-col gap-2 p-4">
                    <div className="flex items-start justify-between gap-3">
                      <p className="font-semibold">{formatDate(day.date)}</p>
                      <TeamStatusBadges day={day} />
                    </div>
                    <p className="text-base tabular-nums">
                      {formatTime(day.checkIn)} – {formatTime(day.checkOut)}
                      {(day.lateSeconds ?? 0) > 0 && <span className="text-warning"> · terlambat {formatDuration(day.lateSeconds)}</span>}
                    </p>
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>
      )}
    </div>
  )
}
```

> Ringkasan memakai `<p>` label lalu `<p>` nilai, jadi di test `getByText('Hadir').nextSibling` menunjuk ke nilai. Badge "Hadir"/"Izin" di tabel berada di luar list ringkasan, sehingga tidak bentrok dengan `within(summary)`.

`frontend/src/App.tsx`: tambahkan import (urut abjad di antara import halaman lain):

```tsx
import { TeamMemberPage } from '@/pages/TeamMemberPage'
import { TeamPage } from '@/pages/TeamPage'
```

dan di dalam blok `AppLayout`, setelah `<Route path="izin-saya/:id" .../>`:

```tsx
        {/* Rekan kerja: semua role yang login; kolom dibatasi backend (K-083). */}
        <Route path="rekan-kerja" element={<TeamPage />} />
        <Route path="rekan-kerja/:id" element={<TeamMemberPage />} />
```

- [ ] **Step 4: Run tests to verify they pass**

Run (dari `frontend/`): `npx vitest run src/pages/TeamPages.test.tsx src/navigation.test.ts src/lib/team.test.ts`
Expected: PASS.

- [ ] **Step 5: Run the full frontend checks**

Run (dari `frontend/`): `npx vitest run; npx tsc -b; npm run lint; npm run build`
Expected: semua test lulus (sebelumnya 43, bertambah 4 + 4 = 51), tsc tanpa error, lint hanya 3 peringatan lama `only-export-components`, dan build OK.

---

### Task 4: Dokumen

**Files:**
- Modify: `docs/keputusan.md`, `docs/pertanyaan-terbuka.md` (Q-016), `docs/aturan-bisnis.md` (bagian hak akses, sekitar baris 460), `docs/arsitektur.md` (daftar paket/modul backend), `docs/README.md` (indeks), `docs/rencana-rekan-kerja.md` (status), `docs/rencana-pengerjaan.md` (baris status)

- [ ] **Step 1: Baca format tiap dokumen**

Baca tabel ringkasan keputusan dan subbagian K-070–K-082 di `keputusan.md` (cari "K-082"). Tiru format baris tabel dan subbagiannya persis. Lakukan hal yang sama untuk bagian paket di `arsitektur.md` (cari "§6.8") dan indeks di `README.md` (cari "rencana-fase-4").

- [ ] **Step 2: Tulis perubahan**

- `keputusan.md`, baris tabel + subbagian:
  - **K-083** — Semua karyawan yang login boleh melihat absensi semua karyawan aktif (No., nama, cabang, jabatan, status, jam masuk/pulang, terlambat, lembur). Data sensitif (No. HP, PIN mesin, kategori, tanggal masuk/keluar, jenis/alasan/surat izin, koreksi, data gaji) tidak dikirim, karena dibatasi DTO paket `team` di backend. Dipilih pengguna 2026-10-14 (hasil rapat tim). Rincian di `rencana-rekan-kerja.md`.
  - **K-084** — Status rekan kerja disederhanakan: Hadir/Belum lengkap/Tidak hadir/Izin/Libur/Belum diatur. Izin tampil tanpa jenis; izin menunggu dan "hadir saat izin" tidak ditampilkan sebagai izin; jam hasil koreksi tampil tanpa keterangan koreksi. Disetujui pengguna 2026-10-14.
- `pertanyaan-terbuka.md` Q-016: tambahkan "Riwayat absensi dijawab 2026-10-14: karyawan melihat absensinya sendiri dan rekan kerja lewat halaman Rekan Kerja (K-083). Slip gaji dan saldo piutang masih terbuka."
- `aturan-bisnis.md`: di bagian hak akses, tambahkan satu poin bahwa karyawan boleh melihat absensi rekan kerja dengan kolom terbatas (K-083), beserta tautan ke `rencana-rekan-kerja.md`.
- `arsitektur.md`: tambahkan paket `team` (GET `/api/team/**`, semua role yang login, DTO berisi kolom yang diizinkan, tanpa SSE) di tempat paket backend lain dijelaskan.
- `README.md`: tambahkan `rencana-rekan-kerja.md` ke indeks, di sebelah `rencana-fase-4.md`.
- `rencana-rekan-kerja.md`: ubah status menjadi "diimplementasikan 2026-10-14, menunggu checkpoint".
- `rencana-pengerjaan.md`: di baris status, ubah "Fase 4 diimplementasikan … menunggu checkpoint" menjadi "Fase 4 disetujui 2026-10-14", lalu tambahkan "Daftar rekan kerja untuk karyawan diimplementasikan 2026-10-14 ([rencana-rekan-kerja.md](rencana-rekan-kerja.md))."

- [ ] **Step 3: Cek tautan**

Run (dari root repo): `grep -ln "rencana-rekan-kerja" docs/*.md`
Expected: README.md, aturan-bisnis.md, keputusan.md, rencana-pengerjaan.md.

---

### Task 5: Verifikasi end-to-end di Docker dan laporan

- [ ] **Step 1: Build ulang dan jalankan**

Run (dari root repo): `docker compose up --build -d`, lalu tunggu `/actuator/health` UP.

- [ ] **Step 2: Uji lewat API sebagai karyawan**

Login sebagai akun karyawan uji dev `uji-x401` (password ada di catatan sesi; jangan tulis ke repo). Lalu `GET /api/team/attendance?date=2026-10-14`. Pada baris karyawan dengan izin Sakit disetujui di 14 Okt (data uji Fase 4), status harus `IZIN`, dan respons tidak boleh mengandung `phone`, `devicePin`, `leaveTypeName`, `Sakit`, maupun `source`.

- [ ] **Step 3: Uji di browser (Playwright MCP)**

Login sebagai karyawan uji. Menu "Rekan Kerja" harus tampil. Buka tabel, ganti tanggal ke 14 Okt (status "Izin" tanpa "Sakit"), cari nama, klik nama untuk membuka riwayat Oktober, lalu cek ringkasan. Ulangi di lebar 375px (tampilan kartu). Simpan screenshot `.playwright-mcp/rekan-kerja-*.png` dan **lihat** hasilnya.

- [ ] **Step 4: Laporkan ke pengguna**

Laporkan jumlah test backend dan frontend persis dari output, hasil uji E2E, bug yang ditemukan, dan batasan (tidak live via SSE, menu hanya untuk Karyawan). Tanyakan apakah boleh di-commit. Setelah itu lanjut ke sub-pekerjaan B (tema glassmorphism).
