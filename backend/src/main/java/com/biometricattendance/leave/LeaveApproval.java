package com.biometricattendance.leave;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Riwayat keputusan satu tahap pada pengajuan izin. Tidak pernah diubah atau dihapus; satu baris per tahap
 * (unique {@code leave_request_id, stage}) sehingga dua keputusan bersamaan tidak bisa sama-sama tersimpan.
 */
@Entity
@Table(name = "leave_approvals")
public class LeaveApproval {

	/** Nilai {@code decidedBy} untuk keputusan Owner yang dicatat HR; dirender sebagai "Owner". */
	public static final String OWNER_ACTOR = "OWNER";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private Long leaveRequestId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private ApprovalStage stage;

	@Column(nullable = false, updatable = false)
	private boolean approved;

	@Column(nullable = false, updatable = false)
	private String decidedBy;

	@Column(nullable = false, updatable = false)
	private Instant decidedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private DecisionMethod method;

	@Column(nullable = false, updatable = false)
	private String recordedBy;

	@Column(nullable = false, updatable = false)
	private Instant recordedAt;

	@Column(updatable = false)
	private String note;

	@Column(updatable = false)
	private String evidenceStorageKey;

	@Column(updatable = false)
	private String evidenceOriginalName;

	@Column(updatable = false)
	private String evidenceContentType;

	@Column(updatable = false)
	private Long evidenceSizeBytes;

	@Column(name = "evidence_sha256", updatable = false)
	private String evidenceSha256;

	@Column(updatable = false)
	private String signatureStorageKey;

	protected LeaveApproval() {
	}

	/** Keputusan lewat web: pencatat sama dengan pemberi keputusan. */
	public static LeaveApproval web(Long leaveRequestId, ApprovalStage stage, boolean approved, String by, Instant at,
			String note) {
		return web(leaveRequestId, stage, approved, by, at, note, null);
	}

	/** Keputusan lewat web beserta salinan beku spesimen penyetuju (null bila tidak punya spesimen). */
	public static LeaveApproval web(Long leaveRequestId, ApprovalStage stage, boolean approved, String by, Instant at,
			String note, String signatureStorageKey) {
		LeaveApproval a = new LeaveApproval();
		a.leaveRequestId = leaveRequestId;
		a.stage = stage;
		a.approved = approved;
		a.decidedBy = by;
		a.decidedAt = at;
		a.method = DecisionMethod.WEB;
		a.recordedBy = by;
		a.recordedAt = at;
		a.note = note;
		a.signatureStorageKey = signatureStorageKey;
		return a;
	}

	/**
	 * Keputusan Owner yang diberikan di luar web dan dicatat HR. Pemberi keputusan adalah {@link #OWNER_ACTOR}
	 * (bukan akun), pencatat adalah HR. Tanda tangan sengaja tidak ada: HR tidak boleh menempelkannya.
	 */
	public static LeaveApproval recordedForOwner(Long leaveRequestId, boolean approved, DecisionMethod method,
			Instant decidedAt, String recordedBy, Instant recordedAt, String note, String evidenceOriginalName,
			AttachmentStorage.Stored evidence) {
		LeaveApproval a = new LeaveApproval();
		a.leaveRequestId = leaveRequestId;
		a.stage = ApprovalStage.OWNER;
		a.approved = approved;
		a.decidedBy = OWNER_ACTOR;
		a.decidedAt = decidedAt;
		a.method = method;
		a.recordedBy = recordedBy;
		a.recordedAt = recordedAt;
		a.note = note;
		if (evidence != null) {
			a.evidenceStorageKey = evidence.key();
			a.evidenceOriginalName = evidenceOriginalName;
			a.evidenceContentType = evidence.contentType();
			a.evidenceSizeBytes = evidence.size();
			a.evidenceSha256 = evidence.sha256();
		}
		return a;
	}

	public Long getId() {
		return id;
	}

	public Long getLeaveRequestId() {
		return leaveRequestId;
	}

	public ApprovalStage getStage() {
		return stage;
	}

	public boolean isApproved() {
		return approved;
	}

	public String getDecidedBy() {
		return decidedBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public DecisionMethod getMethod() {
		return method;
	}

	public String getRecordedBy() {
		return recordedBy;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

	public String getNote() {
		return note;
	}

	public String getEvidenceStorageKey() {
		return evidenceStorageKey;
	}

	public String getEvidenceOriginalName() {
		return evidenceOriginalName;
	}

	public String getEvidenceContentType() {
		return evidenceContentType;
	}

	public Long getEvidenceSizeBytes() {
		return evidenceSizeBytes;
	}

	public String getEvidenceSha256() {
		return evidenceSha256;
	}

	public String getSignatureStorageKey() {
		return signatureStorageKey;
	}

}
