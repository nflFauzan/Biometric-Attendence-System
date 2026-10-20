package com.biometricattendance.devicesettings;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.device.DeviceDtos.ConfirmRequest;
import com.biometricattendance.devicecommand.DeviceCommandDtos.CommandResponse;
import com.biometricattendance.devicesettings.NetworkDtos.NetworkRequest;
import com.biometricattendance.devicesettings.NetworkDtos.NetworkView;

import jakarta.validation.Valid;

/** Jaringan dan ganti kunci alat rakitan, hanya Owner (spec §18.8; hak akses di SecurityConfig). */
@RestController
public class NetworkController {

	private final NetworkService service;
	private final KeyRotationService keyRotation;

	public NetworkController(NetworkService service, KeyRotationService keyRotation) {
		this.service = service;
		this.keyRotation = keyRotation;
	}

	/** "Ganti kunci" (spec §18.6), berbeda dari "Buat kode baru" yang langsung mengganti kunci. */
	@PostMapping("/api/devices/{id}/rotate-key")
	@ResponseStatus(HttpStatus.CREATED)
	CommandResponse rotateKey(@PathVariable Long id, @Valid @RequestBody ConfirmRequest request) {
		return keyRotation.request(id, request.confirmPassword());
	}

	@GetMapping("/api/devices/{id}/network")
	NetworkView view(@PathVariable Long id) {
		return service.view(id);
	}

	@PostMapping("/api/devices/{id}/network")
	@ResponseStatus(HttpStatus.CREATED)
	CommandResponse request(@PathVariable Long id, @Valid @RequestBody NetworkRequest request) {
		return service.request(id, request);
	}

}
