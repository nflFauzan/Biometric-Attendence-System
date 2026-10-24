package com.biometricattendance.leave;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.account.UserAccount;
import com.biometricattendance.account.UserAccountRepository;
import com.biometricattendance.audit.AuditService;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.leave.LeaveDtos.PublicApproval;
import com.biometricattendance.leave.LeaveDtos.PublicLetterResponse;
import com.biometricattendance.leave.letter.LeaveLetterRenderer;
import com.biometricattendance.leave.letter.LetterModel;
import com.biometricattendance.leave.letter.LetterModel.Field;
import com.biometricattendance.leave.letter.LetterModel.SignatureBox;
import com.biometricattendance.signature.SignatureService;

/**
 * Surat PDF izin/cuti dan verifikasi publiknya (spec 2026-10-24 bagian 8). Hak akses pengajuan (milik sendiri atau
 * pengelola, draft hanya untuk pemilik) memakai pencarian yang sama dengan {@link LeaveService}.
 */
@Service
public class LeaveLetterService {

	static final String ENTITY = "LEAVE_LETTER";
	private static final Pattern TOKEN = Pattern.compile("[0-9a-f]{32}");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH.mm");

	private final LeaveService leave;
	private final LeaveRequestRepository requests;
	private final LeaveApprovalRepository approvals;
	private final UserAccountRepository accounts;
	private final LeaveAttachmentRepository attachments;
	private final SignatureService signatures;
	private final LeaveLetterRenderer renderer;
	private final LetterLinks links;
	private final AuditService audit;
	private final ZoneId companyZone;

	public LeaveLetterService(LeaveService leave, LeaveRequestRepository requests, LeaveApprovalRepository approvals,
			UserAccountRepository accounts, LeaveAttachmentRepository attachments, SignatureService signatures, LeaveLetterRenderer renderer,
			LetterLinks links, AuditService audit, @Value("${app.company-timezone}") String companyZone) {
		this.leave = leave;
		this.requests = requests;
		this.approvals = approvals;
		this.accounts = accounts;
		this.attachments = attachments;
		this.signatures = signatures;
		this.renderer = renderer;
		this.links = links;
		this.audit = audit;
		this.companyZone = ZoneId.of(companyZone);
	}

	/** PDF beserta nama berkas unduhan. */
	public record LetterFile(String filename, byte[] pdf) {
	}

	/** Isi audit unduhan surat oleh pengelola. */
	record LetterAuditView(String letterNumber, LeaveStatus status) {
	}

	/** Surat milik sendiri (draft boleh); pengajuan karyawan lain dibalas 404 dan tidak diaudit. */
	@Transactional(readOnly = true)
	public LetterFile mine(Long id) {
		return build(leave.findMine(id));
	}

	/** Surat untuk pengelola (draft 404); setiap unduhan diaudit sebagai VIEW. */
	@Transactional
	public LetterFile forManager(Long id) {
		LeaveRequest r = leave.find(id);
		audit.record(CurrentUser.username(), "VIEW", ENTITY, r.getId(), null,
				new LetterAuditView(number(r), r.getStatus()), null);
		return build(r);
	}

	/** Verifikasi publik: token tidak dikenal atau milik draft dibalas 404 dengan pesan yang sama. */
	@Transactional(readOnly = true)
	public PublicLetterResponse verify(String token) {
		if (token == null || !TOKEN.matcher(token).matches()) {
			throw notFound();
		}
		LeaveRequest r = requests.findPublicByToken(token).orElseThrow(LeaveLetterService::notFound);
		List<LeaveApproval> history = approvals.findByLeaveRequestIdOrderByIdAsc(r.getId());
		Map<String, String> names = linkedEmployeeNames(history);
		List<PublicApproval> rows = history.stream()
			.map(a -> new PublicApproval(a.getStage(), a.isApproved(), publicName(a, names), a.getDecidedAt(),
					a.getMethod()))
			.toList();
		return new PublicLetterResponse(number(r), r.getEmployee().getFullName(), r.getEmployee().getBranch().getName(),
				r.getLeaveType() == null ? null : r.getLeaveType().getName(), r.getStartDate(), r.getEndDate(),
				r.getStatus(), rows);
	}

	/** Nama karyawan yang terhubung ke akun pemberi keputusan; akun tanpa karyawan tidak ada di peta ini. */
	private Map<String, String> linkedEmployeeNames(List<LeaveApproval> history) {
		Set<String> usernames = history.stream()
			.map(LeaveApproval::getDecidedBy)
			.filter(u -> u != null && !LeaveApproval.OWNER_ACTOR.equals(u))
			.collect(Collectors.toSet());
		Map<String, String> names = new HashMap<>();
		if (!usernames.isEmpty()) {
			for (UserAccount account : accounts.findByUsernameInWithEmployee(usernames)) {
				if (account.getEmployee() != null) {
					names.put(account.getUsername(), account.getEmployee().getFullName());
				}
			}
		}
		return names;
	}

	/** Halaman publik tidak boleh membocorkan username login: nama karyawan, atau label tahap bila tidak ada. */
	private static String publicName(LeaveApproval a, Map<String, String> names) {
		if (LeaveApproval.OWNER_ACTOR.equals(a.getDecidedBy())) {
			return "Owner";
		}
		String name = names.get(a.getDecidedBy());
		if (name != null) {
			return name;
		}
		return a.getStage() == ApprovalStage.OWNER ? "Owner" : "HR/Finance";
	}

	private static ApiException notFound() {
		return ApiException.notFound("Surat");
	}

	static String number(LeaveRequest r) {
		return String.format("IZ-%06d", r.getId());
	}

	// ---- Model surat ----

	private LetterFile build(LeaveRequest r) {
		String number = number(r);
		return new LetterFile(number + ".pdf", renderer.render(model(r, number)));
	}

	private LetterModel model(LeaveRequest r, String number) {
		Employee employee = r.getEmployee();
		LetterTemplate template = r.getLetterTemplate() == null ? LetterTemplate.UMUM : r.getLetterTemplate();
		List<LeaveApproval> history = approvals.findByLeaveRequestIdOrderByIdAsc(r.getId());
		Map<String, String> names = linkedEmployeeNames(history);
		List<String> attachmentNames = attachments.findByLeaveRequestIdOrderByIdAsc(r.getId())
			.stream()
			.map(LeaveAttachment::getOriginalName)
			.toList();
		String verifyUrl = r.getStatus() == LeaveStatus.DRAFT || r.getVerificationToken() == null ? null
				: links.verifyUrl(r.getVerificationToken());
		return new LetterModel(number, employee.getBranch().getName(), title(template), employee.getFullName(),
				employee.getEmployeeNumber(), employee.getPosition(), intro(r), fields(r), attachmentNames,
				boxes(r, history, names), stamp(r.getStatus()), verifyUrl,
				DATE_TIME.format(Instant.now().atZone(companyZone)) + " " + companyZone.getId());
	}

	private static String title(LetterTemplate template) {
		return switch (template) {
			case CUTI -> "SURAT PERMOHONAN CUTI";
			case SAKIT -> "SURAT KETERANGAN SAKIT";
			case IZIN -> "SURAT PERMOHONAN IZIN";
			case UMUM -> "SURAT PERMOHONAN IZIN / CUTI";
		};
	}

	private static String stamp(LeaveStatus status) {
		return switch (status) {
			case DRAFT -> "DRAFT";
			case PERLU_TINJAU_HR, PERLU_TINJAU_OWNER -> "MENUNGGU PERSETUJUAN";
			case DITOLAK -> "DITOLAK";
			case DIBATALKAN -> "DIBATALKAN";
			case DISETUJUI -> null;
		};
	}

	private static String intro(LeaveRequest r) {
		String type = r.getLeaveType() == null ? "izin/cuti" : r.getLeaveType().getName();
		LocalDate start = r.getStartDate();
		LocalDate end = r.getEndDate();
		if (start == null || end == null) {
			return "Dengan ini mengajukan " + type + " (tanggal belum diisi).";
		}
		long days = r.calendarDays();
		if (start.equals(end)) {
			return "Dengan ini mengajukan " + type + " pada tanggal " + DATE.format(start) + " (" + days + " hari).";
		}
		return "Dengan ini mengajukan " + type + " mulai tanggal " + DATE.format(start) + " sampai dengan "
				+ DATE.format(end) + " (" + days + " hari).";
	}

	private static List<Field> fields(LeaveRequest r) {
		List<Field> fields = new ArrayList<>();
		add(fields, "Keperluan", r.getReason());
		add(fields, "No. HP", r.getContactPhone());
		add(fields, "Alamat selama cuti", r.getLeaveAddress());
		add(fields, "Tugas dilimpahkan kepada", r.getDelegateTo());
		add(fields, "Faskes / dokter", r.getHealthFacility());
		return fields;
	}

	private static void add(List<Field> fields, String label, String value) {
		if (value != null && !value.isBlank()) {
			fields.add(new Field(label, value.trim()));
		}
	}

	private List<SignatureBox> boxes(LeaveRequest r, List<LeaveApproval> history, Map<String, String> names) {
		List<SignatureBox> boxes = new ArrayList<>();
		boxes.add(new SignatureBox("Pemohon", imageOrNull(r.getApplicantSignatureKey()), r.getEmployee().getFullName(),
				null));
		if (Boolean.TRUE.equals(r.getRequiresOwnerApproval())) {
			boxes.add(approvalBox("HR/Finance", stageOf(history, ApprovalStage.HR), names));
			boxes.add(approvalBox("Owner", stageOf(history, ApprovalStage.OWNER), names));
		}
		else {
			boxes.add(approvalBox("Disetujui oleh", history.isEmpty() ? null : history.get(history.size() - 1), names));
		}
		return boxes;
	}

	private static LeaveApproval stageOf(List<LeaveApproval> history, ApprovalStage stage) {
		return history.stream().filter(a -> a.getStage() == stage).findFirst().orElse(null);
	}

	/** Tahap yang belum diputus dibiarkan kosong (hanya judul). */
	private SignatureBox approvalBox(String title, LeaveApproval a, Map<String, String> names) {
		if (a == null) {
			return new SignatureBox(title, null, null, null);
		}
		String name = publicName(a, names);
		String when = DATE_TIME.format(a.getDecidedAt().atZone(companyZone));
		if (a.getMethod() == null || a.getMethod() == DecisionMethod.WEB) {
			if (!a.isApproved()) {
				return new SignatureBox(title, null, name, "Ditolak\n" + when);
			}
			byte[] image = imageOrNull(a.getSignatureStorageKey());
			return new SignatureBox(title, image, name,
					(image == null ? "disetujui secara elektronik\n" : "") + when);
		}
		// Keputusan di luar web dicatat HR: ditulis sebagai teks, bukan gambar tanda tangan.
		String verdict = a.isApproved() ? "Disetujui" : "Ditolak";
		String recorder = a.getRecordedBy() == null ? "" : ", dicatat oleh " + names.getOrDefault(a.getRecordedBy(), "HR/Finance");
		return new SignatureBox(title, null, name,
				verdict + " " + methodWords(a.getMethod()) + " pada " + when + recorder);
	}

	private static String methodWords(DecisionMethod method) {
		return switch (method) {
			case WHATSAPP -> "melalui WhatsApp";
			case LISAN -> "secara lisan";
			case TERTULIS -> "secara tertulis";
			case WEB -> throw new IllegalStateException("Keputusan lewat web ditulis sebagai tanda tangan");
		};
	}

	/** Spesimen beku; berkas yang hilang dari volume dianggap tanpa spesimen (surat tetap bisa dibuat). */
	private byte[] imageOrNull(String key) {
		if (key == null) {
			return null;
		}
		try {
			return signatures.load(key);
		}
		catch (ApiException ex) {
			if (ex.getStatus() == HttpStatus.NOT_FOUND) {
				return null;
			}
			throw ex;
		}
	}

}
