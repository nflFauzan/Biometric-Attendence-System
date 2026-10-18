package com.biometricattendance.fingerprint;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Menangkap tanggal keluar yang tiba belakangan (spec §17.4); intervalnya parameter teknis. */
@Component
class DepartureSweeper {

	private final FingerprintRemoval removal;

	DepartureSweeper(FingerprintRemoval removal) {
		this.removal = removal;
	}

	@Scheduled(fixedDelayString = "${app.fingerprint.departure-check:1h}")
	void sweep() {
		removal.sweepDepartures(Instant.now());
	}

}
