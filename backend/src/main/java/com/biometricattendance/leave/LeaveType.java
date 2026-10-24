package com.biometricattendance.leave;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Jenis izin/cuti yang diatur Owner (K-070). Tidak dihapus, hanya dinonaktifkan, karena dipakai pengajuan lama.
 * {@code paid} null berarti belum ditentukan; penerapannya ke gaji baru di Fase 5 (Q-013).
 */
@Entity
@Table(name = "leave_types")
public class LeaveType {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private boolean requiresDocument;

	private Boolean paid;

	private String description;

	@Column(nullable = false)
	private boolean requiresOwnerApproval;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LetterTemplate letterTemplate = LetterTemplate.UMUM;

	@Column(nullable = false)
	private boolean active = true;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isRequiresDocument() {
		return requiresDocument;
	}

	public void setRequiresDocument(boolean requiresDocument) {
		this.requiresDocument = requiresDocument;
	}

	public Boolean getPaid() {
		return paid;
	}

	public void setPaid(Boolean paid) {
		this.paid = paid;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean isRequiresOwnerApproval() {
		return requiresOwnerApproval;
	}

	public void setRequiresOwnerApproval(boolean requiresOwnerApproval) {
		this.requiresOwnerApproval = requiresOwnerApproval;
	}

	public LetterTemplate getLetterTemplate() {
		return letterTemplate;
	}

	public void setLetterTemplate(LetterTemplate letterTemplate) {
		this.letterTemplate = letterTemplate;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

}
