package com.biometricattendance.leave;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class LeaveDtos {

	private LeaveDtos() {
	}

	public record LeaveTypeRequest(@NotBlank(message = "Nama wajib diisi") @Size(max = 100) String name,
			boolean requiresDocument, Boolean paid, @Size(max = 500) String description, boolean active) {
	}

	/** @param paid null = belum ditentukan (dipakai di Fase 5) */
	public record LeaveTypeResponse(Long id, String name, boolean requiresDocument, Boolean paid, String description,
			boolean active) {

		public static LeaveTypeResponse from(LeaveType type) {
			return new LeaveTypeResponse(type.getId(), type.getName(), type.isRequiresDocument(), type.getPaid(),
					type.getDescription(), type.isActive());
		}

	}

	/**
	 * Pengajuan baru. {@code employeeId} wajib bila Owner/HR mencatat atas nama karyawan (K-073) dan diabaikan
	 * pada pengajuan sendiri lewat {@code /api/me}.
	 */
	public record SubmitLeaveRequest(Long employeeId, @NotNull(message = "Jenis izin wajib dipilih") Long leaveTypeId,
			@NotNull(message = "Tanggal mulai wajib diisi") LocalDate startDate,
			@NotNull(message = "Tanggal selesai wajib diisi") LocalDate endDate,
			@NotBlank(message = "Alasan wajib diisi") @Size(max = 1000, message = "Alasan maksimal 1000 karakter") String reason) {
	}

	/** Catatan keputusan; wajib untuk penolakan dan pembatalan izin yang sudah disetujui (K-074). */
	public record DecisionRequest(@Size(max = 1000, message = "Catatan maksimal 1000 karakter") String note) {
	}

	public record AttachmentResponse(Long id, String originalName, String contentType, long sizeBytes,
			String uploadedBy, Instant uploadedAt) {

		static AttachmentResponse from(LeaveAttachment a) {
			return new AttachmentResponse(a.getId(), a.getOriginalName(), a.getContentType(), a.getSizeBytes(),
					a.getUploadedBy(), a.getUploadedAt());
		}

	}

	/**
	 * @param calendarDays jumlah hari kalender termasuk tanggal awal dan akhir
	 * @param workingDays hari kerja di dalam rentang menurut jadwal dan kalender libur; null bila tidak dihitung
	 *        (daftar) atau aturan hari kerja belum diatur untuk sebagian tanggal
	 */
	public record LeaveRequestResponse(Long id, Long employeeId, String employeeNumber, String employeeName,
			String position, Long branchId, String branchName, Long leaveTypeId, String leaveTypeName,
			boolean requiresDocument, LocalDate startDate, LocalDate endDate, long calendarDays, Integer workingDays,
			String reason, LeaveStatus status, String submittedBy, Instant submittedAt, String decidedBy,
			Instant decidedAt, String decisionNote, String cancelledBy, Instant cancelledAt, String cancelReason,
			List<AttachmentResponse> attachments) {

		static LeaveRequestResponse from(LeaveRequest r, Integer workingDays, List<AttachmentResponse> attachments) {
			var e = r.getEmployee();
			var t = r.getLeaveType();
			return new LeaveRequestResponse(r.getId(), e.getId(), e.getEmployeeNumber(), e.getFullName(),
					e.getPosition(), e.getBranch().getId(), e.getBranch().getName(), t.getId(), t.getName(),
					t.isRequiresDocument(), r.getStartDate(), r.getEndDate(), r.calendarDays(), workingDays,
					r.getReason(), r.getStatus(), r.getSubmittedBy(), r.getSubmittedAt(), r.getDecidedBy(),
					r.getDecidedAt(), r.getDecisionNote(), r.getCancelledBy(), r.getCancelledAt(), r.getCancelReason(),
					attachments);
		}

	}

	/** Pemakaian satu jenis pada satu tahun dari izin yang disetujui (K-072); rentang dipotong ke tahun tersebut. */
	public record UsageRow(Long leaveTypeId, String leaveTypeName, int requests, long calendarDays,
			Integer workingDays) {
	}

	public record UsageResponse(Long employeeId, int year, List<UsageRow> types, int pendingRequests) {
	}

	/** Ringkasan untuk lonceng notifikasi Owner/HR (K-081). */
	public record PendingItem(Long id, Long employeeId, String employeeName, String branchName, String leaveTypeName,
			LocalDate startDate, LocalDate endDate, Instant submittedAt) {

		static PendingItem from(LeaveRequest r) {
			return new PendingItem(r.getId(), r.getEmployee().getId(), r.getEmployee().getFullName(),
					r.getEmployee().getBranch().getName(), r.getLeaveType().getName(), r.getStartDate(),
					r.getEndDate(), r.getSubmittedAt());
		}

	}

	public record PendingSummary(long count, List<PendingItem> items) {
	}

}
