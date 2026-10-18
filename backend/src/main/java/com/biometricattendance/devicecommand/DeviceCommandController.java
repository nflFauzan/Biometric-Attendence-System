package com.biometricattendance.devicecommand;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.devicecommand.DeviceCommandDtos.CommandResponse;

/** Status dan pembatalan perintah mesin untuk Owner dan HR/Finance (spec §3.6). ID bukan UUID = tidak ditemukan. */
@RestController
@RequestMapping("/api/device-commands")
public class DeviceCommandController {

	private final DeviceCommandService service;

	public DeviceCommandController(DeviceCommandService service) {
		this.service = service;
	}

	@GetMapping("/{id}")
	CommandResponse get(@PathVariable String id) {
		return service.get(id);
	}

	@PostMapping("/{id}/cancel")
	CommandResponse cancel(@PathVariable String id) {
		return service.cancel(id);
	}

}
