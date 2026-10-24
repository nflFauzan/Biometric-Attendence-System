package com.biometricattendance.leave;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class LeaveDtos {

	private LeaveDtos() {
	}

	public record LeaveTypeRequest(@NotBlank(message = "Nama wajib diisi") @Size(max = 100) String name,
			boolean requiresDocument, Boolean paid, @Size(max = 500) String description, boolean active,
			Boolean requiresOwnerApproval, LetterTemplate letterTemplate) {
	}

	/** @param paid null = belum ditentukan (dipakai di Fase 5) */
	public record LeaveTypeResponse(Long id, String name, boolean requiresDocument, Boolean paid, String description,
			boolean active, boolean requiresOwnerApproval, LetterTemplate letterTemplate) {

		public static LeaveTypeResponse from(LeaveType type) {
			return new LeaveTypeResponse(type.getId(), type.getName(), type.isRequiresDocument(), type.getPaid(),
					type.getDescription(), type.isActive(), type.isRequiresOwnerApproval(),
					type.getLetterTemplate());
		}

	}

	/**
	 * Draft atau pengajuan izin. {@code employeeId} wajib bila Owner/HR mencatat atas nama karyawan (K-073) dan
	 * diabaikan pada pengajuan sendiri lewat {@code /api/me}. {@code draft=true} menyimpan tanpa validasi
	 * kelengkapan; tanpa {@code draft} (body lama) pengajuan langsung dikirim. Endpoint Owner/HR selalu mengirim.
	 */
	public record LeaveDraftRequest(Long employeeId, Long leaveTypeId, LocalDate startDate, LocalDate endDate,
			@Size(max = 1000, message = "Alasan maksimal 1000 karakter") String reason,
			@Size(max = 30, message = "No. HP maksimal 30 karakter") String contactPhone,
			@Size(max = 300, message = "Alamat maksimal 300 karakter") String leaveAddress,
			@Size(max = 150, message = "Nama pengganti maksimal 150 karakter") String delegateTo,
			@Size(max = 150, message = "Nama faskes maksimal 150 karakter") String healthFacility, Boolean draft) {
	}

	/**
	 * Keputusan Owner yang diberikan di luar web dan dicatat HR (spec bagian 6). {@code decidedAt} dibaca di zona
	 * waktu perusahaan.
	 */
	public record OwnerDecisionRequest(boolean approved, @NotNull(message = "Cara keputusan wajib dipilih") DecisionMethod method,
			@NotNull(message = "Waktu keputusan wajib diisi") LocalDateTime decidedAt,
			@NotBlank(message = "Catatan wajib diisi") @Size(max = 1000, message = "Catatan maksimal 1000 karakter") String note) {
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
			List<AttachmentResponse> attachments, String contactPhone, String leaveAddress, String delegateTo,
			String healthFacility, LetterTemplate letterTemplate, boolean requiresOwnerApproval,
			Instant createdAt, List<ApprovalResponse> approvals, ApprovalStage nextStage) {

		static LeaveRequestResponse from(LeaveRequest r, Integer workingDays, List<AttachmentResponse> attachments,
				List<ApprovalResponse> approvals) {
			var e = r.getEmployee();
			var t = r.getLeaveType();
			// Draft belum membekukan jenis: tampilkan pengaturan jenis saat ini agar form tahu isian apa yang perlu.
			LetterTemplate letterTemplate = r.getLetterTemplate() != null ? r.getLetterTemplate()
					: t == null ? null : t.getLetterTemplate();
			boolean requiresOwner = r.getRequiresOwnerApproval() != null ? r.getRequiresOwnerApproval()
					: t != null && t.isRequiresOwnerApproval();
			return new LeaveRequestResponse(r.getId(), e.getId(), e.getEmployeeNumber(), e.getFullName(),
					e.getPosition(), e.getBranch().getId(), e.getBranch().getName(), t == null ? null : t.getId(),
					t == null ? null : t.getName(), t != null && t.isRequiresDocument(), r.getStartDate(), r.getEndDate(), r.calendarDays(), workingDays,
					r.getReason(), r.getStatus(), r.getSubmittedBy(), r.getSubmittedAt(), r.getDecidedBy(),
					r.getDecidedAt(), r.getDecisionNote(), r.getCancelledBy(), r.getCancelledAt(), r.getCancelReason(),
					attachments, r.getContactPhone(), r.getLeaveAddress(), r.getDelegateTo(), r.getHealthFacility(),
					letterTemplate, requiresOwner, r.getCreatedAt(), approvals, awaitingStage(r.getStatus()));
		}

	}

	/** Tahap berikutnya yang harus memutus; null bila pengajuan tidak sedang menunggu. */
	static ApprovalStage awaitingStage(LeaveStatus status) {
		return switch (status) {
			case PERLU_TINJAU_HR -> ApprovalStage.HR;
			case PERLU_TINJAU_OWNER -> ApprovalStage.OWNER;
			default -> null;
		};
	}

	/** @param decidedByName nama karyawan yang terhubung ke akun, atau username bila tidak ada */
	public record ApprovalResponse(ApprovalStage stage, boolean approved, String decidedBy, String decidedByName,
			Instant decidedAt, DecisionMethod method, String recordedBy, String recordedByName, Instant recordedAt,
			String note, boolean hasEvidence) {

		static ApprovalResponse from(LeaveApproval a, java.util.function.Function<String, String> nameOf) {
			return new ApprovalResponse(a.getStage(), a.isApproved(), a.getDecidedBy(), nameOf.apply(a.getDecidedBy()),
					a.getDecidedAt(), a.getMethod(), a.getRecordedBy(), nameOf.apply(a.getRecordedBy()),
					a.getRecordedAt(), a.getNote(), a.getEvidenceStorageKey() != null);
		}

	}

	/** Verifikasi surat publik: sengaja minimal, tanpa alasan, isian templat, maupun lampiran. */
	public record PublicLetterResponse(String letterNumber, String employeeName, String branchName,
			String leaveTypeName, LocalDate startDate, LocalDate endDate, LeaveStatus status,
			List<PublicApproval> approvals) {
	}

	public record PublicApproval(ApprovalStage stage, boolean approved, String decidedByName, Instant decidedAt,
			DecisionMethod method) {
	}

	/** Pemakaian satu jenis pada satu tahun dari izin yang disetujui (K-072); rentang dipotong ke tahun tersebut. */
	public record UsageRow(Long leaveTypeId, String leaveTypeName, int requests, long calendarDays,
			Integer workingDays) {
	}

	public record UsageResponse(Long employeeId, int year, List<UsageRow> types, int pendingRequests) {
	}

	/** Ringkasan untuk lonceng notifikasi Owner/HR (K-081). */
	public record PendingItem(Long id, Long employeeId, String employeeName, String branchName, String leaveTypeName,
			LocalDate startDate, LocalDate endDate, Instant submittedAt, LeaveStatus status) {

		static PendingItem from(LeaveRequest r) {
			return new PendingItem(r.getId(), r.getEmployee().getId(), r.getEmployee().getFullName(),
					r.getEmployee().getBranch().getName(), r.getLeaveType().getName(), r.getStartDate(),
					r.getEndDate(), r.getSubmittedAt(), r.getStatus());
		}

	}

	public record PendingSummary(long count, List<PendingItem> items) {
	}

}
