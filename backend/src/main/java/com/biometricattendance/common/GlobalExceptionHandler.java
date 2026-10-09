package com.biometricattendance.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Semua error API dikembalikan dalam format ProblemDetail (RFC 9457). */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ProblemDetail handleApi(ApiException ex) {
		return ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
			.forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Data tidak valid");
		problem.setProperty("errors", errors);
		return problem;
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail handleUnreadable() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Format data tidak valid");
	}

	@ExceptionHandler(BadCredentialsException.class)
	ProblemDetail handleBadCredentials() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Username atau password salah");
	}

	@ExceptionHandler(DisabledException.class)
	ProblemDetail handleDisabled() {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Akun dinonaktifkan");
	}

}
