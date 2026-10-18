package com.biometricattendance.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.biometricattendance.employee.EmployeeRepository;
import com.biometricattendance.ingest.RakitanFixtures;

/**
 * Persetujuan sidik jari dan edit karyawan oleh HR menulis kolom berbeda, jadi tidak boleh saling menimpa (M4). Di
 * dalam satu transaksi, JdbcTemplate memakai koneksi yang sama dengan JPA: perubahan "pengguna lain" ditulis setelah
 * entity dimuat dan sebelum flush.
 */
class FingerprintConsentRaceTests extends RakitanFixtures {

	@Autowired
	private FingerprintService fingerprintService;

	@Autowired
	private EmployeeRepository employees;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@AfterEach
	void clearUser() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void consentKeepsConcurrentEmployeeEdit() throws Exception {
		long id = createEmployee(createBranch(), unique("P"));
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken("owner-absensi", null, List.of()));

		tx().executeWithoutResult(s -> {
			fingerprintService.consent(id);
			jdbc.update("update employees set full_name = 'Nama Baru' where id = ?", id);
		});

		Map<String, Object> row = row(id);
		assertThat(row.get("full_name")).isEqualTo("Nama Baru");
		assertThat(row.get("fingerprint_consent_at")).isNotNull();
	}

	@Test
	void employeeEditKeepsConcurrentConsent() throws Exception {
		long id = createEmployee(createBranch(), unique("P"));

		tx().executeWithoutResult(s -> {
			var employee = employees.findById(id).orElseThrow();
			jdbc.update("update employees set fingerprint_consent_at = now(), fingerprint_consent_by = 'hr'"
					+ " where id = ?", id);
			employee.setFullName("Nama Baru");
		});

		Map<String, Object> row = row(id);
		assertThat(row.get("fingerprint_consent_by")).isEqualTo("hr");
		assertThat(row.get("full_name")).isEqualTo("Nama Baru");
	}

	private Map<String, Object> row(long id) {
		return jdbc.queryForMap(
				"select full_name, fingerprint_consent_at, fingerprint_consent_by from employees where id = ?", id);
	}

	private TransactionTemplate tx() {
		return new TransactionTemplate(transactionManager);
	}

}
