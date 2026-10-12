package com.biometricattendance.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Koreksi jam masuk dan/atau pulang untuk satu karyawan pada satu tanggal (K-059). Tidak pernah diubah:
 * koreksi baru menandai yang lama {@code supersededAt} sehingga riwayatnya tetap ada (K-066).
 */
@Entity
@Table(name = "attendance_corrections")
public class AttendanceCorrection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long employeeId;

	@Column(nullable = false)
	private LocalDate workDate;

	private LocalTime checkIn;

	private LocalTime checkOut;

	@Column(nullable = false)
	private String reason;

	@Column(nullable = false)
	private String createdBy;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant supersededAt;

	protected AttendanceCorrection() {
	}

	public AttendanceCorrection(Long employeeId, LocalDate workDate, LocalTime checkIn, LocalTime checkOut,
			String reason, String createdBy) {
		this.employeeId = employeeId;
		this.workDate = workDate;
		this.checkIn = checkIn;
		this.checkOut = checkOut;
		this.reason = reason;
		this.createdBy = createdBy;
	}

	public Long getId() {
		return id;
	}

	public Long getEmployeeId() {
		return employeeId;
	}

	public LocalDate getWorkDate() {
		return workDate;
	}

	public LocalTime getCheckIn() {
		return checkIn;
	}

	public LocalTime getCheckOut() {
		return checkOut;
	}

	public String getReason() {
		return reason;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getSupersededAt() {
		return supersededAt;
	}

	public void supersede(Instant at) {
		this.supersededAt = at;
	}

}
