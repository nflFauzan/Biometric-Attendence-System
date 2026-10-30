package com.biometricattendance.overtime;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Keputusan lembur satu karyawan pada satu tanggal (FR-REV-003, K-140). Satu baris per hari (unique
 * {@code employee_id, work_date}) dan boleh diganti selama penggajian belum ada. {@code candidateSeconds} adalah
 * kandidat saat divalidasi; bila rekap kemudian berubah, hari itu perlu divalidasi ulang. Status tidak disimpan.
 */
@Entity
@Table(name = "overtime_validations")
public class OvertimeValidation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private Long employeeId;

	@Column(nullable = false, updatable = false)
	private LocalDate workDate;

	@Column(nullable = false)
	private long candidateSeconds;

	@Column(nullable = false)
	private long approvedSeconds;

	private String note;

	@Column(nullable = false)
	private String validatedBy;

	@Column(nullable = false)
	private Instant validatedAt;

	@Version
	private Long version;

	protected OvertimeValidation() {
	}

	public OvertimeValidation(Long employeeId, LocalDate workDate) {
		this.employeeId = employeeId;
		this.workDate = workDate;
	}

	public void validate(long candidateSeconds, long approvedSeconds, String note, String validatedBy,
			Instant validatedAt) {
		this.candidateSeconds = candidateSeconds;
		this.approvedSeconds = approvedSeconds;
		this.note = note;
		this.validatedBy = validatedBy;
		this.validatedAt = validatedAt;
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

	public long getCandidateSeconds() {
		return candidateSeconds;
	}

	public long getApprovedSeconds() {
		return approvedSeconds;
	}

	public String getNote() {
		return note;
	}

	public String getValidatedBy() {
		return validatedBy;
	}

	public Instant getValidatedAt() {
		return validatedAt;
	}

}
