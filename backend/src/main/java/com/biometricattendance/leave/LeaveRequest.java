package com.biometricattendance.leave;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.biometricattendance.employee.Employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Pengajuan izin/cuti hari penuh untuk satu rentang tanggal (K-071). Perubahan status hanya lewat method di bawah
 * agar transisi yang tidak sah tidak mungkin terjadi (K-074). {@code @Version} mencegah dua penyetuju memutus
 * pengajuan yang sama secara bersamaan.
 */
@Entity
@Table(name = "leave_requests")
public class LeaveRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "employee_id")
	private Employee employee;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "leave_type_id")
	private LeaveType leaveType;

	private LocalDate startDate;

	private LocalDate endDate;

	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LeaveStatus status;

	private String submittedBy;

	private Instant submittedAt;

	private Boolean requiresOwnerApproval;

	@Enumerated(EnumType.STRING)
	private LetterTemplate letterTemplate;

	private String contactPhone;

	private String leaveAddress;

	private String delegateTo;

	private String healthFacility;

	/** Pengajuan yang ditolak yang menjadi asal draft ini (K-126); null bila bukan pengajuan ulang. */
	private Long resubmittedFromId;

	@Column(unique = true)
	private String verificationToken;

	/** Salinan beku spesimen pemohon saat mengirim sendiri; null bila dikirim orang lain atau tanpa spesimen. */
	private String applicantSignatureKey;

	@Column(nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	private String decidedBy;

	private Instant decidedAt;

	private String decisionNote;

	private String cancelledBy;

	private Instant cancelledAt;

	private String cancelReason;

	@Version
	private long version;

	protected LeaveRequest() {
	}

	public LeaveRequest(Employee employee, LeaveType leaveType, LocalDate startDate, LocalDate endDate, String reason,
			String submittedBy, Instant submittedAt, LeaveStatus initial) {
		this.employee = employee;
		this.leaveType = leaveType;
		this.startDate = startDate;
		this.endDate = endDate;
		this.reason = reason;
		this.submittedBy = submittedBy;
		this.submittedAt = submittedAt;
		this.status = initial;
	}

	public void approve(String by, Instant at, String note) {
		this.status = LeaveStatus.DISETUJUI;
		this.decidedBy = by;
		this.decidedAt = at;
		this.decisionNote = note;
	}

	/** Tahap HR selesai pada jenis persetujuan ganda; keputusan akhir (decided_*) baru diisi oleh tahap Owner. */
	public void passHrStage() {
		this.status = LeaveStatus.PERLU_TINJAU_OWNER;
	}

	public void reject(String by, Instant at, String note) {
		this.status = LeaveStatus.DITOLAK;
		this.decidedBy = by;
		this.decidedAt = at;
		this.decisionNote = note;
	}

	public void cancel(String by, Instant at, String reason) {
		this.status = LeaveStatus.DIBATALKAN;
		this.cancelledBy = by;
		this.cancelledAt = at;
		this.cancelReason = reason;
	}

	/** Mengisi ulang isian draft; draft boleh belum lengkap sehingga semua nilai boleh null. */
	public void updateDraft(LeaveType leaveType, LocalDate startDate, LocalDate endDate, String reason) {
		this.leaveType = leaveType;
		this.startDate = startDate;
		this.endDate = endDate;
		this.reason = reason;
	}

	/** Draft menjadi pengajuan yang menunggu HR; jenis dibekukan ke pengajuan saat ini. */
	public void submit(String by, Instant at, String token) {
		this.status = LeaveStatus.PERLU_TINJAU_HR;
		this.submittedBy = by;
		this.submittedAt = at;
		this.verificationToken = token;
		this.requiresOwnerApproval = leaveType.isRequiresOwnerApproval();
		this.letterTemplate = leaveType.getLetterTemplate();
	}

	public String getApplicantSignatureKey() {
		return applicantSignatureKey;
	}

	public void setApplicantSignatureKey(String applicantSignatureKey) {
		this.applicantSignatureKey = applicantSignatureKey;
	}

	/** Jumlah hari kalender termasuk tanggal awal dan akhir; 0 bila tanggal draft belum lengkap. */
	public long calendarDays() {
		if (startDate == null || endDate == null) {
			return 0;
		}
		return ChronoUnit.DAYS.between(startDate, endDate) + 1;
	}

	public boolean covers(LocalDate date) {
		return startDate != null && endDate != null && !date.isBefore(startDate) && !date.isAfter(endDate);
	}

	public void setRequiresOwnerApproval(Boolean v) {
		this.requiresOwnerApproval = v;
	}

	public void setLetterTemplate(LetterTemplate v) {
		this.letterTemplate = v;
	}

	public void setContactPhone(String v) {
		this.contactPhone = v;
	}

	public void setLeaveAddress(String v) {
		this.leaveAddress = v;
	}

	public void setDelegateTo(String v) {
		this.delegateTo = v;
	}

	public void setHealthFacility(String v) {
		this.healthFacility = v;
	}

	public void setResubmittedFromId(Long v) {
		this.resubmittedFromId = v;
	}

	public void setVerificationToken(String v) {
		this.verificationToken = v;
	}

	public Boolean getRequiresOwnerApproval() {
		return requiresOwnerApproval;
	}

	public LetterTemplate getLetterTemplate() {
		return letterTemplate;
	}

	public String getContactPhone() {
		return contactPhone;
	}

	public String getLeaveAddress() {
		return leaveAddress;
	}

	public String getDelegateTo() {
		return delegateTo;
	}

	public String getHealthFacility() {
		return healthFacility;
	}

	public Long getResubmittedFromId() {
		return resubmittedFromId;
	}

	public String getVerificationToken() {
		return verificationToken;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Long getId() {
		return id;
	}

	public Employee getEmployee() {
		return employee;
	}

	public LeaveType getLeaveType() {
		return leaveType;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public String getReason() {
		return reason;
	}

	public LeaveStatus getStatus() {
		return status;
	}

	public String getSubmittedBy() {
		return submittedBy;
	}

	public Instant getSubmittedAt() {
		return submittedAt;
	}

	public String getDecidedBy() {
		return decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public String getDecisionNote() {
		return decisionNote;
	}

	public String getCancelledBy() {
		return cancelledBy;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}

	public String getCancelReason() {
		return cancelReason;
	}

	public long getVersion() {
		return version;
	}

}
