package com.biometricattendance.fingerprint;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.biometricattendance.audit.AuditService;
import com.biometricattendance.device.Device;
import com.biometricattendance.device.DeviceRepository;
import com.biometricattendance.devicecommand.EnrollSucceededEvent;

/**
 * Mencatat jari yang berhasil didaftarkan. Sinkron di transaksi hasil perintah: hasil, catatan jari, dan auditnya
 * tersimpan bersama atau tidak sama sekali. Pendaftaran ulang jari yang sama mengganti catatan lama (satu baris per
 * jari), dengan isi sebelum dan sesudahnya di audit.
 */
@Component
class EnrollmentRecorder {

	private final EmployeeFingerprintRepository fingerprints;
	private final DeviceRepository devices;
	private final AuditService audit;

	EnrollmentRecorder(EmployeeFingerprintRepository fingerprints, DeviceRepository devices, AuditService audit) {
		this.fingerprints = fingerprints;
		this.devices = devices;
		this.audit = audit;
	}

	@EventListener
	void onEnrollSucceeded(EnrollSucceededEvent event) {
		Device device = devices.getReferenceById(event.deviceId());
		fingerprints.findByEmployeeIdAndFinger(event.employeeId(), event.finger()).ifPresentOrElse(f -> {
			Map<String, Object> before = f.snapshot();
			f.replace(device, event.commandId(), event.at());
			record(event, "FINGERPRINT_REENROLLED", f, before);
		}, () -> {
			EmployeeFingerprint f = fingerprints.saveAndFlush(new EmployeeFingerprint(event.employeeId(),
					event.finger(), device, event.commandId(), event.at()));
			record(event, "FINGERPRINT_ENROLLED", f, null);
		});
	}

	private void record(EnrollSucceededEvent event, String action, EmployeeFingerprint f, Map<String, Object> before) {
		audit.record(event.actor(), action, "EmployeeFingerprint", f.getId(), before, f.snapshot(), null);
	}

}
