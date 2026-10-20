package com.biometricattendance.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.biometricattendance.ingest.RakitanFixtures;

/**
 * Kunci PIN (K-093 diperluas, spec §17.5): PIN tidak bisa diganti selama masih ada jari terdaftar atau penghapusan di
 * mesin aktif belum selesai, dan PIN yang menunggu penghapusan tidak bisa dipakai karyawan lain.
 */
class PinLockTests extends RakitanFixtures {

	private static final String HAS_FINGERS = "Hapus sidik jari dulu sebelum mengganti PIN.";
	private static final String WAIT_DELETE = "Tunggu sampai sidik jari selesai dihapus di semua mesin.";
	private static final String ENROLL_ACTIVE = "Tunggu sampai pendaftaran sidik jari selesai sebelum mengganti PIN.";
	private static final String PIN_PENDING ="PIN ini masih menunggu penghapusan sidik jari di mesin.";

	@Test
	void pinLockedWhileFingerRegistered() throws Exception {
		long branch = createBranch();
		long employee = readyEmployee(branch);
		registerFinger(employee, onlineRakitan(branch), 1);

		expectRejected(saveEmployee(employee, unique("P"), null, true), HAS_FINGERS);
		expectRejected(saveEmployee(employee, null, null, true), HAS_FINGERS);
	}

	@Test
	void pinLockedUntilAllMachinesDelete() throws Exception {
		long branch = createBranch();
		Rakitan first = onlineRakitan(branch);
		Rakitan second = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		registerFinger(employee, first, 1);
		deleteFinger(employee, 1).andExpect(status().isOk());
		finishOtherMachines(employee, List.of(first.id(), second.id()));

		expectRejected(saveEmployee(employee, unique("P"), null, true), WAIT_DELETE);
		report(first, employee, "{\"status\":\"BERHASIL\"}");
		expectRejected(saveEmployee(employee, unique("P"), null, true), WAIT_DELETE);
		report(second, employee, "{\"status\":\"BERHASIL\"}");
		saveEmployee(employee, unique("P"), null, true).andExpect(status().isOk());
	}

	@Test
	void failedDeleteKeepsLock() throws Exception {
		long branch = createBranch();
		Rakitan a = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		registerFinger(employee, a, 1);
		deleteFinger(employee, 1).andExpect(status().isOk());
		finishOtherMachines(employee, List.of(a.id()));

		report(a, employee, "{\"status\":\"GAGAL\",\"error\":\"SENSOR_ERROR\"}");

		expectRejected(saveEmployee(employee, unique("P"), null, true), WAIT_DELETE);
	}

	@Test
	void deactivatingMachineUnlocksPin() throws Exception {
		long branch = createBranch();
		Rakitan a = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		registerFinger(employee, a, 1);
		deleteFinger(employee, 1).andExpect(status().isOk());
		finishOtherMachines(employee, List.of(a.id()));
		expectRejected(saveEmployee(employee, unique("P"), null, true), WAIT_DELETE);

		deactivate(a, branch);

		saveEmployee(employee, unique("P"), null, true).andExpect(status().isOk());
	}

	@Test
	void pendingPinCannotBeTakenByNewEmployee() throws Exception {
		long branch = createBranch();
		Rakitan a = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		String pin = pinOf(employee);
		registerFinger(employee, a, 1);
		deleteFinger(employee, 1).andExpect(status().isOk());
		finishOtherMachines(employee, List.of(a.id()));
		// Lepas PIN lewat jalur sah (mesin nonaktif), lalu mesin aktif kembali dengan penghapusan yang masih tertunda.
		deactivate(a, branch);
		saveEmployee(employee, null, null, true).andExpect(status().isOk());
		setActive(a, branch, true);

		expectRejected(mockMvc.perform(post("/api/employees").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(employeeJson(branch, unique("E"), pin))), PIN_PENDING);
	}

	@Test
	void deactivateAndChangePinTogetherIsRejected() throws Exception {
		long branch = createBranch();
		long employee = readyEmployee(branch);
		registerFinger(employee, onlineRakitan(branch), 1);

		expectRejected(saveEmployee(employee, unique("P"), null, false), HAS_FINGERS);

		assertThat(jdbc.queryForObject("select count(*) from employee_fingerprints where employee_id = ?",
				Integer.class, employee)).isEqualTo(1);
	}

	/** Final review R5 #1: perintah ENROLL membawa PIN saat dibuat, jadi PIN terkunci sampai pendaftaran selesai. */
	@Test
	void pinLockedWhileEnrollInFlight() throws Exception {
		long branch = createBranch();
		Rakitan a = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		String id = enrollOk(employee, a.id(), 1);

		expectRejected(saveEmployee(employee, unique("P"), null, true), ENROLL_ACTIVE);
		heartbeat(a.key(), "{}").andExpect(status().isOk());
		expectRejected(saveEmployee(employee, null, null, true), ENROLL_ACTIVE);

		result(a.key(), id, "{\"status\":\"GAGAL\",\"error\":\"SENSOR_ERROR\"}").andExpect(status().isOk());
		saveEmployee(employee, unique("P"), null, true).andExpect(status().isOk());
	}

	@Test
	void unchangedPinIsNotChecked() throws Exception {
		long branch = createBranch();
		Rakitan a = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		registerFinger(employee, a, 1);
		deleteFinger(employee, 1).andExpect(status().isOk());

		saveEmployee(employee, null, true).andExpect(status().isOk());
	}

	/** Mesin ini mengambil perintah hapus terbarunya untuk karyawan lalu melaporkan hasil. */
	private void report(Rakitan rakitan, long employee, String body) throws Exception {
		String id = deleteCommandFor(employee, rakitan.id());
		heartbeat(rakitan.key(), "{}").andExpect(status().isOk());
		result(rakitan.key(), id, body).andExpect(status().isOk());
	}

	/** Basis data uji dipakai bersama: perintah hapus ke mesin milik test lain dianggap sudah selesai. */
	private void finishOtherMachines(long employee, List<Long> keep) {
		jdbc.update("update device_commands set status = 'BERHASIL', finished_at = now() where employee_id = ?"
				+ " and type like 'DELETE%' and not (device_id = any (?))", employee,
				keep.toArray(new Long[0]));
	}

	private String pinOf(long employee) {
		return jdbc.queryForObject("select device_pin from employees where id = ?", String.class, employee);
	}

	private static void expectRejected(ResultActions result, String message) throws Exception {
		result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(message));
	}

}
