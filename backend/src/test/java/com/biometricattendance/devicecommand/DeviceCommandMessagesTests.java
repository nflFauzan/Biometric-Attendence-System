package com.biometricattendance.devicecommand;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.biometricattendance.device.Device;
import com.biometricattendance.employee.Employee;

/** Pesan status perintah untuk web (spec §9, §16). */
class DeviceCommandMessagesTests {

	private static final Instant NOW = Instant.parse("2026-10-18T02:00:00Z");

	@Test
	void activeCommandShowsWaitingThenStep() {
		DeviceCommand command = enroll();
		assertThat(DeviceCommandMessages.message(command)).isEqualTo("Menunggu mesin…");
		command.markSent(NOW);
		assertThat(DeviceCommandMessages.message(command)).isEqualTo("Menunggu mesin…");
		command.recordProgress(2);
		assertThat(DeviceCommandMessages.message(command)).isEqualTo("Tempelkan jari (2/3)");
	}

	@Test
	void finalStatuses() {
		DeviceCommand ok = enroll();
		ok.succeed(NOW);
		assertThat(DeviceCommandMessages.message(ok)).isEqualTo("Berhasil");

		DeviceCommand expired = enroll();
		expired.expire(NOW);
		assertThat(DeviceCommandMessages.message(expired))
			.isEqualTo("Mesin tidak mengambil perintah. Periksa koneksi mesin.");

		DeviceCommand cancelled = enroll();
		cancelled.cancel(NOW);
		assertThat(DeviceCommandMessages.message(cancelled)).isEqualTo("Pendaftaran dibatalkan.");
	}

	@Test
	void failureCodes() {
		assertThat(failed("JARI_TIDAK_COCOK", null))
			.isEqualTo("Tiga tempelan tidak cocok satu sama lain. Ulangi dengan jari yang sama.");
		assertThat(failed("KUALITAS_BURUK", null))
			.isEqualTo("Sidik jari tidak terbaca jelas. Bersihkan jari atau sensor, lalu ulangi.");
		assertThat(failed("JARI_SUDAH_TERDAFTAR", "Budi")).isEqualTo("Sidik jari ini sudah terdaftar atas nama Budi.");
		assertThat(failed("SENSOR_PENUH", null)).isEqualTo("Kapasitas sensor mesin penuh.");
		assertThat(failed("WAKTU_HABIS", null)).isEqualTo("Tidak ada jari yang ditempelkan. Ulangi pendaftaran.");
		assertThat(failed("TIDAK_ADA_HASIL", null)).isEqualTo("Mesin tidak merespons. Periksa koneksi mesin.");
		assertThat(failed("SENSOR_ERROR", null)).isEqualTo("Sensor bermasalah. Hubungi teknisi.");
		assertThat(failed("KODE_ANEH", null)).isEqualTo("Pendaftaran gagal (KODE_ANEH).");
	}

	@Test
	void deleteMessages() {
		assertThat(DeviceCommandMessages.message(delete())).isEqualTo("Menunggu mesin…");
		DeviceCommand done = delete();
		done.succeed(NOW);
		assertThat(DeviceCommandMessages.message(done)).isEqualTo("Terhapus");
		DeviceCommand odd = delete();
		odd.fail("X", null, NOW);
		assertThat(DeviceCommandMessages.message(odd)).isEqualTo("Penghapusan gagal (X).");
		DeviceCommand sensor = delete();
		sensor.fail("SENSOR_ERROR", null, NOW);
		assertThat(DeviceCommandMessages.message(sensor)).isEqualTo("Sensor bermasalah. Hubungi teknisi.");
	}

	private static DeviceCommand delete() {
		Employee employee = new Employee();
		employee.setDevicePin("9001");
		return DeviceCommand.deleteTemplate(new Device(), employee, "9001", 1, "owner", NOW);
	}

	private static String failed(String code, String detail) {
		DeviceCommand command = enroll();
		command.fail(code, detail, NOW);
		return DeviceCommandMessages.message(command);
	}

	private static DeviceCommand enroll() {
		Employee employee = new Employee();
		employee.setFullName("Budi Santoso");
		employee.setDevicePin("9001");
		return DeviceCommand.enroll(new Device(), employee, 1, "owner", NOW);
	}

}
