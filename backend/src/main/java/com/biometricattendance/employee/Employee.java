package com.biometricattendance.employee;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import com.biometricattendance.branch.Branch;
import com.biometricattendance.category.EmployeeCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Data karyawan. Tidak pernah dihapus permanen: isi endDate dan nonaktifkan. UPDATE hanya menulis kolom yang berubah,
 * agar persetujuan sidik jari dan edit HR yang bersamaan tidak saling menimpa.
 */
@Entity
@DynamicUpdate
@Table(name = "employees")
public class Employee {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String employeeNumber;

	@Column(nullable = false)
	private String fullName;

	private String phone;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "branch_id", nullable = false)
	private Branch branch;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private EmployeeCategory category;

	/** Cabang absen tambahan (K-103); penempatan ada di {@link #branch}. */
	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "employee_extra_branches", joinColumns = @JoinColumn(name = "employee_id"),
			inverseJoinColumns = @JoinColumn(name = "branch_id"))
	private Set<Branch> extraBranches = new HashSet<>();

	private String position;

	@Column(nullable = false)
	private LocalDate joinDate;

	private LocalDate endDate;

	@Column(nullable = false)
	private boolean active = true;

	/** PIN/User ID karyawan di mesin absensi, sama di semua mesin (K-056). */
	private String devicePin;

	/** Persetujuan penyimpanan sidik jari (K-091): sekali per karyawan, siapa dan kapan. */
	private Instant fingerprintConsentAt;

	private String fingerprintConsentBy;

	/** Kapan sidik jari karyawan keluar/nonaktif dihapus (spec §17.4); null = belum atau aktif kembali. */
	private Instant fingerprintsRemovedAt;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	public Long getId() {
		return id;
	}

	public String getEmployeeNumber() {
		return employeeNumber;
	}

	public void setEmployeeNumber(String employeeNumber) {
		this.employeeNumber = employeeNumber;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public Branch getBranch() {
		return branch;
	}

	public void setBranch(Branch branch) {
		this.branch = branch;
	}

	public Set<Branch> getExtraBranches() {
		return extraBranches;
	}

	/** Boleh absen di cabang ini: penempatan atau cabang tambahan. */
	public boolean isAllowedAt(Long branchId) {
		return allowedBranchIds().contains(branchId);
	}

	public Set<Long> allowedBranchIds() {
		Set<Long> ids = extraBranches.stream().map(Branch::getId).collect(Collectors.toCollection(HashSet::new));
		ids.add(branch.getId());
		return ids;
	}

	public EmployeeCategory getCategory() {
		return category;
	}

	public void setCategory(EmployeeCategory category) {
		this.category = category;
	}

	public String getPosition() {
		return position;
	}

	public void setPosition(String position) {
		this.position = position;
	}

	public LocalDate getJoinDate() {
		return joinDate;
	}

	public void setJoinDate(LocalDate joinDate) {
		this.joinDate = joinDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public void setEndDate(LocalDate endDate) {
		this.endDate = endDate;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getDevicePin() {
		return devicePin;
	}

	public void setDevicePin(String devicePin) {
		this.devicePin = devicePin;
	}

	public Instant getFingerprintConsentAt() {
		return fingerprintConsentAt;
	}

	public String getFingerprintConsentBy() {
		return fingerprintConsentBy;
	}

	public Instant getFingerprintsRemovedAt() {
		return fingerprintsRemovedAt;
	}

	public void markFingerprintsRemoved(Instant at) {
		this.fingerprintsRemovedAt = at;
	}

	public void clearFingerprintsRemoved() {
		this.fingerprintsRemovedAt = null;
	}

	public void recordFingerprintConsent(String by, Instant at) {
		this.fingerprintConsentBy = by;
		this.fingerprintConsentAt = at;
	}

}
