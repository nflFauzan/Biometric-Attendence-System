package com.biometricattendance.leave;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Metadata surat pendukung (K-078). Isinya disimpan di disk dengan nama acak {@code storageKey}; nama asli hanya
 * untuk ditampilkan dan untuk nama file unduhan. Tidak pernah diubah atau dihapus.
 */
@Entity
@Table(name = "leave_attachments")
public class LeaveAttachment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private Long leaveRequestId;

	@Column(nullable = false, updatable = false)
	private String originalName;

	@Column(nullable = false, updatable = false)
	private String contentType;

	@Column(nullable = false, updatable = false)
	private long sizeBytes;

	@Column(nullable = false, updatable = false)
	private String storageKey;

	@Column(nullable = false, updatable = false)
	private String sha256;

	@Column(nullable = false, updatable = false)
	private String uploadedBy;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant uploadedAt;

	protected LeaveAttachment() {
	}

	public LeaveAttachment(Long leaveRequestId, String originalName, String contentType, long sizeBytes,
			String storageKey, String sha256, String uploadedBy) {
		this.leaveRequestId = leaveRequestId;
		this.originalName = originalName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.storageKey = storageKey;
		this.sha256 = sha256;
		this.uploadedBy = uploadedBy;
	}

	public Long getId() {
		return id;
	}

	public Long getLeaveRequestId() {
		return leaveRequestId;
	}

	public String getOriginalName() {
		return originalName;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public String getSha256() {
		return sha256;
	}

	public String getUploadedBy() {
		return uploadedBy;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}

}
