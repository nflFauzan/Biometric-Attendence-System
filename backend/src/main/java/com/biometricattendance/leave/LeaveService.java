package com.biometricattendance.leave;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
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
import com.biometricattendance.leave.LeaveDtos.AttachmentResponse;
import com.biometricattendance.leave.LeaveDtos.LeaveRequestResponse;
import com.biometricattendance.leave.LeaveDtos.PendingItem;
import com.biometricattendance.leave.LeaveDtos.PendingSummary;
import com.biometricattendance.leave.LeaveDtos.SubmitLeaveRequest;
import com.biometricattendance.leave.LeaveDtos.UsageResponse;
import com.biometricattendance.leave.LeaveDtos.UsageRow;
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
	static final int MAX_ATTACHMENTS = 5;
	static final int MAX_RANGE_DAYS = 366;
	static final int PENDING_LIST_LIMIT = 20;
	static final Set<LeaveStatus> ACTIVE = EnumSet.of(LeaveStatus.MENUNGGU, LeaveStatus.DISETUJUI);
	private static final String OVERLAP_CONSTRAINT = "leave_requests_no_overlap";
	private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
	private static final LocalDate LATEST = LocalDate.of(9999, 12, 31);

	private final LeaveRequestRepository requests;
	private final LeaveTypeRepository types;
	private final LeaveAttachmentRepository attachments;
	private final AttachmentStorage storage;
	private final EmployeeRepository employees;
	private final UserAccountRepository accounts;
	private final HolidayRepository holidays;
	private final WorkRuleService workRules;
	private final AuditService audit;
	private final ApplicationEventPublisher events;

	public LeaveService(LeaveRequestRepository requests, LeaveTypeRepository types,
			LeaveAttachmentRepository attachments, AttachmentStorage storage, EmployeeRepository employees,
			UserAccountRepository accounts, HolidayRepository holidays, WorkRuleService workRules, AuditService audit,
			ApplicationEventPublisher events) {
		this.requests = requests;
		this.types = types;
		this.attachments = attachments;
		this.storage = storage;
		this.employees = employees;
		this.accounts = accounts;
		this.holidays = holidays;
		this.workRules = workRules;
		this.audit = audit;
		this.events = events;
	}

	/** Isi pengajuan yang dicatat di audit (tanpa isi lampiran). */
	record AuditView(Long employeeId, String employeeName, String leaveType, LocalDate startDate, LocalDate endDate,
			String reason, LeaveStatus status, String decisionNote, String cancelReason) {

		static AuditView of(LeaveRequest r) {
			return new AuditView(r.getEmployee().getId(), r.getEmployee().getFullName(), r.getLeaveType().getName(),
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
		return detailed(find(id));
	}

	@Transactional
	public LeaveRequestResponse submitFor(SubmitLeaveRequest request) {
		if (request.employeeId() == null) {
			throw ApiException.badRequest("Karyawan wajib dipilih");
		}
		Employee employee = employees.findById(request.employeeId())
			.orElseThrow(() -> ApiException.notFound("Karyawan"));
		return submit(employee, request);
	}

	@Transactional
	public LeaveRequestResponse approve(Long id, String note) {
		LeaveRequest r = find(id);
		requirePending(r);
		requireNotOwnRequest(r);
		if (r.getLeaveType().isRequiresDocument() && attachments.countByLeaveRequestId(r.getId()) == 0) {
			throw ApiException.badRequest("Jenis \"" + r.getLeaveType().getName()
					+ "\" wajib surat pendukung. Unggah surat sebelum menyetujui");
		}
		AuditView before = AuditView.of(r);
		r.approve(CurrentUser.username(), Instant.now(), trimToNull(note));
		return decided(r, before, "APPROVE", Action.APPROVED, trimToNull(note));
	}

	@Transactional
	public LeaveRequestResponse reject(Long id, String note) {
		LeaveRequest r = find(id);
		requirePending(r);
		requireNotOwnRequest(r);
		String reason = requireText(note, "Alasan penolakan wajib diisi");
		AuditView before = AuditView.of(r);
		r.reject(CurrentUser.username(), Instant.now(), reason);
		return decided(r, before, "REJECT", Action.REJECTED, reason);
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
		return decided(r, before, "CANCEL", Action.CANCELLED, reason);
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
		int pending = requests.findOverlapping(List.of(employeeId), Set.of(LeaveStatus.MENUNGGU), EARLIEST, LATEST)
			.size();
		return new UsageResponse(employeeId, year, rows, pending);
	}

	/** Pengajuan yang menunggu keputusan, untuk lonceng notifikasi (K-081). */
	@Transactional(readOnly = true)
	public PendingSummary pending() {
		List<LeaveRequest> list = requests.findByStatusDetailed(LeaveStatus.MENUNGGU);
		return new PendingSummary(list.size(),
				list.stream().limit(PENDING_LIST_LIMIT).map(PendingItem::from).toList());
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

	@Transactional
	public LeaveRequestResponse submitMine(SubmitLeaveRequest request) {
		return submit(myEmployee(), request);
	}

	/** Karyawan hanya bisa membatalkan pengajuan yang belum diputus (K-074). */
	@Transactional
	public LeaveRequestResponse cancelMine(Long id, String note) {
		LeaveRequest r = findMine(id);
		if (r.getStatus() != LeaveStatus.MENUNGGU) {
			throw ApiException.conflict("Pengajuan yang sudah " + label(r.getStatus())
					+ " hanya bisa dibatalkan oleh Owner atau HR/Finance");
		}
		AuditView before = AuditView.of(r);
		String reason = trimToNull(note);
		r.cancel(CurrentUser.username(), Instant.now(), reason == null ? "Dibatalkan oleh pengaju" : reason);
		return decided(r, before, "CANCEL", Action.CANCELLED, r.getCancelReason());
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

	private LeaveRequestResponse submit(Employee employee, SubmitLeaveRequest request) {
		if (!employee.isActive()) {
			throw ApiException.badRequest("Karyawan tidak aktif");
		}
		LeaveType type = types.findById(request.leaveTypeId()).orElseThrow(() -> ApiException.notFound("Jenis izin"));
		if (!type.isActive()) {
			throw ApiException.badRequest("Jenis izin \"" + type.getName() + "\" sudah tidak aktif");
		}
		if (request.endDate().isBefore(request.startDate())) {
			throw ApiException.badRequest("Tanggal selesai tidak boleh sebelum tanggal mulai");
		}
		if (ChronoUnit.DAYS.between(request.startDate(), request.endDate()) >= MAX_RANGE_DAYS) {
			throw ApiException.badRequest("Rentang izin maksimal " + MAX_RANGE_DAYS + " hari");
		}
		requests.findOverlapping(List.of(employee.getId()), ACTIVE, request.startDate(), request.endDate())
			.stream()
			.findFirst()
			.ifPresent(existing -> {
				throw overlapConflict(existing);
			});

		LeaveRequest r = new LeaveRequest(employee, type, request.startDate(), request.endDate(),
				request.reason().trim(), CurrentUser.username(), Instant.now());
		try {
			r = requests.saveAndFlush(r);
		}
		catch (DataIntegrityViolationException ex) {
			// Pengajuan lain yang beririsan tersimpan bersamaan (K-075).
			if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains(OVERLAP_CONSTRAINT)) {
				throw ApiException.conflict("Tanggal bertabrakan dengan pengajuan lain yang masih aktif");
			}
			throw ex;
		}
		audit.record(CurrentUser.username(), "CREATE", ENTITY, r.getId(), null, AuditView.of(r), null);
		publish(r, Action.SUBMITTED);
		return detailed(r);
	}

	private LeaveRequestResponse decided(LeaveRequest r, AuditView before, String action, Action event,
			String reason) {
		// Flush di sini agar konflik @Version (dua penyetuju bersamaan) muncul sebelum audit tercatat.
		requests.saveAndFlush(r);
		audit.record(CurrentUser.username(), action, ENTITY, r.getId(), before, AuditView.of(r), reason);
		publish(r, event);
		return detailed(r);
	}

	private AttachmentResponse attach(LeaveRequest r, MultipartFile file) {
		if (r.getStatus() != LeaveStatus.MENUNGGU) {
			throw ApiException.conflict("Lampiran hanya bisa ditambah selama pengajuan masih menunggu");
		}
		if (attachments.countByLeaveRequestId(r.getId()) >= MAX_ATTACHMENTS) {
			throw ApiException.badRequest("Maksimal " + MAX_ATTACHMENTS + " lampiran per pengajuan");
		}
		AttachmentStorage.Stored stored = storage.store(file);
		// File sudah ditulis ke disk; hapus lagi bila transaksi yang mencatat metadatanya gagal.
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					storage.delete(stored.key());
				}
			}
		});
		LeaveAttachment saved = attachments.save(new LeaveAttachment(r.getId(), originalName(file),
				stored.contentType(), stored.size(), stored.key(), stored.sha256(), CurrentUser.username()));
		audit.record(CurrentUser.username(), "UPLOAD", ATTACHMENT_ENTITY, saved.getId(), null,
				new AttachmentAuditView(r.getId(), saved.getOriginalName(), saved.getContentType(),
						saved.getSizeBytes(), saved.getSha256()),
				null);
		publish(r, Action.ATTACHMENT_ADDED);
		return AttachmentResponse.from(saved);
	}

	private LeaveRequest find(Long id) {
		return requests.findDetailed(id).orElseThrow(() -> ApiException.notFound("Pengajuan izin"));
	}

	/** Pengajuan karyawan lain dibalas 404 (bukan 403) agar ID tidak bisa ditebak. */
	private LeaveRequest findMine(Long id) {
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

	private void requireNotOwnRequest(LeaveRequest r) {
		Long own = accounts.findByUsername(CurrentUser.username())
			.map(UserAccount::getEmployee)
			.map(Employee::getId)
			.orElse(null);
		if (Objects.equals(own, r.getEmployee().getId())) {
			throw ApiException.forbidden("Pengajuan milik sendiri harus diputus oleh Owner atau HR/Finance lain");
		}
	}

	private static void requirePending(LeaveRequest r) {
		if (r.getStatus() != LeaveStatus.MENUNGGU) {
			throw ApiException.conflict("Pengajuan ini sudah " + label(r.getStatus()) + "; muat ulang halaman");
		}
	}

	private LeaveRequestResponse detailed(LeaveRequest r) {
		Integer working = workingDayCounter(r.getEmployee(), r.getStartDate(), r.getEndDate()).count(r.getStartDate(),
				r.getEndDate());
		return LeaveRequestResponse.from(r, working, attachments.findByLeaveRequestIdOrderByIdAsc(r.getId())
			.stream()
			.map(AttachmentResponse::from)
			.toList());
	}

	private List<LeaveRequestResponse> withAttachments(List<LeaveRequest> rows) {
		if (rows.isEmpty()) {
			return List.of();
		}
		Map<Long, List<AttachmentResponse>> byRequest = attachments
			.findByLeaveRequestIdIn(rows.stream().map(LeaveRequest::getId).toList())
			.stream()
			.sorted((a, b) -> a.getId().compareTo(b.getId()))
			.collect(Collectors.groupingBy(LeaveAttachment::getLeaveRequestId,
					Collectors.mapping(AttachmentResponse::from, Collectors.toList())));
		return rows.stream()
			.map(r -> LeaveRequestResponse.from(r, null, byRequest.getOrDefault(r.getId(), List.of())))
			.toList();
	}

	private void publish(LeaveRequest r, Action action) {
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
			case MENUNGGU -> "menunggu";
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
