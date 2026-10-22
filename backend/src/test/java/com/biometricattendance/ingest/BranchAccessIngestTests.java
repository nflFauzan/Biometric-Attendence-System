package com.biometricattendance.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.biometricattendance.AttendanceFixtures;

/** K-103: penanda cabang saat scan diterima (CB-01, CB-02, CB-03, CB-06, CB-07). */
class BranchAccessIngestTests extends AttendanceFixtures {

	private void addExtraBranch(long employeeId, long branchId) throws Exception {
		mockMvc.perform(put("/api/employees/" + employeeId + "/branches").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"extraBranchIds\":[" + branchId + "]}")).andExpect(status().isOk());
	}

	private List<String> flagsOf(long deviceId) {
		return jdbc.queryForList("select flags from attendance_scans where device_id = ? order by scanned_at",
				String.class, deviceId);
	}

	/** CB-01 */
	@Test
	void scanAtPlacementHasNoBranchFlag() throws Exception {
		long b1 = createBranch();
		String pin = uniquePin();
		createEmployee(b1, pin);
		String sn = uniqueSerial();
		long device = createDevice(b1, sn, ACCEPT_FROM);

		postAttlog(sn, lines(line(pin, "2026-10-05 08:00:00"))).andExpect(content().string("OK"));

		assertThat(flagsOf(device)).containsExactly((String) null);
	}

	/** CB-02 */
	@Test
	void scanAtExtraBranchIsCabangBerbeda() throws Exception {
		long b1 = createBranch();
		long b2 = createBranch();
		String pin = uniquePin();
		long employee = createEmployee(b1, pin);
		addExtraBranch(employee, b2);
		String sn = uniqueSerial();
		long device = createDevice(b2, sn, ACCEPT_FROM);

		postAttlog(sn, lines(line(pin, "2026-10-05 08:00:00"))).andExpect(content().string("OK"));

		assertThat(flagsOf(device)).containsExactly("CABANG_BERBEDA");
	}

	/** CB-03 (sisi ingest): ditandai saja, tetap tersimpan. */
	@Test
	void scanAtOtherBranchIsHeld() throws Exception {
		long b1 = createBranch();
		long b2 = createBranch();
		String pin = uniquePin();
		long employee = createEmployee(b1, pin);
		String sn = uniqueSerial();
		long device = createDevice(b2, sn, ACCEPT_FROM);

		postAttlog(sn, lines(line(pin, "2026-10-05 08:00:00"))).andExpect(content().string("OK"));

		assertThat(scanCount(device)).isEqualTo(1);
		assertThat(flagsOf(device)).containsExactly("CABANG_TIDAK_DIIZINKAN");
		assertThat(jdbc.queryForObject("select employee_id from attendance_scans where device_id = ?", Long.class,
				device)).isEqualTo(employee);
	}

	/** CB-06: perubahan cabang kemudian tidak mengubah penanda scan lama. */
	@Test
	void laterBranchChangeDoesNotReflag() throws Exception {
		long b1 = createBranch();
		long b2 = createBranch();
		String pin = uniquePin();
		long employee = createEmployee(b1, pin);
		String sn = uniqueSerial();
		long device = createDevice(b2, sn, ACCEPT_FROM);
		postAttlog(sn, lines(line(pin, "2026-10-05 08:00:00"))).andExpect(content().string("OK"));

		addExtraBranch(employee, b2);

		assertThat(flagsOf(device)).containsExactly("CABANG_TIDAK_DIIZINKAN");
	}

	/** CB-07 */
	@Test
	void linkingUnknownPinFlagsDisallowedBranch() throws Exception {
		long b1 = createBranch();
		long b2 = createBranch();
		String pin = uniquePin();
		String snOther = uniqueSerial();
		long deviceOther = createDevice(b2, snOther, ACCEPT_FROM);
		String snHome = uniqueSerial();
		long deviceHome = createDevice(b1, snHome, ACCEPT_FROM);
		postAttlog(snOther, lines(line(pin, "2026-10-05 08:00:00"))).andExpect(content().string("OK"));
		postAttlog(snHome, lines(line(pin, "2026-10-05 09:00:00"))).andExpect(content().string("OK"));
		assertThat(flagsOf(deviceOther)).containsExactly("PIN_TIDAK_DIKENAL");

		String number = unique("E");
		long employee = idOf(mockMvc.perform(post("/api/employees").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(employeeJson(b1, number, null))).andExpect(status().isCreated()));
		updateEmployeePin(employee, b1, number, pin).andExpect(status().isOk());

		Map<String, Object> other = jdbc.queryForMap("select employee_id, flags from attendance_scans where device_id = ?",
				deviceOther);
		assertThat(((Number) other.get("employee_id")).longValue()).isEqualTo(employee);
		assertThat(other.get("flags")).isEqualTo("CABANG_TIDAK_DIIZINKAN");
		assertThat(flagsOf(deviceHome)).containsExactly((String) null);
	}

	/** Penautan juga menjaga WAKTU_DI_MASA_DEPAN dan menghormati cabang tambahan. */
	@Test
	void linkingKeepsFutureFlagAndHonoursExtraBranch() throws Exception {
		long b1 = createBranch();
		long b2 = createBranch();
		long b3 = createBranch();
		String pin = uniquePin();
		String sn2 = uniqueSerial();
		long device2 = createDevice(b2, sn2, ACCEPT_FROM);
		String sn3 = uniqueSerial();
		long device3 = createDevice(b3, sn3, ACCEPT_FROM);
		postAttlog(sn2, lines(line(pin, "2099-01-01 08:00:00"))).andExpect(content().string("OK"));
		postAttlog(sn3, lines(line(pin, "2026-10-05 08:00:00"))).andExpect(content().string("OK"));

		String number = unique("E");
		long employee = idOf(mockMvc.perform(post("/api/employees").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(employeeJson(b1, number, null))).andExpect(status().isCreated()));
		addExtraBranch(employee, b2);
		updateEmployeePin(employee, b1, number, pin).andExpect(status().isOk());

		assertThat(flagsOf(device2)).containsExactly("WAKTU_DI_MASA_DEPAN");
		assertThat(flagsOf(device3)).containsExactly("CABANG_TIDAK_DIIZINKAN");
	}

	@Test
	void unknownPinGetsNoBranchFlag() throws Exception {
		long b1 = createBranch();
		String sn = uniqueSerial();
		long device = createDevice(b1, sn, ACCEPT_FROM);

		postAttlog(sn, lines(line(uniquePin(), "2026-10-05 08:00:00"))).andExpect(content().string("OK"));

		assertThat(flagsOf(device)).containsExactly("PIN_TIDAK_DIKENAL");
	}

}
