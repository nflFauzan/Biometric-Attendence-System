package com.biometricattendance.devicesettings;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.devicesettings.DeviceSettingsDtos.SettingsResponse;
import com.biometricattendance.devicesettings.DeviceSettingsDtos.SettingsValues;

import jakarta.validation.Valid;

/** Pengaturan ringan alat rakitan untuk Owner dan HR/Finance (K-098, spec §18.8). */
@RestController
public class DeviceSettingsController {

	private final DeviceSettingsService service;

	public DeviceSettingsController(DeviceSettingsService service) {
		this.service = service;
	}

	@GetMapping("/api/devices/{id}/settings")
	SettingsResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	@PutMapping("/api/devices/{id}/settings")
	SettingsResponse update(@PathVariable Long id, @Valid @RequestBody SettingsValues values) {
		return service.update(id, values);
	}

	/** "Pengaturan semua mesin": semua alat rakitan aktif. */
	@PutMapping("/api/device-settings")
	List<SettingsResponse> updateAll(@Valid @RequestBody SettingsValues values) {
		return service.updateAll(values);
	}

}
