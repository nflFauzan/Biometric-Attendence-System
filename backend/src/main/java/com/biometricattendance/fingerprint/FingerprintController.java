package com.biometricattendance.fingerprint;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.devicecommand.DeviceCommandDtos.CommandResponse;
import com.biometricattendance.fingerprint.FingerprintDtos.EnrollRequest;
import com.biometricattendance.fingerprint.FingerprintDtos.FingerprintOverview;

import jakarta.validation.Valid;

/** Bagian "Sidik Jari" di Detail Karyawan (spec §3.2): persetujuan, status per jari, dan mulai pendaftaran. */
@RestController
@RequestMapping("/api/employees/{employeeId}")
public class FingerprintController {

	private final FingerprintService service;

	public FingerprintController(FingerprintService service) {
		this.service = service;
	}

	@GetMapping("/fingerprints")
	FingerprintOverview overview(@PathVariable Long employeeId) {
		return service.overview(employeeId);
	}

	@PostMapping("/fingerprint-consent")
	FingerprintOverview consent(@PathVariable Long employeeId) {
		return service.consent(employeeId);
	}

	@PostMapping("/fingerprints/enroll")
	@ResponseStatus(HttpStatus.CREATED)
	CommandResponse enroll(@PathVariable Long employeeId, @Valid @RequestBody EnrollRequest request) {
		return service.startEnroll(employeeId, request);
	}

}
