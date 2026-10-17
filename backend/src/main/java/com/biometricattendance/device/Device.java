package com.biometricattendance.device;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
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
 * Mesin absensi. Mesin ZK-Push dikenali dari {@link #serialNumber} tanpa secret (K-055); alat rakitan dikenali
 * dari kuncinya ({@link #keyHash}, K-088), dan {@link #serialNumber}-nya berisi ID {@code RK-…} buatan server.
 */
@Entity
@Table(name = "devices")
// UPDATE hanya menulis kolom yang berubah: heartbeat yang berjalan bersamaan dengan "Buat kode baru" tidak menulis
// ulang key_hash lama (K-088).
@DynamicUpdate
public class Device {

	public static final String PROTOCOL_ZK_PUSH = "ZK_PUSH";
	public static final String PROTOCOL_RAKITAN = "RAKITAN_V1";

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
	private String protocol = PROTOCOL_ZK_PUSH;

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

	/** SHA-256 kunci alat rakitan (K-088); kosong untuk ZK-Push. */
	private String keyHash;

	/** Kontak pertama yang berhasil dengan kunci saat ini; kosong = kunci baru belum dipakai. */
	private Instant keyFirstUsedAt;

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

	/** Hanya dipakai saat membuat mesin; jenis mesin tidak bisa diubah (K-099). */
	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public boolean isRakitan() {
		return PROTOCOL_RAKITAN.equals(protocol);
	}

	public String getKeyHash() {
		return keyHash;
	}

	public Instant getKeyFirstUsedAt() {
		return keyFirstUsedAt;
	}

	/** Mengganti kunci; kunci lama langsung tidak berlaku dan konfirmasi "terhubung" muncul lagi. */
	public void issueKey(String keyHash) {
		this.keyHash = keyHash;
		this.keyFirstUsedAt = null;
	}

	public void markKeyFirstUsed(Instant at) {
		this.keyFirstUsedAt = at;
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
