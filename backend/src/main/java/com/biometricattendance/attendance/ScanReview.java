package com.biometricattendance.attendance;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Keputusan Owner/HR atas scan yang tertahan karena cabang (K-103). Scan aslinya tidak diubah; keputusan
 * disimpan terpisah dan bisa diganti. {@code @Version} (pembaruan) dan primary key (insert bersamaan) menjaga
 * agar dua peninjau tidak saling menimpa.
 */
@Entity
@Table(name = "scan_reviews")
public class ScanReview {

	public enum Decision {
		DITERIMA, DIABAIKAN
	}

	@Id
	private Long scanId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Decision decision;

	@Column(nullable = false)
	private String reason;

	@Column(nullable = false)
	private String decidedBy;

	@Column(nullable = false)
	private Instant decidedAt;

	/** Null pada entity baru agar Spring Data memperlakukannya sebagai insert. */
	@Version
	private Long version;

	protected ScanReview() {
	}

	public ScanReview(Long scanId, Decision decision, String reason, String decidedBy, Instant decidedAt) {
		this.scanId = scanId;
		this.decision = decision;
		this.reason = reason;
		this.decidedBy = decidedBy;
		this.decidedAt = decidedAt;
	}

	public void decide(Decision decision, String reason, String decidedBy, Instant decidedAt) {
		this.decision = decision;
		this.reason = reason;
		this.decidedBy = decidedBy;
		this.decidedAt = decidedAt;
	}

	public Long getScanId() {
		return scanId;
	}

	public Decision getDecision() {
		return decision;
	}

	public String getReason() {
		return reason;
	}

	public String getDecidedBy() {
		return decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

}
