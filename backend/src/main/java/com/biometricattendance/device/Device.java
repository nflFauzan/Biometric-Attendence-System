package com.biometricattendance.device;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.biometricattendance.branch.Branch;

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
 * Mesin absensi. Identitas mesin saat mengirim scan adalah {@link #serialNumber}; protokol mesin uji X401
 * tidak mengirim secret (K-055).
 */
@Entity
@Table(name = "devices")
public class Device {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String serialNumber;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@Column(nullable = false)
	private boolean active = true;

	@Column(nullable = false)
	private String protocol = "ZK_PUSH";

	/** Scan yang lebih lama dari ini tidak disimpan (K-057). */
	@Column(nullable = false)
	private Instant acceptScansFrom;

	@Column(nullable = false)
	private long ignoredScanCount;

	private Instant lastSeenAt;

	private String lastIp;

	private String firmware;

	private String lastInfo;

	private String lastAttlogStamp;

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

	public String getSerialNumber() {
		return serialNumber;
	}

	public void setSerialNumber(String serialNumber) {
		this.serialNumber = serialNumber;
	}

	public Branch getBranch() {
		return branch;
	}

	public void setBranch(Branch branch) {
		this.branch = branch;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getProtocol() {
		return protocol;
	}

	public Instant getAcceptScansFrom() {
		return acceptScansFrom;
	}

	public void setAcceptScansFrom(Instant acceptScansFrom) {
		this.acceptScansFrom = acceptScansFrom;
	}

	public long getIgnoredScanCount() {
		return ignoredScanCount;
	}

	public void addIgnoredScans(long count) {
		this.ignoredScanCount += count;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}

	public String getLastIp() {
		return lastIp;
	}

	public String getFirmware() {
		return firmware;
	}

	public String getLastInfo() {
		return lastInfo;
	}

	public String getLastAttlogStamp() {
		return lastAttlogStamp;
	}

	public void setLastAttlogStamp(String lastAttlogStamp) {
		this.lastAttlogStamp = lastAttlogStamp;
	}

	/** Mencatat kontak terakhir dari mesin; nilai null tidak menimpa nilai lama. */
	public void recordContact(Instant at, String ip, String firmware, String info) {
		this.lastSeenAt = at;
		if (ip != null) {
			this.lastIp = ip;
		}
		if (firmware != null) {
			this.firmware = firmware;
		}
		if (info != null) {
			this.lastInfo = info;
		}
	}

}
