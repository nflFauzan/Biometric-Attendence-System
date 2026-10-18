package com.biometricattendance.devicecommand;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Pemeriksaan batas waktu perintah secara berkala (K-094). */
@Component
class DeviceCommandSweeper {

	private final DeviceCommandService service;

	DeviceCommandSweeper(DeviceCommandService service) {
		this.service = service;
	}

	@Scheduled(fixedDelayString = "${app.device.command-sweep:5s}")
	void sweep() {
		service.expireStale(Instant.now());
	}

}
