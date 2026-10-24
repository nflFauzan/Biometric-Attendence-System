package com.biometricattendance.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/** Pembatas laju jendela tetap per menit. */
class RateLimiterTests {

	@Test
	void limitsPerKeyAndResetsInNextMinute() {
		AtomicLong now = new AtomicLong(120_000);
		RateLimiter limiter = new RateLimiter(2, now::get);

		assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
		assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
		assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
		assertThat(limiter.tryAcquire("2.2.2.2")).isTrue();

		now.addAndGet(60_000);
		assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
	}

}
