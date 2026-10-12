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

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "leave_type_id")
	private LeaveType leaveType;

	@Column(nullable = false)
	private LocalDate startDate;

	@Column(nullable = false)
	private LocalDate endDate;

	@Column(nullable = false)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LeaveStatus status;

	@Column(nullable = false, updatable = false)
	private String submittedBy;

	@Column(nullable = false, updatable = false)
	private Instant submittedAt;

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
			String submittedBy, Instant submittedAt) {
		this.employee = employee;
		this.leaveType = leaveType;
		this.startDate = startDate;
		this.endDate = endDate;
		this.reason = reason;
		this.submittedBy = submittedBy;
		this.submittedAt = submittedAt;
		this.status = LeaveStatus.MENUNGGU;
	}

	public void approve(String by, Instant at, String note) {
		this.status = LeaveStatus.DISETUJUI;
		this.decidedBy = by;
		this.decidedAt = at;
		this.decisionNote = note;
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

	/** Jumlah hari kalender termasuk tanggal awal dan akhir. */
	public long calendarDays() {
		return ChronoUnit.DAYS.between(startDate, endDate) + 1;
	}

	public boolean covers(LocalDate date) {
		return !date.isBefore(startDate) && !date.isAfter(endDate);
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
