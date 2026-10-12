package com.biometricattendance.device;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Nomor seri yang mencoba terhubung tetapi belum terdaftar (K-067), agar mudah didaftarkan tanpa menebak SN.
 * Hanya disimpan di memori: tidak ada data scan di sini dan hilang saat backend restart.
 */
@Component
public class UnregisteredDeviceTracker {

	static final int MAX_ENTRIES = 50;

	public record Attempt(String serialNumber, String ip, Instant lastAttemptAt, long attempts) {
	}

	private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

	public void record(String serialNumber, String ip, Instant at) {
		String key = serialNumber.trim().toUpperCase();
		attempts.merge(key, new Attempt(serialNumber.trim(), ip, at, 1),
				(old, fresh) -> new Attempt(old.serialNumber(), ip, at, old.attempts() + 1));
		if (attempts.size() > MAX_ENTRIES) {
			attempts.values().stream().min(Comparator.comparing(Attempt::lastAttemptAt))
				.ifPresent(oldest -> attempts.remove(oldest.serialNumber().toUpperCase()));
		}
	}

	public void forget(String serialNumber) {
		attempts.remove(serialNumber.trim().toUpperCase());
	}

	public List<Attempt> list() {
		return attempts.values().stream().sorted(Comparator.comparing(Attempt::lastAttemptAt).reversed()).toList();
	}

}
