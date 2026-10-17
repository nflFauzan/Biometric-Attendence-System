package com.biometricattendance.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.biometricattendance.device.Device;
import com.biometricattendance.device.DeviceKeys;
import com.biometricattendance.device.DeviceRepository;

/**
 * Heartbeat yang berjalan bersamaan dengan "Buat kode baru" tidak boleh mengembalikan kunci lama, dan kontak
 * pertama dicatat sekali per kunci (K-088).
 */
class RakitanKeyRaceTests extends RakitanFixtures {

	@Autowired
	private DeviceRepository devices;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void contactRecordedAfterNewKeyDoesNotRestoreOldKey() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());
		String newHash = DeviceKeys.hash(DeviceKeys.newKey());

		new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
			Device stale = devices.findByKeyHash(DeviceKeys.hash(rakitan.key())).orElseThrow();
			// "Buat kode baru" tersimpan saat heartbeat dengan kunci lama masih berjalan.
			jdbc.update("update devices set key_hash = ?, key_first_used_at = null where id = ?", newHash,
					rakitan.id());
			stale.recordContact(Instant.now(), "10.0.0.9", "0.1.0", null);
		});

		assertThat(jdbc.queryForObject("select key_hash from devices where id = ?", String.class, rakitan.id()))
			.isEqualTo(newHash);
	}

	@Test
	void firstUseIsClaimedOncePerKey() throws Exception {
		Rakitan rakitan = createRakitan(createBranch());
		String oldHash = DeviceKeys.hash(rakitan.key());
		assertThat(claim(rakitan.id(), oldHash)).isEqualTo(1);
		assertThat(claim(rakitan.id(), oldHash)).isEqualTo(0);

		String newHash = DeviceKeys.hash(issueNewKey(rakitan.id()));
		// Heartbeat terlambat dengan kunci lama tidak boleh "memakai" kontak pertama kunci baru.
		assertThat(claim(rakitan.id(), oldHash)).isEqualTo(0);
		assertThat(claim(rakitan.id(), newHash)).isEqualTo(1);
	}

	private int claim(long id, String keyHash) {
		Integer updated = new TransactionTemplate(transactionManager)
			.execute(s -> devices.claimKeyFirstUse(id, keyHash, Instant.now()));
		return updated;
	}

}
