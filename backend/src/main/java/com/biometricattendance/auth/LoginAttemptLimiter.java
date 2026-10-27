package com.biometricattendance.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.audit.AuditService;
import com.biometricattendance.common.ApiException;

/**
 * K-138 (NFR-SEC-004): setelah sejumlah gagal berturut-turut, login satu username ditolak selama beberapa menit,
 * termasuk bila passwordnya benar. Username yang tidak ada ikut dihitung agar tidak bisa dipakai menebak username.
 * Disimpan di memori satu proses seperti {@code RateLimiter} (K-116), jadi kunci hilang saat server di-restart.
 */
@Component
public class LoginAttemptLimiter {

	private static final int PRUNE_THRESHOLD = 10_000;

	/** Panjang kolom audit_logs.actor_username. */
	private static final int ACTOR_MAX_LENGTH = 50;

	/** {@code lastFailure} membuat gagal lama kedaluwarsa, sehingga hanya gagal dalam satu masa kunci yang dihitung. */
	private record State(int failures, Instant lastFailure, Instant lockedUntil) {
	}

	private final Clock clock;
	private final int maxFailures;
	private final Duration lockDuration;
	private final AuditService audit;
	private final Map<String, State> states = new ConcurrentHashMap<>();

	@Autowired
	public LoginAttemptLimiter(@Value("${app.login.max-failures}") int maxFailures,
			@Value("${app.login.lock-minutes}") int lockMinutes, AuditService audit) {
		this(Clock.systemUTC(), maxFailures, lockMinutes, audit);
	}

	LoginAttemptLimiter(Clock clock, int maxFailures, int lockMinutes, AuditService audit) {
		this.clock = clock;
		this.maxFailures = maxFailures;
		this.lockDuration = Duration.ofMinutes(lockMinutes);
		this.audit = audit;
	}

	/** Melempar 429 bila username sedang terkunci. */
	public void checkAllowed(String username) {
		Instant now = clock.instant();
		State state = states.computeIfPresent(normalize(username), (k, s) -> isExpired(s, now) ? null : s);
		if (state != null && state.lockedUntil() != null) {
			long minutes = (Duration.between(now, state.lockedUntil()).toSeconds() + 59) / 60;
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
					"Terlalu banyak percobaan masuk. Coba lagi dalam " + minutes + " menit.");
		}
	}

	/** Audit memerlukan transaksi (Propagation.MANDATORY), sedangkan login sendiri tidak membukanya. */
	@Transactional
	public void recordFailure(String username) {
		Instant now = clock.instant();
		String key = normalize(username);
		if (states.size() > PRUNE_THRESHOLD) {
			states.values().removeIf(s -> isExpired(s, now));
		}
		boolean[] lockedNow = new boolean[1];
		State state = states.compute(key, (k, s) -> {
			State current = s == null || isExpired(s, now) ? new State(0, now, null) : s;
			if (current.lockedUntil() != null) {
				return current;
			}
			int failures = current.failures() + 1;
			lockedNow[0] = failures >= maxFailures;
			return new State(failures, now, lockedNow[0] ? now.plus(lockDuration) : null);
		});
		if (lockedNow[0]) {
			String actor = key.length() > ACTOR_MAX_LENGTH ? key.substring(0, ACTOR_MAX_LENGTH) : key;
			audit.record(actor, "LOGIN_LOCKED", "AUTH", null, null,
					Map.of("failures", state.failures(), "lockedUntil", state.lockedUntil().toString()), null);
		}
	}

	public void recordSuccess(String username) {
		states.remove(normalize(username));
	}

	private boolean isExpired(State state, Instant now) {
		Instant until = state.lockedUntil() != null ? state.lockedUntil() : state.lastFailure().plus(lockDuration);
		return !now.isBefore(until);
	}

	private static String normalize(String username) {
		return username.trim().toLowerCase(Locale.ROOT);
	}

}
