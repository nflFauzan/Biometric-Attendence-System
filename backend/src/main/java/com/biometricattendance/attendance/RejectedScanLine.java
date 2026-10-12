package com.biometricattendance.attendance;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Baris kiriman mesin yang tidak bisa dibaca, disimpan apa adanya (K-062). */
@Entity
@Table(name = "rejected_scan_lines")
public class RejectedScanLine {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long deviceId;

	@Column(nullable = false)
	private String rawLine;

	@Column(nullable = false)
	private String reason;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant receivedAt;

	protected RejectedScanLine() {
	}

	public RejectedScanLine(Long deviceId, String rawLine, String reason) {
		this.deviceId = deviceId;
		this.rawLine = rawLine;
		this.reason = reason;
	}

	public Long getId() {
		return id;
	}

	public Long getDeviceId() {
		return deviceId;
	}

	public String getRawLine() {
		return rawLine;
	}

	public String getReason() {
		return reason;
	}

	public Instant getReceivedAt() {
		return receivedAt;
	}

}
