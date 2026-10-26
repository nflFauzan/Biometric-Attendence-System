package com.biometricattendance.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

/** Error bisnis yang dikembalikan ke client dengan status HTTP tertentu. */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final Map<String, Object> properties = new LinkedHashMap<>();

	public ApiException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}

	/** Properti tambahan di ProblemDetail, mis. daftar baris bermasalah pada impor karyawan. */
	public ApiException withProperty(String name, Object value) {
		properties.put(name, value);
		return this;
	}

	public Map<String, Object> getProperties() {
		return properties;
	}

	public static ApiException notFound(String what) {
		return new ApiException(HttpStatus.NOT_FOUND, what + " tidak ditemukan");
	}

	public static ApiException conflict(String message) {
		return new ApiException(HttpStatus.CONFLICT, message);
	}

	public static ApiException badRequest(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, message);
	}

	public static ApiException forbidden(String message) {
		return new ApiException(HttpStatus.FORBIDDEN, message);
	}

}
