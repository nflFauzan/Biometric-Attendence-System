package com.biometricattendance.attendance;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

import com.biometricattendance.device.Device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Scan asli dari mesin (K-019). Hanya dibaca lewat JPA; baris baru ditulis oleh
 * {@link AttendanceScanRepository#insertIgnoringDuplicate} agar scan ganda diabaikan database (K-061).
 */
@Entity
@Table(name = "attendance_scans")
public class AttendanceScan {

	@Id
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "device_id", nullable = false, insertable = false, updatable = false)
	private Device device;

	@Column(nullable = false, insertable = false, updatable = false)
	private String devicePin;

	@Column(insertable = false, updatable = false)
	private Long employeeId;

	@Column(nullable = false, insertable = false, updatable = false)
	private Instant scannedAt;

	@Column(nullable = false, insertable = false, updatable = false)
	private LocalDateTime deviceLocalTime;

	@Column(insertable = false, updatable = false)
	private String statusCode;

	@Column(insertable = false, updatable = false)
	private String verifyMode;

	@Column(insertable = false, updatable = false)
	private String flags;

	@Column(nullable = false, insertable = false, updatable = false)
	private String rawLine;

	@Column(nullable = false, insertable = false, updatable = false)
	private Instant receivedAt;

	protected AttendanceScan() {
	}

	public Long getId() {
		return id;
	}

	public Device getDevice() {
		return device;
	}

	public String getDevicePin() {
		return devicePin;
	}

	public Long getEmployeeId() {
		return employeeId;
	}

	public Instant getScannedAt() {
		return scannedAt;
	}

	public LocalDateTime getDeviceLocalTime() {
		return deviceLocalTime;
	}

	public String getStatusCode() {
		return statusCode;
	}

	public String getVerifyMode() {
		return verifyMode;
	}

	public Set<ScanFlag> getFlags() {
		return ScanFlag.parse(flags);
	}

	public String getRawLine() {
		return rawLine;
	}

	public Instant getReceivedAt() {
		return receivedAt;
	}

}
