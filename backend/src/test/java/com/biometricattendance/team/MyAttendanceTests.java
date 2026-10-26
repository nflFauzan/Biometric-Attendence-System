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

/**
 * Absensi Saya: karyawan hanya melihat riwayat absensinya sendiri (K-127, menggantikan daftar rekan kerja K-083).
 * Bentuk data tetap terbatas seperti K-084. Data sintetis.
 */
class MyAttendanceTests extends AttendanceFixtures {

	private static final String SCAN_DAY = "2026-10-05";
	private static final String LEAVE_DAY = "2026-10-06";
	private static final String CORRECTED_DAY = "2026-10-07";
	private static final String PENDING_DAY = "2026-10-08";
	private static final String SECRET_REASON = "Rahasia koreksi HR uji";
	private static final String LEAVE_REASON = "Alasan izin rahasia";

	private record Person(long branch, long employee, String pin, String leaveTypeName, RequestPostProcessor login) {
	}

	/**
	 * Satu karyawan dengan semua data sensitif terisi: No. HP, PIN, izin disetujui, koreksi beralasan, dan izin
	 * yang masih menunggu.
	 */
	private Person personWithSensitiveData() throws Exception {
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

		return new Person(branch, employee, pin, typeName, employeeLogin(employee));
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
		String username = unique("akun-");
		mockMvc.perform(post("/api/users").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\":\"" + username + "\",\"password\":\"rahasia-uji-123\",\"role\":\"EMPLOYEE\","
					+ "\"employeeId\":" + employeeId + "}"))
			.andExpect(status().isCreated());
		return user(username).roles("EMPLOYEE");
	}

	private ResultActions mine(RequestPostProcessor who, String from, String to) throws Exception {
		return mockMvc.perform(get("/api/me/attendance").param("from", from).param("to", to).with(who));
	}

	private static String body(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString();
	}

	@Test
	void employeeSeesOwnAttendanceOnly() throws Exception {
		Person p = personWithSensitiveData();
		// Karyawan lain tidak pernah melihat data orang ini; ia hanya mendapat miliknya sendiri.
		long otherEmployee = createEmployee(createBranch(), null);
		RequestPostProcessor other = employeeLogin(otherEmployee);

		mine(p.login(), SCAN_DAY, SCAN_DAY).andExpect(status().isOk())
			.andExpect(jsonPath("$.employeeId").value(p.employee()))
			.andExpect(jsonPath("$.position").value("Staf gudang"))
			.andExpect(jsonPath("$.days[0].status").value("HADIR"))
			.andExpect(jsonPath("$.days[0].checkIn").value(startsWith("08:10")))
			.andExpect(jsonPath("$.days[0].checkOut").value(startsWith("17:30")))
			.andExpect(jsonPath("$.days[0].lateSeconds").value(600))
			.andExpect(jsonPath("$.days[0].overtimeSeconds").value(1800));
		mine(other, SCAN_DAY, SCAN_DAY).andExpect(status().isOk())
			.andExpect(jsonPath("$.employeeId").value(otherEmployee));
	}

	@Test
	void responsesNeverContainSensitiveData() throws Exception {
		Person p = personWithSensitiveData();
		String[] forbiddenKeys = { "\"phone\"", "\"devicePin\"", "\"categoryName\"", "\"leaveTypeName\"",
				"\"requestId\"", "\"reason\"", "\"scans\"", "\"flags\"", "\"scanFlags\"", "\"correction\"",
				"\"source\"", "\"leave\"", "\"ruleMissing\"", "\"joinDate\"" };
		String[] forbiddenValues = { "081299990000", p.pin(), p.leaveTypeName(), SECRET_REASON, LEAVE_REASON,
				"Cepat sembuh", "KOREKSI" };

		String body = body(mine(p.login(), SCAN_DAY, PENDING_DAY).andExpect(status().isOk()));
		for (String key : forbiddenKeys) {
			assertThat(body).as("kunci %s tidak boleh ada", key).doesNotContain(key);
		}
		for (String value : forbiddenValues) {
			assertThat(body).as("nilai %s tidak boleh ada", value).doesNotContain(value);
		}
	}

	@Test
	void statusesFollowRecap() throws Exception {
		Person p = personWithSensitiveData();

		mine(p.login(), SCAN_DAY, PENDING_DAY).andExpect(status().isOk())
			.andExpect(jsonPath("$.days", hasSize(4)))
			.andExpect(jsonPath("$.days[0].status").value("HADIR"))
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
	void approvedLeaveWithoutRuleIsStillIzin() throws Exception {
		long employee = createEmployee(createBranch(), null);
		approve(idOf(submitLeave(employee, createLeaveType(unique("Cuti-uji-")), LEAVE_DAY)));

		mine(employeeLogin(employee), SCAN_DAY, LEAVE_DAY).andExpect(status().isOk())
			.andExpect(jsonPath("$.days[0].status").value("BELUM_DIATUR"))
			.andExpect(jsonPath("$.days[1].status").value("IZIN"));
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

		mine(employeeLogin(employee), SCAN_DAY, SCAN_DAY)
			.andExpect(jsonPath("$.days[0].checkIn").value(anyOf(is("08:10"), is("08:10:00"))))
			.andExpect(jsonPath("$.days[0].checkOut").value(anyOf(is("17:30"), is("17:30:00"))))
			.andExpect(jsonPath("$.days[0].lateSeconds").value(600))
			.andExpect(jsonPath("$.days[0].overtimeSeconds").value(1800));
	}

	@Test
	void accountWithoutEmployeeGets404() throws Exception {
		mine(OWNER, SCAN_DAY, SCAN_DAY).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Data karyawan untuk akun ini")));
	}

	@Test
	void inactiveEmployeeGets404() throws Exception {
		long employee = createEmployee(createBranch(), null);
		RequestPostProcessor login = employeeLogin(employee);
		jdbc.update("update employees set active = false where id = ?", employee);

		mine(login, SCAN_DAY, SCAN_DAY).andExpect(status().isNotFound());
	}

	/** Tanggal yang belum terjadi akan tampil "Tidak hadir" palsu dan membocorkan izin mendatang. */
	@Test
	void futureRangeIsRejected() throws Exception {
		RequestPostProcessor login = employeeLogin(createEmployee(createBranch(), null));
		mine(login, "2098-12-01", "2099-01-01").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Tanggal belum terjadi")));
	}

	@Test
	void historyRangeIsLimited() throws Exception {
		RequestPostProcessor login = employeeLogin(createEmployee(createBranch(), null));
		mine(login, "2026-01-01", "2026-06-30").andExpect(status().isBadRequest());
		mine(login, "2026-10-10", "2026-10-01").andExpect(status().isBadRequest());
	}

	/** K-127: endpoint daftar dan riwayat rekan kerja dihapus. */
	@Test
	void teamEndpointsAreGone() throws Exception {
		long employee = createEmployee(createBranch(), null);
		RequestPostProcessor login = employeeLogin(employee);
		mockMvc.perform(get("/api/team/attendance").with(login)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/team/employees/" + employee + "/attendance").with(login))
			.andExpect(status().isForbidden());
	}

}
