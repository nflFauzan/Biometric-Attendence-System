package com.biometricattendance.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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

	/**
	 * Koreksi disimpan per menit (detik 0), scan asli berdetik acak. Agar koreksi tidak bisa ditebak dari detiknya,
	 * semua jam dan durasi dikirim dalam menit penuh.
	 */
	@Test
	void timesAreWholeMinutesSoCorrectionsCannotBeInferred() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		long employee = createEmployee(branch, pin);
		addFullRule(employee);
		String sn = uniqueSerial();
		createDevice(branch, sn, ACCEPT_FROM);
		postAttlog(sn, lines(line(pin, SCAN_DAY + " 08:10:37"), line(pin, SCAN_DAY + " 17:30:12")))
			.andExpect(status().isOk());

		daily(HR, SCAN_DAY, branch).andExpect(jsonPath("$.rows[0].day.checkIn").value(anyOf(is("08:10"), is("08:10:00"))))
			.andExpect(jsonPath("$.rows[0].day.checkOut").value(anyOf(is("17:30"), is("17:30:00"))))
			.andExpect(jsonPath("$.rows[0].day.lateSeconds").value(600))
			.andExpect(jsonPath("$.rows[0].day.overtimeSeconds").value(1800));
	}

	/** Tanggal yang belum terjadi akan tampil "Tidak hadir" palsu dan membocorkan izin mendatang. */
	@Test
	void futureDatesAreRejected() throws Exception {
		long employee = createEmployee(createBranch(), null);
		mockMvc.perform(get("/api/team/attendance").param("date", "2099-01-01").with(HR))
			.andExpect(status().isBadRequest());
		history(HR, employee, "2098-12-01", "2099-01-01").andExpect(status().isBadRequest());
	}

	@Test
	void historyRangeIsLimited() throws Exception {
		long employee = createEmployee(createBranch(), null);
		history(HR, employee, "2026-01-01", "2026-06-30").andExpect(status().isBadRequest());
		history(HR, employee, "2026-10-10", "2026-10-01").andExpect(status().isBadRequest());
	}

}
