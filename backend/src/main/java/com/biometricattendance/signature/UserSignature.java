package com.biometricattendance.signature;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Spesimen tanda tangan satu akun (PNG transparan di volume lampiran). */
@Entity
@Table(name = "user_signatures")
public class UserSignature {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private Long userAccountId;

	@Column(nullable = false)
	private String storageKey;

	@Column(name = "sha256", nullable = false)
	private String sha256;

	@Column(nullable = false)
	private Instant updatedAt;

	protected UserSignature() {
	}

	public UserSignature(Long userAccountId, String storageKey, String sha256, Instant updatedAt) {
		this.userAccountId = userAccountId;
		this.storageKey = storageKey;
		this.sha256 = sha256;
		this.updatedAt = updatedAt;
	}

	public void replace(String storageKey, String sha256, Instant updatedAt) {
		this.storageKey = storageKey;
		this.sha256 = sha256;
		this.updatedAt = updatedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getUserAccountId() {
		return userAccountId;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public String getSha256() {
		return sha256;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
