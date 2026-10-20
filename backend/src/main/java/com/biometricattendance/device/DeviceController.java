package com.biometricattendance.device;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.device.DeviceDtos.ConfirmRequest;
import com.biometricattendance.device.DeviceDtos.DeviceRequest;
import com.biometricattendance.device.DeviceDtos.DeviceResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

	private final DeviceService service;

	public DeviceController(DeviceService service) {
		this.service = service;
	}

	@GetMapping
	List<DeviceResponse> list() {
		return service.list();
	}

	/** K-067: nomor seri yang mencoba terhubung tetapi belum terdaftar. */
	@GetMapping("/unregistered-attempts")
	List<UnregisteredDeviceTracker.Attempt> unregisteredAttempts() {
		return service.unregisteredAttempts();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	DeviceResponse create(@Valid @RequestBody DeviceRequest request) {
		return service.create(request);
	}

	/** "Buat kode baru" untuk alat rakitan; hanya Owner (SecurityConfig). Kunci hanya ada di response ini. */
	@PostMapping("/{id}/key")
	DeviceResponse issueKey(@PathVariable Long id, @Valid @RequestBody ConfirmRequest request) {
		return service.issueKey(id, request.confirmPassword());
	}

	@PutMapping("/{id}")
	DeviceResponse update(@PathVariable Long id, @Valid @RequestBody DeviceRequest request) {
		return service.update(id, request);
	}

}
