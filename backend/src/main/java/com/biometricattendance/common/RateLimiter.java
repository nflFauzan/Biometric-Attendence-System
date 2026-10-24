package com.biometricattendance.common;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Pembatas laju jendela tetap per menit, per kunci (mis. alamat IP). Disimpan di memori satu proses; cukup untuk
 * satu server dan hanya meredam penebakan token, bukan pengaman utama.
 */
public class RateLimiter {

	private static final int PRUNE_THRESHOLD = 10_000;

	private record Window(long minute, int count) {
	}

	private final int limitPerMinute;
	private final LongSupplier clockMillis;
	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	public RateLimiter(int limitPerMinute) {
		this(limitPerMinute, System::currentTimeMillis);
	}

	RateLimiter(int limitPerMinute, LongSupplier clockMillis) {
		this.limitPerMinute = limitPerMinute;
		this.clockMillis = clockMillis;
	}

	/** True bila permintaan ini masih dalam batas pada menit berjalan. */
	public boolean tryAcquire(String key) {
		long minute = clockMillis.getAsLong() / 60_000L;
		if (windows.size() > PRUNE_THRESHOLD) {
			windows.values().removeIf(w -> w.minute() < minute);
		}
		boolean[] allowed = new boolean[1];
		windows.compute(key, (k, w) -> {
			Window current = w == null || w.minute() != minute ? new Window(minute, 0) : w;
			if (current.count() >= limitPerMinute) {
				allowed[0] = false;
				return current;
			}
			allowed[0] = true;
			return new Window(minute, current.count() + 1);
		});
		return allowed[0];
	}

}
