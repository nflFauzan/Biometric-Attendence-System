package com.biometricattendance.fingerprint;

import java.time.Instant;
import java.util.Objects;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.devicecommand.DeviceCommandRepository;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeChangedEvent;

/**
 * Reaksi modul sidik jari terhadap perubahan karyawan, sinkron di transaksi yang sama: kunci PIN (K-093), lalu
 * penghapusan sidik jari saat karyawan keluar atau nonaktif (K-090, spec §17.4).
 */
@Component
class EmployeeFingerprintListener {

	private final FingerprintRemoval removal;
	private final EmployeeFingerprintRepository fingerprints;
	private final DeviceCommandRepository commands;

	EmployeeFingerprintListener(FingerprintRemoval removal, EmployeeFingerprintRepository fingerprints,
			DeviceCommandRepository commands) {
		this.removal = removal;
		this.fingerprints = fingerprints;
		this.commands = commands;
	}

	@EventListener
	void onEmployeeChanged(EmployeeChangedEvent event) {
		Employee employee = event.employee();
		checkPinLock(event);
		if (event.created()) {
			return;
		}
		Instant now = Instant.now();
		boolean departed = removal.isDeparted(employee, now);
		if (departed && employee.getFingerprintsRemovedAt() == null) {
			removal.removeAll(employee, CurrentUser.username(), now);
		}
		else if (!departed && employee.getFingerprintsRemovedAt() != null) {
			employee.clearFingerprintsRemoved();
		}
	}

	/**
	 * PIN terkunci selama pendaftaran berjalan, masih ada jari terdaftar, atau penghapusan di mesin aktif belum selesai, supaya sidik jari
	 * lama di sensor tidak tercatat atas nama orang lain. Dijalankan sebelum penghapusan agar penolakan membatalkan
	 * seluruh perubahan.
	 */
	private void checkPinLock(EmployeeChangedEvent event) {
		Employee employee = event.employee();
		String pin = employee.getDevicePin();
		String before = event.pinBefore();
		if (Objects.equals(before, pin)) {
			return;
		}
		// ENROLL membawa PIN saat perintah dibuat; diperiksa sebelum jari terdaftar karena hasil BERHASIL menyimpan
		// baris jari di transaksi lain (final review R5 #1).
		if (!event.created() && !commands.findActiveForEmployee(employee.getId()).isEmpty()) {
			throw ApiException.badRequest("Tunggu sampai pendaftaran sidik jari selesai sebelum mengganti PIN.");
		}
		if (!event.created() && !fingerprints.findByEmployeeIdOrderByFinger(employee.getId()).isEmpty()) {
			throw ApiException.badRequest("Hapus sidik jari dulu sebelum mengganti PIN.");
		}
		if (!event.created() && before != null && commands.existsPendingDeleteForPin(before)) {
			throw ApiException.badRequest("Tunggu sampai sidik jari selesai dihapus di semua mesin.");
		}
		if (pin != null && commands.existsPendingDeleteForPin(pin)) {
			throw ApiException.badRequest("PIN ini masih menunggu penghapusan sidik jari di mesin.");
		}
	}

}
