package com.biometricattendance.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.attendance.AttendanceDtos.HeldScanResponse;
import com.biometricattendance.attendance.AttendanceDtos.ReviewRequest;
import com.biometricattendance.attendance.AttendanceDtos.ReviewStatus;
import com.biometricattendance.attendance.AttendanceDtos.ScanResponse;
import com.biometricattendance.audit.AuditService;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;

/**
 * Peninjauan scan yang tertahan karena cabang (K-103). Scan tertahan tidak masuk rekap sampai Owner/HR
 * menerimanya; keputusan boleh diganti. Scan asli tidak pernah diubah, keputusan disimpan di {@code scan_reviews}
 * dan setiap perubahan dicatat di audit pada transaksi yang sama.
 */
@Service
public class HeldScanService {

	static final String ENTITY = "AttendanceScan";
	static final String ACTION = "SCAN_REVIEW";
	static final String CONFLICT = "Scan ini baru saja ditinjau pengguna lain. Muat ulang halaman.";

	private final AttendanceScanRepository scans;
	private final ScanReviewRepository reviews;
	private final EmployeeRepository employees;
	private final AuditService audit;
	private final ApplicationEventPublisher events;
	private final ZoneId companyZone;

	public HeldScanService(AttendanceScanRepository scans, ScanReviewRepository reviews, EmployeeRepository employees,
			AuditService audit, ApplicationEventPublisher events, @Value("${app.company-timezone}") String companyZone) {
		this.scans = scans;
		this.reviews = reviews;
		this.employees = employees;
		this.audit = audit;
		this.events = events;
		this.companyZone = ZoneId.of(companyZone);
	}

	/** Isi keputusan yang dicatat di audit. */
	record ReviewSnapshot(String decision, String reason, String decidedBy, Instant decidedAt) {

		static ReviewSnapshot of(ScanReview r) {
			return r == null ? null
					: new ReviewSnapshot(r.getDecision().name(), r.getReason(), r.getDecidedBy(), r.getDecidedAt());
		}

	}

	/** Scan tertahan dalam rentang tanggal (zona waktu perusahaan, seperti scan tak dikenal), terbaru dulu. */
	@Transactional(readOnly = true)
	public List<HeldScanResponse> list(LocalDate from, LocalDate to, ReviewStatus status) {
		AttendanceService.requireRange(from, to);
		List<AttendanceScan> held = scans.findHeldFlagged(from.atStartOfDay(companyZone).toInstant(),
				to.plusDays(1).atStartOfDay(companyZone).toInstant());
		Map<Long, ScanReview> byScan = reviewsOf(held);
		List<AttendanceScan> filtered = held.stream()
			.filter(s -> status == null || statusOf(byScan.get(s.getId())) == status)
			.toList();
		return toResponses(filtered, byScan);
	}

	/**
	 * Menetapkan keputusan untuk semua scan, atau tidak sama sekali: seluruh batch diperiksa dulu. Dua peninjau
	 * pada scan yang sama menghasilkan satu sukses dan satu 409.
	 */
	@Transactional
	public List<HeldScanResponse> review(ReviewRequest request) {
		String reason = request.reason() == null ? "" : request.reason().trim();
		if (reason.isEmpty()) {
			throw ApiException.badRequest("Alasan wajib diisi.");
		}
		List<Long> ids = new ArrayList<>(new LinkedHashSet<>(request.scanIds()));
		Map<Long, AttendanceScan> found = new HashMap<>();
		scans.findAllById(ids).forEach(s -> found.put(s.getId(), s));
		List<AttendanceScan> ordered = new ArrayList<>();
		for (Long id : ids) {
			AttendanceScan scan = found.get(id);
			if (scan == null) {
				throw ApiException.notFound("Scan");
			}
			ordered.add(scan);
		}
		for (AttendanceScan scan : ordered) {
			if (!scan.getFlags().contains(ScanFlag.CABANG_TIDAK_DIIZINKAN)) {
				throw ApiException.badRequest("Scan ini tidak tertahan karena cabang.");
			}
		}

		Map<Long, ScanReview> existing = reviewsOf(ordered);
		ScanReview.Decision decision = ScanReview.Decision.valueOf(request.decision().name());
		String actor = CurrentUser.username();
		Instant now = Instant.now();
		Map<Long, ReviewSnapshot> before = new HashMap<>();
		try {
			for (AttendanceScan scan : ordered) {
				ScanReview current = existing.get(scan.getId());
				before.put(scan.getId(), ReviewSnapshot.of(current));
				if (current == null) {
					existing.put(scan.getId(), reviews.save(new ScanReview(scan.getId(), decision, reason, actor, now)));
				}
				else {
					current.decide(decision, reason, actor, now);
				}
			}
			// Flush di sini agar konflik (@Version atau primary key) muncul di dalam request dan sebelum audit.
			reviews.flush();
		}
		catch (OptimisticLockingFailureException | DataIntegrityViolationException ex) {
			throw ApiException.conflict(CONFLICT);
		}

		Set<String> notified = new LinkedHashSet<>();
		Map<Long, Employee> owners = employeesOf(ordered);
		for (AttendanceScan scan : ordered) {
			ScanReview saved = existing.get(scan.getId());
			audit.record(actor, ACTION, ENTITY, scan.getId(), before.get(scan.getId()), ReviewSnapshot.of(saved), reason);
			Employee employee = owners.get(scan.getEmployeeId());
			LocalDate date = scan.getScannedAt().atZone(ZoneId.of(employee.getBranch().getTimezone())).toLocalDate();
			if (notified.add(employee.getId() + "/" + date)) {
				events.publishEvent(new AttendanceChangedEvent(employee.getId(), date));
			}
		}
		return toResponses(ordered, existing);
	}

	/** Jumlah scan tertahan yang belum diputus sama sekali (dashboard). */
	@Transactional(readOnly = true)
	public long countPending() {
		return scans.countPendingHeld();
	}

	private Map<Long, ScanReview> reviewsOf(List<AttendanceScan> list) {
		Map<Long, ScanReview> map = new LinkedHashMap<>();
		if (!list.isEmpty()) {
			reviews.findByScanIdIn(list.stream().map(AttendanceScan::getId).toList())
				.forEach(r -> map.put(r.getScanId(), r));
		}
		return map;
	}

	private Map<Long, Employee> employeesOf(List<AttendanceScan> list) {
		Map<Long, Employee> map = new HashMap<>();
		employees.findAllById(list.stream().map(AttendanceScan::getEmployeeId).distinct().toList())
			.forEach(e -> map.put(e.getId(), e));
		return map;
	}

	static ReviewStatus statusOf(ScanReview review) {
		return review == null ? ReviewStatus.BELUM_DITINJAU : ReviewStatus.valueOf(review.getDecision().name());
	}

	private List<HeldScanResponse> toResponses(List<AttendanceScan> list, Map<Long, ScanReview> byScan) {
		Map<Long, Employee> owners = employeesOf(list);
		return list.stream().map(scan -> {
			ScanReview review = byScan.get(scan.getId());
			ReviewStatus status = statusOf(review);
			Employee employee = owners.get(scan.getEmployeeId());
			return new HeldScanResponse(ScanResponse.from(scan, status), employee.getFullName(),
					employee.getBranch().getName(), scan.getDevice().getBranch().getName(), status,
					review == null ? null : review.getReason(), review == null ? null : review.getDecidedBy(),
					review == null ? null : review.getDecidedAt());
		}).toList();
	}

}
