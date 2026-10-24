package com.biometricattendance.leave;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.RateLimiter;
import com.biometricattendance.leave.LeaveDtos.PublicLetterResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Verifikasi keaslian surat tanpa login, lewat token acak 128-bit pada QR. Datanya sengaja minimal dan jumlah
 * permintaan dibatasi per IP agar token tidak bisa ditebak. IP klien dari {@code getRemoteAddr()} (di belakang
 * proxy, {@code server.forward-headers-strategy=native} sudah memakai X-Forwarded-For).
 */
@RestController
@RequestMapping("/api/public/letters")
public class PublicLetterController {

	private final LeaveLetterService letters;
	private final RateLimiter limiter;

	public PublicLetterController(LeaveLetterService letters,
			@Value("${app.public-letters.rate-per-minute:30}") int ratePerMinute) {
		this.letters = letters;
		this.limiter = new RateLimiter(ratePerMinute);
	}

	@GetMapping("/{token}")
	ResponseEntity<PublicLetterResponse> verify(@PathVariable String token, HttpServletRequest request) {
		if (!limiter.tryAcquire(request.getRemoteAddr())) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Terlalu banyak permintaan, coba lagi sebentar");
		}
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(letters.verify(token));
	}

}
