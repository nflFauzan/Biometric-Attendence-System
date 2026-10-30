package com.biometricattendance.attendance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.attendance.AttendanceDtos.DayRecord;
import com.biometricattendance.attendance.AttendanceDtos.EmployeeDays;
import com.biometricattendance.attendance.AttendanceDtos.ReviewItem;
import com.biometricattendance.calculation.CalculationDtos.DailyResultResponse;
import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.calculation.DailyResult.Flag;

/**
 * Daftar "Perlu Ditinjau" (FR-REV-001): menyaring rekap harian yang sudah ada, tanpa tabel baru. Hari keluar dari
 * daftar dengan sendirinya setelah koreksi atau keputusan scan tertahan mengubah hasil rekap.
 */
@Service
public class ReviewQueueService {

	static final String INCOMPLETE = "Tidak lengkap";
	static final String ODD_ORDER = "Urutan jam tidak wajar";
	static final String PRESENT_ON_LEAVE = "Hadir saat izin";
	static final String PRESENT_ON_HOLIDAY = "Hadir di hari libur";
	static final String HELD_SCAN = "Ada scan tertahan";
	static final String RULE_MISSING = "Aturan kerja belum diatur";

	private final AttendanceService attendance;

	public ReviewQueueService(AttendanceService attendance) {
		this.attendance = attendance;
	}

	/** Terbaru lebih dulu, lalu nama karyawan. */
	@Transactional(readOnly = true)
	public List<ReviewItem> list(LocalDate from, LocalDate to, Long branchId, Long categoryId) {
		List<ReviewItem> items = new ArrayList<>();
		for (EmployeeDays employee : attendance.range(from, to, branchId, categoryId, null)) {
			for (DayRecord day : employee.days()) {
				// Hari sebelum tanggal masuk atau setelah tanggal keluar tidak perlu ditinjau.
				if (!employee.employed(day.date())) {
					continue;
				}
				List<String> reasons = reasons(day);
				if (reasons.isEmpty()) {
					continue;
				}
				DailyResultResponse result = day.result();
				items.add(new ReviewItem(employee.employeeId(), employee.employeeNumber(), employee.fullName(),
						employee.branchName(), employee.categoryName(), day.date(),
						result == null ? null : result.status(), result == null ? Set.of() : result.flags(),
						day.ruleMissing() != null, reasons));
			}
		}
		items.sort(Comparator.comparing(ReviewItem::date).reversed().thenComparing(ReviewItem::fullName));
		return items;
	}

	static List<String> reasons(DayRecord day) {
		List<String> reasons = new ArrayList<>();
		DailyResultResponse result = day.result();
		if (result != null) {
			Set<Flag> flags = result.flags();
			if (result.status() == AttendanceStatus.TIDAK_LENGKAP) {
				reasons.add(INCOMPLETE);
			}
			if (flags.contains(Flag.URUTAN_TIDAK_WAJAR)) {
				reasons.add(ODD_ORDER);
			}
			if (flags.contains(Flag.HADIR_SAAT_IZIN)) {
				reasons.add(PRESENT_ON_LEAVE);
			}
			if (flags.contains(Flag.PERLU_TINJAUAN_HARI_LIBUR)) {
				reasons.add(PRESENT_ON_HOLIDAY);
			}
			if (flags.contains(Flag.ADA_SCAN_TERTAHAN)) {
				reasons.add(HELD_SCAN);
			}
		}
		if (day.ruleMissing() != null) {
			reasons.add(RULE_MISSING);
		}
		return reasons;
	}

}
