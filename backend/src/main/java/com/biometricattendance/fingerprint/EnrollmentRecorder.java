package com.biometricattendance.fingerprint;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.biometricattendance.device.Device;
import com.biometricattendance.device.DeviceRepository;
import com.biometricattendance.devicecommand.EnrollSucceededEvent;

/**
 * Mencatat jari yang berhasil didaftarkan. Sinkron di transaksi hasil perintah: hasil dan catatan jari tersimpan
 * bersama atau tidak sama sekali. Pendaftaran ulang jari yang sama mengganti catatan lama (satu baris per jari).
 */
@Component
class EnrollmentRecorder {

	private final EmployeeFingerprintRepository fingerprints;
	private final DeviceRepository devices;

	EnrollmentRecorder(EmployeeFingerprintRepository fingerprints, DeviceRepository devices) {
		this.fingerprints = fingerprints;
		this.devices = devices;
	}

	@EventListener
	void onEnrollSucceeded(EnrollSucceededEvent event) {
		Device device = devices.getReferenceById(event.deviceId());
		fingerprints.findByEmployeeIdAndFinger(event.employeeId(), event.finger())
			.ifPresentOrElse(f -> f.replace(device, event.commandId(), event.at()),
					() -> fingerprints.save(new EmployeeFingerprint(event.employeeId(), event.finger(), device,
							event.commandId(), event.at())));
	}

}
