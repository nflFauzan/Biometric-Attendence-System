package com.biometricattendance.leave;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.biometricattendance.account.UserAccount;
import com.biometricattendance.account.UserAccountRepository;
import com.biometricattendance.audit.AuditService;
import com.biometricattendance.calculation.EffectiveRule;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;
import com.biometricattendance.holiday.Holiday;
import com.biometricattendance.holiday.HolidayRepository;
import com.biometricattendance.leave.LeaveChangedEvent.Action;
import com.biometricattendance.leave.LeaveDtos.ApprovalResponse;
import com.biometricattendance.leave.LeaveDtos.AttachmentResponse;
import com.biometricattendance.leave.LeaveDtos.LeaveDraftRequest;
import com.biometricattendance.leave.LeaveDtos.LeaveRequestResponse;
import com.biometricattendance.leave.LeaveDtos.OwnerDecisionRequest;
import com.biometricattendance.leave.LeaveDtos.PendingItem;
import com.biometricattendance.leave.LeaveDtos.PendingSummary;
import com.biometricattendance.leave.LeaveDtos.RecordedDecisionItem;
import com.biometricattendance.leave.LeaveDtos.UsageResponse;
import com.biometricattendance.leave.LeaveDtos.UsageRow;
import com.biometricattendance.signature.SignatureService;
import com.biometricattendance.workrule.WorkRuleService;

/**
 * Pengajuan izin/cuti (Fase 4, docs/rencana-fase-4.md). Setiap perubahan dicatat di audit log dalam transaksi
 * yang sama dan diumumkan lewat {@link LeaveChangedEvent} (notifikasi Owner/HR, K-081).
 *
 * <p>Aturan yang dijaga di sini: transisi status K-074, tidak tumpang tindih K-075 (dijaga juga oleh constraint
 * database), wajib surat diperiksa saat persetujuan K-077, lampiran K-078, dan tidak boleh memutus pengajuan
 * sendiri K-079.
 */
@Service
public class LeaveService {

	static final String ENTITY = "LEAVE_REQUEST";
	static final String ATTACHMENT_ENTITY = "LEAVE_ATTACHMENT";
	static final String EVIDENCE_ENTITY = "LEAVE_EVIDENCE";
	static final int MAX_ATTACHMENTS = 5;
	static final int MAX_RANGE_DAYS = 366;
	static final int PENDING_LIST_LIMIT = 20;
	/** Batas tampilan notifikasi keputusan yang dicatat HR; bukan aturan bisnis. */
	static final Duration RECORDED_NOTICE_WINDOW = Duration.ofDays(7);
	static final Set<LeaveStatus> ACTIVE = LeaveStatuses.ACTIVE;
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String OVERLAP_CONSTRAINT = "leave_requests_no_overlap";
	private static final String RESUBMIT_CONSTRAINT = "ux_leave_requests_resubmitted_from";
	private static final String ALREADY_RESUBMITTED = "Pengajuan ini sudah diajukan ulang";
	private static final String STAGE_CONSTRAINT = "leave_approvals_stage_uq";
	private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
	private static final LocalDate LATEST = LocalDate.of(9999, 12, 31);

	private final LeaveRequestRepository requests;
	private final LeaveTypeRepository types;
	private final LeaveAttachmentRepository attachments;
	private final LeaveApprovalRepository approvals;
	private final HrReviewerCounter hrReviewers;
	private final AttachmentStorage storage;
	private final EmployeeRepository employees;
	private final UserAccountRepository accounts;
	private final HolidayRepository holidays;
	private final WorkRuleService workRules;
	private final AuditService audit;
	private final SignatureService signatures;
	private final ApplicationEventPublisher events;
	private final ZoneId companyZone;

	public LeaveService(LeaveRequestRepository requests, LeaveTypeRepository types,
			LeaveAttachmentRepository attachments, LeaveApprovalRepository approvals, HrReviewerCounter hrReviewers,
			AttachmentStorage storage, EmployeeRepository employees,
			UserAccountRepository accounts, HolidayRepository holidays, WorkRuleService workRules, AuditService audit,
			SignatureService signatures, ApplicationEventPublisher events, @Value("${app.company-timezone}") String companyZone) {
		this.requests = requests;
		this.types = types;
		this.attachments = attachments;
		this.approvals = approvals;
		this.hrReviewers = hrReviewers;
		this.storage = storage;
		this.employees = employees;
		this.accounts = accounts;
		this.holidays = holidays;
		this.workRules = workRules;
		this.audit = audit;
		this.signatures = signatures;
		this.events = events;
		this.companyZone = ZoneId.of(companyZone);
	}

	/** Isi pengajuan yang dicatat di audit (tanpa isi lampiran). */
	record AuditView(Long employeeId, String employeeName, String leaveType, LocalDate startDate, LocalDate endDate,
			String reason, LeaveStatus status, String decisionNote, String cancelReason) {

		static AuditView of(LeaveRequest r) {
			return new AuditView(r.getEmployee().getId(), r.getEmployee().getFullName(),
					r.getLeaveType() == null ? null : r.getLeaveType().getName(),
					r.getStartDate(), r.getEndDate(), r.getReason(), r.getStatus(), r.getDecisionNote(),
					r.getCancelReason());
		}

	}

	record AttachmentAuditView(Long leaveRequestId, String originalName, String contentType, long sizeBytes,
			String sha256) {
	}

	/** Isi file lampiran beserta nama dan jenisnya, untuk diunduh. */
	public record AttachmentFile(String originalName, String contentType, Resource resource) {
	}

	// ---- Owner/HR ----

	@Transactional(readOnly = true)
	public List<LeaveRequestResponse> list(LeaveStatus status, Long employeeId, Long branchId, LocalDate from,
			LocalDate to) {
		List<LeaveRequest> rows = requests.search(status, employeeId, branchId, from == null ? EARLIEST : from,
				to == null ? LATEST : to);
		return withAttachments(rows);
	}

	@Transactional(readOnly = true)
	public LeaveRequestResponse get(Long id) {
		return detailedForManager(find(id));
	}

	@Transactional
	public LeaveRequestResponse submitFor(LeaveDraftRequest request) {
		if (request.employeeId() == null) {
			throw ApiException.badRequest("Karyawan wajib dipilih");
		}
		Employee employee = employees.findById(request.employeeId())
			.orElseThrow(() -> ApiException.notFound("Karyawan"));
		// Pencatatan oleh Owner/HR selalu langsung dikirim; flag draft diabaikan.
		return create(employee, request, false, false);
	}

	/**
	 * Menyetujui tahap yang sedang berjalan. HR menyetujui tahap HR; pada jenis persetujuan ganda pengajuan lalu
	 * menunggu Owner, selain itu langsung disetujui. Owner mengerjakan tahap HR pada jenis satu persetujuan; pada
	 * jenis persetujuan ganda Owner memutus di tahap Owner, juga langsung dari tahap HR (K-125). Surat pendukung wajib
	 * ada selama pengajuan belum melewati tahap HR, siapa pun yang menyetujui.
	 */
	@Transactional
	public LeaveRequestResponse approve(Long id, String note) {
		LeaveRequest r = find(id);
		ApprovalStage stage = decisionCheck(r).orThrow();
		if (r.getStatus() == LeaveStatus.PERLU_TINJAU_HR && r.getLeaveType().isRequiresDocument()
				&& attachments.countByLeaveRequestId(r.getId()) == 0) {
			throw ApiException.badRequest("Jenis \"" + r.getLeaveType().getName()
					+ "\" wajib surat pendukung. Unggah surat sebelum menyetujui");
		}
		String reason = trimToNull(note);
		AuditView before = AuditView.of(r);
		Instant now = Instant.now();
		boolean moreStages = stage == ApprovalStage.HR && Boolean.TRUE.equals(r.getRequiresOwnerApproval());
		if (moreStages) {
			r.passHrStage();
			return decided(r, before, stage, true, reason, now, "STAGE_APPROVE", Action.STAGE_APPROVED);
		}
		r.approve(CurrentUser.username(), now, reason);
		return decided(r, before, stage, true, reason, now, "APPROVE", Action.APPROVED);
	}

	@Transactional
	public LeaveRequestResponse reject(Long id, String note) {
		LeaveRequest r = find(id);
		ApprovalStage stage = decisionCheck(r).orThrow();
		String reason = requireText(note, "Alasan penolakan wajib diisi");
		AuditView before = AuditView.of(r);
		Instant now = Instant.now();
		r.reject(CurrentUser.username(), now, reason);
		return decided(r, before, stage, false, reason, now, "REJECT", Action.REJECTED);
	}

	/** Owner/HR membatalkan pengajuan yang masih menunggu atau sudah disetujui; alasan wajib (K-074). */
	@Transactional
	public LeaveRequestResponse cancel(Long id, String note) {
		LeaveRequest r = find(id);
		if (!ACTIVE.contains(r.getStatus())) {
			throw ApiException.conflict("Pengajuan yang sudah " + label(r.getStatus()) + " tidak bisa dibatalkan");
		}
		String reason = requireText(note, "Alasan pembatalan wajib diisi");
		AuditView before = AuditView.of(r);
		r.cancel(CurrentUser.username(), Instant.now(), reason);
		return cancelled(r, before, reason);
	}

	@Transactional
	public AttachmentResponse addAttachment(Long id, MultipartFile file) {
		return attach(find(id), file);
	}

	@Transactional
	public AttachmentFile download(Long id, Long attachmentId) {
		LeaveRequest r = find(id);
		LeaveAttachment a = attachmentOf(r, attachmentId);
		// Surat pendukung bisa berisi data kesehatan: setiap pembukaan oleh pengelola dicatat.
		audit.record(CurrentUser.username(), "VIEW", ATTACHMENT_ENTITY, a.getId(), null,
				new AttachmentAuditView(r.getId(), a.getOriginalName(), a.getContentType(), a.getSizeBytes(),
						a.getSha256()),
				null);
		return new AttachmentFile(a.getOriginalName(), a.getContentType(), storage.load(a.getStorageKey()));
	}

	/**
	 * HR mencatat keputusan Owner yang diberikan di luar web (WhatsApp, lisan, tertulis; spec bagian 6). Riwayat
	 * mencatat Owner sebagai pemberi keputusan dan HR sebagai pencatat; tanda tangan Owner tidak ditempel.
	 */
	@Transactional
	public LeaveRequestResponse recordOwnerDecision(Long id, OwnerDecisionRequest req, MultipartFile evidence) {
		if (CurrentUser.hasRole("OWNER")) {
			throw ApiException.forbidden("Owner memutus langsung lewat tombol Setujui atau Tolak");
		}
		LeaveRequest r = find(id);
		ownerRecordCheck(r).orThrow();
		if (req.method() == DecisionMethod.WEB) {
			throw ApiException.badRequest("Keputusan lewat web dibuat Owner sendiri, bukan dicatat HR");
		}
		String note = requireText(req.note(), "Catatan wajib diisi");
		boolean hasEvidence = evidence != null && !evidence.isEmpty();
		if (!hasEvidence && (req.method() == DecisionMethod.WHATSAPP || req.method() == DecisionMethod.TERTULIS)) {
			throw ApiException.badRequest("Bukti wajib diunggah untuk keputusan lewat WhatsApp atau tertulis");
		}
		Instant now = Instant.now();
		Instant decidedAt = req.decidedAt().atZone(companyZone).toInstant();
		if (decidedAt.isAfter(now)) {
			throw ApiException.badRequest("Waktu keputusan Owner tidak boleh di masa depan");
		}
		Instant hrStageAt = approvals.findByLeaveRequestIdAndStage(r.getId(), ApprovalStage.HR)
			.map(LeaveApproval::getDecidedAt)
			.orElse(null);
		// Isian waktu dari UI presisi menit, jadi tahap HR dibandingkan per menit (10:05 sah setelah HR 10:05:30).
		if (hrStageAt != null && decidedAt.isBefore(hrStageAt.truncatedTo(ChronoUnit.MINUTES))) {
			throw ApiException.badRequest("Waktu keputusan Owner tidak boleh sebelum tahap HR");
		}

		AttachmentStorage.Stored stored = hasEvidence ? storeWithCleanup(evidence) : null;
		AuditView before = AuditView.of(r);
		if (req.approved()) {
			r.approve(CurrentUser.username(), now, note);
		}
		else {
			r.reject(CurrentUser.username(), now, note);
		}
		requests.saveAndFlush(r);
		LeaveApproval row = LeaveApproval.recordedForOwner(r.getId(), req.approved(), req.method(), decidedAt,
				CurrentUser.username(), now, note, hasEvidence ? originalName(evidence) : null, stored);
		try {
			approvals.saveAndFlush(row);
		}
		catch (DataIntegrityViolationException ex) {
			if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains(STAGE_CONSTRAINT)) {
				throw ApiException.conflict("Pengajuan ini baru saja diputus; muat ulang halaman");
			}
			throw ex;
		}
		audit.record(CurrentUser.username(), "OWNER_DECISION", ENTITY, r.getId(), before, AuditView.of(r), note);
		publish(r, Action.OWNER_DECISION_RECORDED);
		return detailedForManager(r);
	}

	/** Bukti keputusan Owner yang dicatat HR; setiap pembukaan diaudit seperti lampiran. */
	@Transactional
	public AttachmentFile downloadEvidence(Long id, ApprovalStage stage) {
		LeaveRequest r = find(id);
		LeaveApproval a = approvals.findByLeaveRequestIdAndStage(r.getId(), stage)
			.filter(row -> row.getEvidenceStorageKey() != null)
			.orElseThrow(() -> ApiException.notFound("Bukti keputusan"));
		audit.record(CurrentUser.username(), "VIEW", EVIDENCE_ENTITY, a.getId(), null,
				new AttachmentAuditView(r.getId(), a.getEvidenceOriginalName(), a.getEvidenceContentType(),
						a.getEvidenceSizeBytes(), a.getEvidenceSha256()),
				null);
		return new AttachmentFile(a.getEvidenceOriginalName(), a.getEvidenceContentType(),
				storage.load(a.getEvidenceStorageKey()));
	}

	/** Pemakaian izin yang disetujui per jenis pada satu tahun (K-072, tanpa kuota). */
	@Transactional(readOnly = true)
	public UsageResponse usage(Long employeeId, int year) {
		Employee employee = employees.findById(employeeId).orElseThrow(() -> ApiException.notFound("Karyawan"));
		LocalDate yearStart = LocalDate.of(year, 1, 1);
		LocalDate yearEnd = LocalDate.of(year, 12, 31);
		List<LeaveRequest> approved = requests.findOverlapping(List.of(employeeId), Set.of(LeaveStatus.DISETUJUI),
				yearStart, yearEnd);
		WorkingDayCounter counter = workingDayCounter(employee, yearStart, yearEnd);

		Map<Long, List<LeaveRequest>> byType = approved.stream()
			.sorted((a, b) -> a.getLeaveType().getName().compareToIgnoreCase(b.getLeaveType().getName()))
			.collect(Collectors.groupingBy(r -> r.getLeaveType().getId(), LinkedHashMap::new, Collectors.toList()));
		List<UsageRow> rows = new ArrayList<>();
		for (List<LeaveRequest> list : byType.values()) {
			long calendar = 0;
			Integer working = 0;
			for (LeaveRequest r : list) {
				LocalDate from = max(r.getStartDate(), yearStart);
				LocalDate to = min(r.getEndDate(), yearEnd);
				calendar += ChronoUnit.DAYS.between(from, to) + 1;
				Integer days = counter.count(from, to);
				working = working == null || days == null ? null : working + days;
			}
			rows.add(new UsageRow(list.get(0).getLeaveType().getId(), list.get(0).getLeaveType().getName(),
					list.size(), calendar, working));
		}
		int pending = requests.findOverlapping(List.of(employeeId), LeaveStatuses.PENDING, EARLIEST, LATEST)
			.size();
		return new UsageResponse(employeeId, year, rows, pending);
	}

	/**
	 * Pengajuan yang menunggu keputusan pemanggil, untuk lonceng notifikasi (K-081). HR melihat tahap HR; Owner
	 * melihat tahap Owner ditambah tahap HR pada jenis satu persetujuan (yang juga boleh diputusnya).
	 */
	@Transactional(readOnly = true)
	public PendingSummary pending() {
		boolean owner = CurrentUser.hasRole("OWNER");
		// Hasil hitung HR lain per pemohon disimpan sementara agar tidak query berulang untuk pemohon yang sama.
		Map<Long, Boolean> soleHr = new HashMap<>();
		List<LeaveRequest> list = owner
				? requests.findByStatusDetailed(LeaveStatuses.PENDING)
					.stream()
					.filter(r -> r.getStatus() == LeaveStatus.PERLU_TINJAU_OWNER
							|| !Boolean.TRUE.equals(r.getRequiresOwnerApproval())
							|| soleHr.computeIfAbsent(r.getEmployee().getId(),
									e -> hrReviewers.countOtherActiveHr(e) == 0))
					.toList()
				: requests.findByStatusDetailed(Set.of(LeaveStatus.PERLU_TINJAU_HR));
		return new PendingSummary(list.size(),
				list.stream().limit(PENDING_LIST_LIMIT).map(PendingItem::from).toList(),
				owner ? recordedForOwner() : List.of());
	}

	/** Keputusan Owner yang dicatat HR dalam {@link #RECORDED_NOTICE_WINDOW} terakhir, terbaru dulu. */
	private List<RecordedDecisionItem> recordedForOwner() {
		List<LeaveApproval> found = approvals.findRecordedForOwnerSince(Instant.now().minus(RECORDED_NOTICE_WINDOW),
				PageRequest.of(0, PENDING_LIST_LIMIT));
		if (found.isEmpty()) {
			return List.of();
		}
		Map<Long, LeaveRequest> byId = new HashMap<>();
		requests.findAllById(found.stream().map(LeaveApproval::getLeaveRequestId).collect(Collectors.toSet()))
			.forEach(r -> byId.put(r.getId(), r));
		// Pencatat tanpa karyawan terhubung ditampilkan sebagai peran, bukan username (K-123).
		Function<String, String> nameOf = nameResolver(found, u -> "HR/Finance");
		return found.stream()
			.filter(a -> byId.containsKey(a.getLeaveRequestId()))
			.map(a -> {
				LeaveRequest r = byId.get(a.getLeaveRequestId());
				return new RecordedDecisionItem(a.getId(), r.getId(), r.getEmployee().getFullName(),
						r.getLeaveType() == null ? null : r.getLeaveType().getName(), a.isApproved(), a.getMethod(),
						nameOf.apply(a.getRecordedBy()), a.getRecordedAt(), r.getStatus());
			})
			.toList();
	}

	// ---- Karyawan (pengajuan milik sendiri) ----

	@Transactional(readOnly = true)
	public List<LeaveRequestResponse> mine() {
		return withAttachments(requests.findForEmployee(myEmployee().getId()));
	}

	@Transactional(readOnly = true)
	public LeaveRequestResponse getMine(Long id) {
		return detailed(findMine(id));
	}

	/** Menyimpan draft ({@code draft=true}) atau langsung mengirim pengajuan sendiri. */
	@Transactional
	public LeaveRequestResponse createMine(LeaveDraftRequest request) {
		return create(myEmployee(), request, Boolean.TRUE.equals(request.draft()), true);
	}

	/**
	 * Ajukan ulang pengajuan yang ditolak (K-126): draft baru berisi salinan jenis, tanggal, alasan, dan isian
	 * templat, tanpa lampiran. Pengajuan lama tidak berubah; satu pengajuan yang ditolak hanya diajukan ulang sekali.
	 */
	@Transactional
	public LeaveRequestResponse resubmitMine(Long id) {
		LeaveRequest old = findMine(id);
		if (old.getStatus() != LeaveStatus.DITOLAK) {
			throw ApiException.conflict("Hanya pengajuan yang ditolak yang bisa diajukan ulang");
		}
		if (requests.existsByResubmittedFromId(old.getId())) {
			throw ApiException.conflict(ALREADY_RESUBMITTED);
		}
		if (old.getLeaveType() != null && !old.getLeaveType().isActive()) {
			throw ApiException.conflict("Jenis izin ini sudah tidak aktif");
		}
		LeaveRequest r = new LeaveRequest(old.getEmployee(), old.getLeaveType(), old.getStartDate(), old.getEndDate(),
				old.getReason(), null, null, LeaveStatus.DRAFT);
		r.setContactPhone(old.getContactPhone());
		r.setLeaveAddress(old.getLeaveAddress());
		r.setDelegateTo(old.getDelegateTo());
		r.setHealthFacility(old.getHealthFacility());
		r.setResubmittedFromId(old.getId());
		try {
			r = requests.saveAndFlush(r);
		}
		catch (DataIntegrityViolationException ex) {
			// Klik ganda: pengajuan ulang lain dari sumber yang sama tersimpan bersamaan.
			if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains(RESUBMIT_CONSTRAINT)) {
				throw ApiException.conflict(ALREADY_RESUBMITTED);
			}
			throw ex;
		}
		audit.record(CurrentUser.username(), "RESUBMIT", ENTITY, r.getId(), null, AuditView.of(r),
				"Ajukan ulang dari #" + old.getId());
		return detailed(r);
	}

	@Transactional
	public LeaveRequestResponse updateMyDraft(Long id, LeaveDraftRequest request) {
		LeaveRequest r = findMine(id);
		if (r.getStatus() != LeaveStatus.DRAFT) {
			throw ApiException.conflict("Hanya draft yang bisa diubah");
		}
		if (!r.getEmployee().isActive()) {
			throw ApiException.badRequest("Karyawan tidak aktif");
		}
		AuditView before = AuditView.of(r);
		LeaveType type = resolveType(request.leaveTypeId());
		checkDates(request.startDate(), request.endDate());
		r.updateDraft(type, request.startDate(), request.endDate(), trimToNull(request.reason()));
		applyLetterFields(r, request);
		requests.saveAndFlush(r);
		audit.record(CurrentUser.username(), "UPDATE", ENTITY, r.getId(), before, AuditView.of(r), null);
		return detailed(r);
	}

	/** Hapus permanen: draft belum menjadi dokumen resmi. File lampiran dihapus dari disk setelah commit. */
	@Transactional
	public void deleteMyDraft(Long id) {
		LeaveRequest r = findMine(id);
		if (r.getStatus() != LeaveStatus.DRAFT) {
			throw ApiException.conflict("Hanya draft yang bisa dihapus");
		}
		AuditView before = AuditView.of(r);
		List<LeaveAttachment> files = attachments.findByLeaveRequestIdOrderByIdAsc(r.getId());
		List<String> keys = files.stream().map(LeaveAttachment::getStorageKey).toList();
		attachments.deleteAll(files);
		requests.delete(r);
		requests.flush();
		audit.record(CurrentUser.username(), "DELETE", ENTITY, id, before, null, null);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				keys.forEach(storage::delete);
			}
		});
	}

	@Transactional
	public LeaveRequestResponse submitMyDraft(Long id) {
		LeaveRequest r = findMine(id);
		if (r.getStatus() != LeaveStatus.DRAFT) {
			throw ApiException.conflict("Hanya draft yang bisa dikirim");
		}
		AuditView before = AuditView.of(r);
		prepareSubmit(r);
		r.submit(CurrentUser.username(), Instant.now(), newToken());
		r.setApplicantSignatureKey(signatures.freezeCopy(CurrentUser.username()));
		flushSubmitted(r);
		audit.record(CurrentUser.username(), "SUBMIT", ENTITY, r.getId(), before, AuditView.of(r), null);
		publish(r, Action.SUBMITTED);
		return detailed(r);
	}

	/** Karyawan hanya bisa membatalkan pengajuan yang belum diputus (K-074). */
	@Transactional
	public LeaveRequestResponse cancelMine(Long id, String note) {
		LeaveRequest r = findMine(id);
		if (!r.getStatus().isPending()) {
			throw ApiException.conflict("Pengajuan yang sudah " + label(r.getStatus())
					+ " hanya bisa dibatalkan oleh Owner atau HR/Finance");
		}
		AuditView before = AuditView.of(r);
		String reason = trimToNull(note);
		r.cancel(CurrentUser.username(), Instant.now(), reason == null ? "Dibatalkan oleh pengaju" : reason);
		return cancelled(r, before, r.getCancelReason());
	}

	@Transactional
	public AttachmentResponse addMyAttachment(Long id, MultipartFile file) {
		return attach(findMine(id), file);
	}

	@Transactional(readOnly = true)
	public AttachmentFile downloadMine(Long id, Long attachmentId) {
		LeaveAttachment a = attachmentOf(findMine(id), attachmentId);
		return new AttachmentFile(a.getOriginalName(), a.getContentType(), storage.load(a.getStorageKey()));
	}

	// ---- Bersama ----

	/** Menyimpan draft, atau langsung mengirim bila bukan draft (validasi lengkap berlaku). */
	/**
	 * {@code own} true bila pengirim adalah pemohon sendiri; hanya saat itu spesimennya dibekukan sebagai tanda
	 * tangan pemohon.
	 */
	private LeaveRequestResponse create(Employee employee, LeaveDraftRequest request, boolean draft, boolean own) {
		if (!employee.isActive()) {
			throw ApiException.badRequest("Karyawan tidak aktif");
		}
		LeaveType type = resolveType(request.leaveTypeId());
		checkDates(request.startDate(), request.endDate());
		LeaveRequest r = new LeaveRequest(employee, type, request.startDate(), request.endDate(),
				trimToNull(request.reason()), null, null, LeaveStatus.DRAFT);
		applyLetterFields(r, request);
		if (draft) {
			r = requests.saveAndFlush(r);
			audit.record(CurrentUser.username(), "CREATE", ENTITY, r.getId(), null, AuditView.of(r), null);
			return detailed(r);
		}
		prepareSubmit(r);
		r.submit(CurrentUser.username(), Instant.now(), newToken());
		if (own) {
			r.setApplicantSignatureKey(signatures.freezeCopy(CurrentUser.username()));
		}
		r = flushSubmitted(r);
		audit.record(CurrentUser.username(), "CREATE", ENTITY, r.getId(), null, AuditView.of(r), null);
		publish(r, Action.SUBMITTED);
		return detailed(r);
	}

	/** Jenis yang diisi harus ada dan aktif; null berarti belum dipilih (hanya sah untuk draft). */
	private LeaveType resolveType(Long leaveTypeId) {
		if (leaveTypeId == null) {
			return null;
		}
		LeaveType type = types.findById(leaveTypeId).orElseThrow(() -> ApiException.notFound("Jenis izin"));
		if (!type.isActive()) {
			throw ApiException.badRequest("Jenis izin \"" + type.getName() + "\" sudah tidak aktif");
		}
		return type;
	}

	/** Urutan dan rentang tanggal; dicek hanya bila kedua tanggal ada (draft boleh belum lengkap). */
	private static void checkDates(LocalDate start, LocalDate end) {
		if (start == null || end == null) {
			return;
		}
		if (end.isBefore(start)) {
			throw ApiException.badRequest("Tanggal selesai tidak boleh sebelum tanggal mulai");
		}
		if (ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
			throw ApiException.badRequest("Rentang izin maksimal " + MAX_RANGE_DAYS + " hari");
		}
	}

	private static void applyLetterFields(LeaveRequest r, LeaveDraftRequest request) {
		r.setContactPhone(trimToNull(request.contactPhone()));
		r.setLeaveAddress(trimToNull(request.leaveAddress()));
		r.setDelegateTo(trimToNull(request.delegateTo()));
		r.setHealthFacility(trimToNull(request.healthFacility()));
	}

	/**
	 * Pemeriksaan penuh sebelum kirim: jenis aktif, tanggal lengkap dan valid, isian templat, dan tidak
	 * tumpang tindih dengan pengajuan aktif (K-075).
	 */
	private void prepareSubmit(LeaveRequest r) {
		if (!r.getEmployee().isActive()) {
			throw ApiException.badRequest("Karyawan tidak aktif");
		}
		LeaveType type = r.getLeaveType();
		if (type == null) {
			throw ApiException.badRequest("Jenis izin wajib dipilih");
		}
		if (!type.isActive()) {
			throw ApiException.badRequest("Jenis izin \"" + type.getName() + "\" sudah tidak aktif");
		}
		if (r.getStartDate() == null) {
			throw ApiException.badRequest("Tanggal mulai wajib diisi");
		}
		if (r.getEndDate() == null) {
			throw ApiException.badRequest("Tanggal selesai wajib diisi");
		}
		checkDates(r.getStartDate(), r.getEndDate());
		LetterFieldsValidator.validate(type.getLetterTemplate(), r);
		requests.findOverlapping(List.of(r.getEmployee().getId()), ACTIVE, r.getStartDate(), r.getEndDate())
			.stream()
			.findFirst()
			.ifPresent(existing -> {
				throw overlapConflict(existing);
			});
	}

	private LeaveRequest flushSubmitted(LeaveRequest r) {
		try {
			return requests.saveAndFlush(r);
		}
		catch (DataIntegrityViolationException ex) {
			// Pengajuan lain yang beririsan tersimpan bersamaan (K-075).
			if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains(OVERLAP_CONSTRAINT)) {
				throw ApiException.conflict("Tanggal bertabrakan dengan pengajuan lain yang masih aktif");
			}
			throw ex;
		}
	}

	private static String newToken() {
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		return HexFormat.of().formatHex(bytes);
	}

	/** Menyimpan keputusan satu tahap beserta barisnya di riwayat, dalam satu transaksi dengan audit. */
	private LeaveRequestResponse decided(LeaveRequest r, AuditView before, ApprovalStage stage, boolean approved,
			String note, Instant at, String action, Action event) {
		// Flush di sini agar konflik @Version (dua penyetuju bersamaan) muncul sebelum audit tercatat.
		requests.saveAndFlush(r);
		try {
			// Salinan beku spesimen penyetuju; penolakan tidak menempelkan tanda tangan.
			String signatureKey = approved ? signatures.freezeCopy(CurrentUser.username()) : null;
			approvals.saveAndFlush(
					LeaveApproval.web(r.getId(), stage, approved, CurrentUser.username(), at, note, signatureKey));
		}
		catch (DataIntegrityViolationException ex) {
			// Tahap yang sama sudah diputus oleh penyetuju lain pada saat yang sama.
			if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains(STAGE_CONSTRAINT)) {
				throw ApiException.conflict("Pengajuan ini baru saja diputus; muat ulang halaman");
			}
			throw ex;
		}
		audit.record(CurrentUser.username(), action, ENTITY, r.getId(), before, AuditView.of(r), note);
		publish(r, event);
		return detailedForManager(r);
	}

	private LeaveRequestResponse cancelled(LeaveRequest r, AuditView before, String reason) {
		requests.saveAndFlush(r);
		audit.record(CurrentUser.username(), "CANCEL", ENTITY, r.getId(), before, AuditView.of(r), reason);
		publish(r, Action.CANCELLED);
		return detailed(r);
	}

	/**
	 * Hasil pemeriksaan satu pintu atas hak pemanggil memutus. Endpoint aksi melempar {@link #denial}, sedangkan
	 * flag di respons hanya membaca {@link #allowed()}, sehingga keduanya tidak bisa berbeda.
	 */
	private record Check<T>(T value, ApiException denial) {

		static <T> Check<T> ok(T value) {
			return new Check<>(value, null);
		}

		static <T> Check<T> denied(ApiException denial) {
			return new Check<>(null, denial);
		}

		boolean allowed() {
			return denial == null;
		}

		T orThrow() {
			if (denial != null) {
				throw denial;
			}
			return value;
		}

	}

	/**
	 * Tahap yang boleh dikerjakan pemanggil pada pengajuan ini, atau alasan penolakannya (409 bila sudah tidak
	 * menunggu, 403 bila milik sendiri atau bukan gilirannya).
	 */
	private Check<ApprovalStage> decisionCheck(LeaveRequest r) {
		if (!r.getStatus().isPending()) {
			return Check.denied(ApiException
				.conflict("Pengajuan ini sudah " + label(r.getStatus()) + "; muat ulang halaman"));
		}
		if (isOwnRequest(r)) {
			return Check.denied(ApiException
				.forbidden("Pengajuan milik sendiri harus diputus oleh Owner atau HR/Finance lain"));
		}
		boolean owner = CurrentUser.hasRole("OWNER");
		if (r.getStatus() == LeaveStatus.PERLU_TINJAU_OWNER) {
			return owner ? Check.ok(ApprovalStage.OWNER) : Check.denied(ApiException.forbidden("Menunggu persetujuan Owner"));
		}
		// Pada jenis persetujuan ganda Owner memutus langsung tanpa tahap HR; keputusannya tercatat di tahap Owner
		// sehingga satu orang tidak pernah memberi dua persetujuan (K-125).
		if (owner && Boolean.TRUE.equals(r.getRequiresOwnerApproval())) {
			return Check.ok(ApprovalStage.OWNER);
		}
		return Check.ok(ApprovalStage.HR);
	}

	/** Boleh tidaknya pemanggil (bukan Owner) mencatat keputusan Owner yang diberikan di luar web. */
	private Check<Boolean> ownerRecordCheck(LeaveRequest r) {
		if (CurrentUser.hasRole("OWNER")) {
			return Check.denied(ApiException.forbidden("Owner memutus langsung lewat tombol Setujui atau Tolak"));
		}
		if (r.getStatus() != LeaveStatus.PERLU_TINJAU_OWNER) {
			return Check.denied(ApiException
				.conflict("Pengajuan ini tidak sedang menunggu keputusan Owner; muat ulang halaman"));
		}
		if (isOwnRequest(r)) {
			return Check.denied(ApiException
				.forbidden("Pengajuan milik sendiri harus diputus oleh Owner atau HR/Finance lain"));
		}
		return Check.ok(Boolean.TRUE);
	}

	private AttachmentResponse attach(LeaveRequest r, MultipartFile file) {
		if (r.getStatus() != LeaveStatus.DRAFT && !r.getStatus().isPending()) {
			throw ApiException.conflict("Lampiran hanya bisa ditambahkan pada draft atau pengajuan yang belum diputus");
		}
		if (attachments.countByLeaveRequestId(r.getId()) >= MAX_ATTACHMENTS) {
			throw ApiException.badRequest("Maksimal " + MAX_ATTACHMENTS + " lampiran per pengajuan");
		}
		AttachmentStorage.Stored stored = storeWithCleanup(file);
		LeaveAttachment saved = attachments.save(new LeaveAttachment(r.getId(), originalName(file),
				stored.contentType(), stored.size(), stored.key(), stored.sha256(), CurrentUser.username()));
		audit.record(CurrentUser.username(), "UPLOAD", ATTACHMENT_ENTITY, saved.getId(), null,
				new AttachmentAuditView(r.getId(), saved.getOriginalName(), saved.getContentType(),
						saved.getSizeBytes(), saved.getSha256()),
				null);
		publish(r, Action.ATTACHMENT_ADDED);
		return AttachmentResponse.from(saved);
	}

	/** Menulis file ke disk (validasi K-078); dihapus lagi bila transaksi yang mencatat metadatanya gagal. */
	private AttachmentStorage.Stored storeWithCleanup(MultipartFile file) {
		AttachmentStorage.Stored stored = storage.store(file);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					storage.delete(stored.key());
				}
			}
		});
		return stored;
	}

	/** Draft hanya terlihat oleh pemiliknya, jadi bagi Owner/HR draft sama dengan tidak ada. */
	LeaveRequest find(Long id) {
		return requests.findDetailed(id)
			.filter(r -> r.getStatus() != LeaveStatus.DRAFT)
			.orElseThrow(() -> ApiException.notFound("Pengajuan izin"));
	}

	/** Pengajuan karyawan lain dibalas 404 (bukan 403) agar ID tidak bisa ditebak. */
	LeaveRequest findMine(Long id) {
		Long mine = myEmployee().getId();
		return requests.findDetailed(id)
			.filter(r -> r.getEmployee().getId().equals(mine))
			.orElseThrow(() -> ApiException.notFound("Pengajuan izin"));
	}

	private Employee myEmployee() {
		return accounts.findByUsername(CurrentUser.username())
			.map(UserAccount::getEmployee)
			.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN,
					"Akun ini belum terhubung ke data karyawan. Hubungi HR/Finance"));
	}

	private LeaveAttachment attachmentOf(LeaveRequest r, Long attachmentId) {
		return attachments.findByIdAndLeaveRequestId(attachmentId, r.getId())
			.orElseThrow(() -> ApiException.notFound("Lampiran"));
	}

	private boolean isOwnRequest(LeaveRequest r) {
		Long own = accounts.findByUsername(CurrentUser.username())
			.map(UserAccount::getEmployee)
			.map(Employee::getId)
			.orElse(null);
		return Objects.equals(own, r.getEmployee().getId());
	}

	/** Respons untuk karyawan pemohon dan pemakaian lain tanpa aksi keputusan: kedua flag selalu false. */
	private LeaveRequestResponse detailed(LeaveRequest r) {
		return detailed(r, false, false);
	}

	/** Respons untuk Owner/HR: flag keputusan dihitung dari pemeriksaan yang sama dengan endpoint aksi. */
	private LeaveRequestResponse detailedForManager(LeaveRequest r) {
		return detailed(r, decisionCheck(r).allowed(), ownerRecordCheck(r).allowed());
	}

	private LeaveRequestResponse detailed(LeaveRequest r, boolean canDecide, boolean canRecordOwnerDecision) {
		Integer working = r.getStartDate() == null || r.getEndDate() == null ? null
				: workingDayCounter(r.getEmployee(), r.getStartDate(), r.getEndDate()).count(r.getStartDate(),
						r.getEndDate());
		List<LeaveApproval> history = approvals.findByLeaveRequestIdOrderByIdAsc(r.getId());
		return LeaveRequestResponse.from(r, working, attachments.findByLeaveRequestIdOrderByIdAsc(r.getId())
			.stream()
			.map(AttachmentResponse::from)
			.toList(), approvalResponses(history), canDecide, canRecordOwnerDecision);
	}

	private List<LeaveRequestResponse> withAttachments(List<LeaveRequest> rows) {
		if (rows.isEmpty()) {
			return List.of();
		}
		List<Long> ids = rows.stream().map(LeaveRequest::getId).toList();
		Map<Long, List<AttachmentResponse>> byRequest = attachments.findByLeaveRequestIdIn(ids)
			.stream()
			.sorted((a, b) -> a.getId().compareTo(b.getId()))
			.collect(Collectors.groupingBy(LeaveAttachment::getLeaveRequestId,
					Collectors.mapping(AttachmentResponse::from, Collectors.toList())));
		List<LeaveApproval> history = approvals.findByLeaveRequestIdIn(ids)
			.stream()
			.sorted((a, b) -> a.getId().compareTo(b.getId()))
			.toList();
		Function<String, String> nameOf = nameResolver(history);
		Map<Long, List<ApprovalResponse>> approvalsByRequest = history.stream()
			.collect(Collectors.groupingBy(LeaveApproval::getLeaveRequestId,
					Collectors.mapping(a -> ApprovalResponse.from(a, nameOf), Collectors.toList())));
		return rows.stream()
			.map(r -> LeaveRequestResponse.from(r, null, byRequest.getOrDefault(r.getId(), List.of()),
					approvalsByRequest.getOrDefault(r.getId(), List.of()), false, false))
			.toList();
	}

	private List<ApprovalResponse> approvalResponses(List<LeaveApproval> history) {
		Function<String, String> nameOf = nameResolver(history);
		return history.stream().map(a -> ApprovalResponse.from(a, nameOf)).toList();
	}

	/** Nama untuk username pemberi keputusan/pencatat: nama karyawan yang terhubung ke akun, atau username. */
	Function<String, String> nameResolver(List<LeaveApproval> history) {
		return nameResolver(history, u -> u);
	}

	/** Seperti di atas, tetapi {@code fallback} menentukan nama bila akun tidak terhubung ke karyawan. */
	Function<String, String> nameResolver(List<LeaveApproval> history, Function<String, String> fallback) {
		Set<String> usernames = history.stream()
			.flatMap(a -> Stream.of(a.getDecidedBy(), a.getRecordedBy()))
			.filter(u -> !LeaveApproval.OWNER_ACTOR.equals(u))
			.collect(Collectors.toSet());
		if (usernames.isEmpty()) {
			return u -> LeaveApproval.OWNER_ACTOR.equals(u) ? "Owner" : u;
		}
		Map<String, String> names = new LinkedHashMap<>();
		for (UserAccount account : accounts.findByUsernameInWithEmployee(usernames)) {
			if (account.getEmployee() != null) {
				names.put(account.getUsername(), account.getEmployee().getFullName());
			}
		}
		return username -> LeaveApproval.OWNER_ACTOR.equals(username) ? "Owner" : names.getOrDefault(username, fallback.apply(username));
	}

	private void publish(LeaveRequest r, Action action) {
		if (r.getStatus() == LeaveStatus.DRAFT) {
			return; // draft tidak terlihat oleh Owner/HR, jadi tidak ada notifikasi
		}
		events.publishEvent(new LeaveChangedEvent(r.getId(), r.getEmployee().getId(), r.getEmployee().getFullName(),
				r.getLeaveType().getName(), action));
	}

	/**
	 * Menghitung hari kerja di dalam rentang menurut hari kerja pada aturan yang berlaku tiap tanggal dan kalender
	 * libur. Mengembalikan null bila hari kerja belum diatur untuk salah satu tanggal (tidak ditebak).
	 */
	private WorkingDayCounter workingDayCounter(Employee employee, LocalDate from, LocalDate to) {
		Function<LocalDate, EffectiveRule> resolver = workRules.resolverFor(employee);
		Set<LocalDate> holidayDates = holidays.findByHolidayDateBetweenOrderByHolidayDateAsc(from, to)
			.stream()
			.map(Holiday::getHolidayDate)
			.collect(Collectors.toSet());
		return (start, end) -> {
			int count = 0;
			for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
				Set<DayOfWeek> workDays = resolver.apply(date).workDays();
				if (workDays == null) {
					return null;
				}
				if (workDays.contains(date.getDayOfWeek()) && !holidayDates.contains(date)) {
					count++;
				}
			}
			return count;
		};
	}

	@FunctionalInterface
	private interface WorkingDayCounter {

		Integer count(LocalDate from, LocalDate to);

	}

	private static ApiException overlapConflict(LeaveRequest existing) {
		return ApiException.conflict("Tanggal bertabrakan dengan pengajuan " + existing.getLeaveType().getName() + " "
				+ existing.getStartDate() + " s.d. " + existing.getEndDate() + " (" + label(existing.getStatus())
				+ ")");
	}

	static String label(LeaveStatus status) {
		return switch (status) {
			case DRAFT -> "draft";
			case PERLU_TINJAU_HR -> "perlu ditinjau HR";
			case PERLU_TINJAU_OWNER -> "perlu ditinjau Owner";
			case DISETUJUI -> "disetujui";
			case DITOLAK -> "ditolak";
			case DIBATALKAN -> "dibatalkan";
		};
	}

	private static String originalName(MultipartFile file) {
		String name = file.getOriginalFilename();
		if (name == null || name.isBlank()) {
			return "lampiran";
		}
		// Hanya nama file (tanpa folder); dibatasi panjangnya. Nama ini tidak pernah dipakai sebagai path.
		String normalized = name.replace('\\', '/');
		String base = normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
		return base.isBlank() ? "lampiran" : base.length() > 255 ? base.substring(base.length() - 255) : base;
	}

	private static String requireText(String value, String message) {
		String text = trimToNull(value);
		if (text == null) {
			throw ApiException.badRequest(message);
		}
		return text;
	}

	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static LocalDate max(LocalDate a, LocalDate b) {
		return a.isAfter(b) ? a : b;
	}

	private static LocalDate min(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}

}
