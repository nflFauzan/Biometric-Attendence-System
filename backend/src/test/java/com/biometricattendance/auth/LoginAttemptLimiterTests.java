package com.biometricattendance.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.biometricattendance.audit.AuditService;
import com.biometricattendance.common.ApiException;

/** K-138: batas percobaan login per username, dengan jam yang bisa dimajukan. */
class LoginAttemptLimiterTests {

	private static final Instant START = Instant.parse("2026-10-27T03:00:00Z");

	private final MutableClock clock = new MutableClock(START);
	private final AuditService audit = mock(AuditService.class);
	private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock, 5, 15, audit);

	@Test
	void locksAfterMaxFailures() {
		fail("budi", 5);

		ApiException error = catchThrowableOfType(ApiException.class, () -> limiter.checkAllowed("budi"));
		assertThat(error.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(error.getMessage()).isEqualTo("Terlalu banyak percobaan masuk. Coba lagi dalam 15 menit.");
		verify(audit, times(1)).record(eq("budi"), eq("LOGIN_LOCKED"), eq("AUTH"), any(), any(), any(), any());
	}

	@Test
	void fourFailuresDoNotLock() {
		fail("budi", 4);

		assertThatCode(() -> limiter.checkAllowed("budi")).doesNotThrowAnyException();
	}

	@Test
	void remainingMinutesAreRoundedUp() {
		fail("budi", 5);
		clock.advance(Duration.ofMinutes(10).plusSeconds(1));

		ApiException error = catchThrowableOfType(ApiException.class, () -> limiter.checkAllowed("budi"));
		assertThat(error.getMessage()).isEqualTo("Terlalu banyak percobaan masuk. Coba lagi dalam 5 menit.");
	}

	@Test
	void unlocksAfterLockMinutes() {
		fail("budi", 5);
		clock.advance(Duration.ofMinutes(15).plusSeconds(1));

		assertThatCode(() -> limiter.checkAllowed("budi")).doesNotThrowAnyException();
		// Hitungan mulai dari nol lagi: satu gagal setelah kunci habis belum mengunci.
		fail("budi", 1);
		assertThatCode(() -> limiter.checkAllowed("budi")).doesNotThrowAnyException();
	}

	/** Gagal yang sudah lebih lama dari masa kunci tidak ikut dihitung (review I-2). */
	@Test
	void oldFailuresExpire() {
		fail("budi", 4);
		clock.advance(Duration.ofMinutes(15).plusSeconds(1));
		fail("budi", 1);

		assertThatCode(() -> limiter.checkAllowed("budi")).doesNotThrowAnyException();
	}

	@Test
	void successResetsFailures() {
		fail("budi", 4);
		limiter.recordSuccess("budi");
		fail("budi", 4);

		assertThatCode(() -> limiter.checkAllowed("budi")).doesNotThrowAnyException();
	}

	@Test
	void usernamesAreNormalised() {
		fail("Budi ", 3);
		fail("budi", 2);

		assertThat(catchThrowableOfType(ApiException.class, () -> limiter.checkAllowed(" BUDI"))).isNotNull();
	}

	@Test
	void otherUsernamesAreNotAffected() {
		fail("budi", 5);

		assertThatCode(() -> limiter.checkAllowed("ani")).doesNotThrowAnyException();
	}

	private void fail(String username, int times) {
		for (int i = 0; i < times; i++) {
			limiter.recordFailure(username);
		}
	}

	private static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
