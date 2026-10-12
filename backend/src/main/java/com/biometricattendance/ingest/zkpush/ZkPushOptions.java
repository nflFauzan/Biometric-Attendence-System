package com.biometricattendance.ingest.zkpush;

import java.time.Instant;
import java.time.ZoneId;

import com.biometricattendance.device.Device;

/**
 * Balasan handshake protokol push ZKTeco. Nilai interval adalah parameter protokol yang sama dengan uji
 * tangkapan X401 2026-10-12, bukan aturan bisnis. {@code TransFlag} hanya meminta data absensi: mesin tidak
 * diminta mengirim data user, foto, atau template sidik jari (K-063).
 */
final class ZkPushOptions {

	private ZkPushOptions() {
	}

	static String handshake(Device device, Instant now) {
		ZoneId zone = ZoneId.of(device.getBranch().getTimezone());
		int offsetHours = zone.getRules().getOffset(now).getTotalSeconds() / 3600;
		String stamp = device.getLastAttlogStamp() == null ? "None" : device.getLastAttlogStamp();
		return String.join("\r\n",
				"GET OPTION FROM: " + device.getSerialNumber(),
				"ATTLOGStamp=" + stamp,
				"OPERLOGStamp=9999",
				"ATTPHOTOStamp=None",
				"ErrorDelay=30",
				"Delay=10",
				"TransTimes=00:00;14:05",
				"TransInterval=1",
				"TransFlag=TransData AttLog",
				"TimeZone=" + offsetHours,
				"Realtime=1",
				"Encrypt=None",
				"");
	}

}
