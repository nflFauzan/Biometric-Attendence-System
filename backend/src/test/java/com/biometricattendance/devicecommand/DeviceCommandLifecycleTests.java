package com.biometricattendance.devicecommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.biometricattendance.device.DeviceRepository;
import com.biometricattendance.employee.EmployeeRepository;
import com.biometricattendance.ingest.RakitanFixtures;

/** Antrean perintah: satu ENROLL aktif per mesin (unique index) dan batas waktu 30/75 detik (K-094). */
@RecordApplicationEvents
class DeviceCommandLifecycleTests extends RakitanFixtures {

	@Autowired
	private DeviceCommandRepository commands;

	@Autowired
	private DeviceCommandService service;

	@Autowired
	private DeviceRepository devices;

	@Autowired
	private EmployeeRepository employees;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private ApplicationEvents events;

	@Test
	void secondActiveEnrollForDeviceViolatesIndex() throws Exception {
		long branch = createBranch();
		long device = createRakitan(branch).id();
		long first = createEmployee(branch, pin());
		long second = createEmployee(branch, pin());

		save(device, first, Instant.now());
		assertThatThrownBy(() -> save(device, second, Instant.now()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void finishedEnrollDoesNotBlockNewOne() throws Exception {
		long branch = createBranch();
		long device = createRakitan(branch).id();
		long employee = createEmployee(branch, pin());

		UUID first = save(device, employee, Instant.now());
		tx().executeWithoutResult(s -> commands.findById(first).orElseThrow().cancel(Instant.now()));

		assertThat(save(device, employee, Instant.now())).isNotEqualTo(first);
	}

	@Test
	void unpickedEnrollExpiresAfter30Seconds() throws Exception {
		long branch = createBranch();
		Instant t = Instant.now();
		UUID id = save(createRakitan(branch).id(), createEmployee(branch, pin()), t);

		service.expireStale(t.plusSeconds(29));
		assertThat(status(id)).isEqualTo(DeviceCommand.MENUNGGU);

		service.expireStale(t.plusSeconds(31));
		assertThat(status(id)).isEqualTo(DeviceCommand.KEDALUWARSA);
		assertThat(jdbc.queryForObject("select finished_at is not null from device_commands where id = ?",
				Boolean.class, id)).isTrue();
	}

	@Test
	void sentWithoutResultTimesOut() throws Exception {
		long branch = createBranch();
		Instant t = Instant.now();
		UUID id = save(createRakitan(branch).id(), createEmployee(branch, pin()), t);
		tx().executeWithoutResult(s -> commands.findById(id).orElseThrow().markSent(t));

		service.expireStale(t.plusSeconds(74));
		assertThat(status(id)).isEqualTo(DeviceCommand.DIKIRIM);

		service.expireStale(t.plusSeconds(76));
		assertThat(status(id)).isEqualTo(DeviceCommand.GAGAL);
		assertThat(jdbc.queryForObject("select error_code from device_commands where id = ?", String.class, id))
			.isEqualTo("WAKTU_HABIS");
	}

	@Test
	void expiryPublishesChangedEventAndAudit() throws Exception {
		long branch = createBranch();
		Instant t = Instant.now();
		UUID id = save(createRakitan(branch).id(), createEmployee(branch, pin()), t);

		service.expireStale(t.plusSeconds(31));

		assertThat(events.stream(DeviceCommandChangedEvent.class).filter(e -> e.commandId().equals(id)).count())
			.isEqualTo(1);
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_logs where action = 'COMMAND_EXPIRED' and entity_id = ? and actor_username = 'sistem'",
				Integer.class, id.toString()))
			.isEqualTo(1);
	}

	/**
	 * Final review I1: pemeriksaan kedaluwarsa yang berjalan bersamaan (sweeper, admin) tidak boleh gagal karena baris
	 * yang sedang diubah transaksi lain; baris itu dilewati.
	 */
	@Test
	void expirySkipsRowLockedByAnotherTransaction() throws Exception {
		long branch = createBranch();
		Instant t = Instant.now();
		UUID id = save(createRakitan(branch).id(), createEmployee(branch, pin()), t);
		CountDownLatch locked = new CountDownLatch(1);
		Thread holder = new Thread(() -> tx().executeWithoutResult(s -> {
			jdbc.update("update device_commands set status = 'DIBATALKAN', finished_at = now(), version = version + 1"
					+ " where id = ?", id);
			locked.countDown();
			try {
				Thread.sleep(800);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}));
		holder.start();
		assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

		// Basis data uji dipakai bersama, jadi jumlah yang ditandai tidak diperiksa; yang penting tidak gagal/menunggu.
		long started = System.nanoTime();
		assertThatCode(() -> service.expireStale(t.plusSeconds(31))).doesNotThrowAnyException();
		assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).as("tidak menunggu kunci").isLessThan(700);

		holder.join();
		assertThat(status(id)).isEqualTo(DeviceCommand.DIBATALKAN);
	}

	private UUID save(long deviceId, long employeeId, Instant at) {
		return tx().execute(s -> commands.saveAndFlush(DeviceCommand.enroll(devices.findById(deviceId).orElseThrow(),
				employees.findById(employeeId).orElseThrow(), 1, "owner-absensi", at)).getId());
	}

	private String status(UUID id) {
		return jdbc.queryForObject("select status from device_commands where id = ?", String.class, id);
	}

	private TransactionTemplate tx() {
		return new TransactionTemplate(transactionManager);
	}

	private static String pin() {
		return String.valueOf(10000 + (int) (Math.random() * 89999));
	}

}
