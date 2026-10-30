package com.biometricattendance.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.biometricattendance.ingest.RakitanFixtures;

/**
 * Karyawan keluar atau nonaktif (spec §17.4, K-090): sidik jari dihapus saat dinonaktifkan atau setelah tanggal keluar
 * (hari kerja terakhir) lewat menurut zona waktu cabang, sekali saja.
 */
class FingerprintDepartureTests extends RakitanFixtures {

	@Autowired
	private FingerprintRemoval removal;

	@Test
	void deactivateRemovesFingersAndQueuesDeleteEmployee() throws Exception {
		long branch = createBranch();
		Rakitan a = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		registerFinger(employee, a, 1);
		String enroll = enrollOk(employee, a.id(), 2);

		saveEmployee(employee, null, false).andExpect(status().isOk());

		assertThat(fingerRows(employee)).isZero();
		assertThat(commandStatus(enroll)).isEqualTo("DIBATALKAN");
		String delete = deleteCommandFor(employee, a.id());
		assertThat(jdbc.queryForObject("select type from device_commands where id = ?::uuid", String.class, delete))
			.isEqualTo("DELETE_EMPLOYEE");
		assertThat(removedAt(employee)).isNotNull();
		assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'FINGERPRINT_DELETED'"
				+ " and actor_username = 'owner-absensi' and before_data like ?", Integer.class,
				"%\"deviceId\":" + a.id() + ",%")).isEqualTo(1);
	}

	@Test
	void pastEndDateRemovesImmediately() throws Exception {
		long branch = createBranch();
		Rakitan a = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		registerFinger(employee, a, 1);

		saveEmployee(employee, "2026-02-01", true).andExpect(status().isOk());

		assertThat(fingerRows(employee)).isZero();
		assertThat(deleteCommandFor(employee, a.id())).isNotNull();
	}

	@Test
	void futureEndDateKeepsFingersUntilSweep() throws Exception {
		long branch = createBranch();
		Rakitan a = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		registerFinger(employee, a, 1);
		LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Jakarta")).plusDays(1);

		saveEmployee(employee, tomorrow.toString(), true).andExpect(status().isOk());

		assertThat(fingerRows(employee)).isEqualTo(1);
		assertThat(deleteCommandFor(employee, a.id())).isNull();
		enroll(employee, a.id(), 2).andExpect(status().isCreated());
	}

	@Test
	void departureUsesBranchTimezone() throws Exception {
		long branch = createBranch();
		jdbc.update("update branches set timezone = 'Asia/Jayapura' where id = ?", branch);
		Rakitan a = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		registerFinger(employee, a, 1);
		// Tanggal keluar di masa depan dulu agar tidak langsung terhapus, lalu dimundurkan ke 2026-10-18.
		saveEmployee(employee, "2099-01-01", true).andExpect(status().isOk());
		jdbc.update("update employees set end_date = date '2026-10-18' where id = ?", employee);

		removal.sweepDepartures(Instant.parse("2026-10-18T14:30:00Z"));
		assertThat(fingerRows(employee)).as("23.30 tanggal 18 di Jayapura").isEqualTo(1);

		removal.sweepDepartures(Instant.parse("2026-10-18T15:30:00Z"));
		assertThat(fingerRows(employee)).as("00.30 tanggal 19 di Jayapura").isZero();
		assertThat(jdbc.queryForObject("select created_by from device_commands where id = ?::uuid", String.class,
				deleteCommandFor(employee, a.id()))).isEqualTo("sistem");
	}

	@Test
	void departureCheckRunsOnce() throws Exception {
		long branch = createBranch();
		Rakitan a = onlineRakitan(branch);
		long employee = readyEmployee(branch);
		registerFinger(employee, a, 1);
		saveEmployee(employee, "2099-01-01", true).andExpect(status().isOk());
		jdbc.update("update employees set end_date = date '2026-10-01' where id = ?", employee);

		removal.sweepDepartures(Instant.parse("2026-10-18T03:00:00Z"));
		removal.sweepDepartures(Instant.parse("2026-10-18T04:00:00Z"));

		assertThat(jdbc.queryForObject("select count(*) from device_commands where employee_id = ? and device_id = ?"
				+ " and type = 'DELETE_EMPLOYEE'", Integer.class, employee, a.id())).isEqualTo(1);
	}

	@Test
	void noConsentNoDeleteEmployee() throws Exception {
		long branch = createBranch();
		Rakitan a = onlineRakitan(branch);
		long employee = createEmployee(branch, unique("P"));

		saveEmployee(employee, null, false).andExpect(status().isOk());

		assertThat(deleteCommandFor(employee, a.id())).isNull();
		assertThat(removedAt(employee)).isNotNull();
	}

	@Test
	void reactivateClearsMarker() throws Exception {
		long branch = createBranch();
		long employee = readyEmployee(branch);
		saveEmployee(employee, null, false).andExpect(status().isOk());

		saveEmployee(employee, null, true).andExpect(status().isOk());

		assertThat(removedAt(employee)).isNull();
	}

	private int fingerRows(long employee) {
		return jdbc.queryForObject("select count(*) from employee_fingerprints where employee_id = ?", Integer.class,
				employee);
	}

	private Object removedAt(long employee) {
		return jdbc.queryForObject("select fingerprints_removed_at from employees where id = ?", Object.class,
				employee);
	}

}
