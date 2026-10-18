package com.biometricattendance.fingerprint;

import java.time.Instant;
import java.util.UUID;

import com.biometricattendance.device.Device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Sidik jari karyawan yang berhasil didaftarkan di sebuah mesin. Template tidak disimpan di Rencana 3 (spec §16);
 * baris ini hanya mencatat jari mana, di mesin mana, kapan, dan dari perintah mana.
 */
@Entity
@Table(name = "employee_fingerprints")
public class EmployeeFingerprint {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long employeeId;

	@Column(nullable = false)
	private int finger;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "device_id", nullable = false)
	private Device device;

	@Column(nullable = false)
	private UUID commandId;

	@Column(nullable = false)
	private Instant enrolledAt;

	protected EmployeeFingerprint() {
	}

	public EmployeeFingerprint(Long employeeId, int finger, Device device, UUID commandId, Instant enrolledAt) {
		this.employeeId = employeeId;
		this.finger = finger;
		this.device = device;
		this.commandId = commandId;
		this.enrolledAt = enrolledAt;
	}

	/** Daftar ulang jari yang sama: catatan lama diganti. */
	public void replace(Device device, UUID commandId, Instant enrolledAt) {
		this.device = device;
		this.commandId = commandId;
		this.enrolledAt = enrolledAt;
	}

	public Long getId() {
		return id;
	}

	public Long getEmployeeId() {
		return employeeId;
	}

	public int getFinger() {
		return finger;
	}

	public Device getDevice() {
		return device;
	}

	public UUID getCommandId() {
		return commandId;
	}

	public Instant getEnrolledAt() {
		return enrolledAt;
	}

}
