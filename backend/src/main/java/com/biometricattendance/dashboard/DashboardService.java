package com.biometricattendance.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.attendance.AttendanceDtos.DailyRow;
import com.biometricattendance.attendance.AttendanceScan;
import com.biometricattendance.attendance.AttendanceScanRepository;
import com.biometricattendance.attendance.AttendanceService;
import com.biometricattendance.attendance.HeldScanService;
import com.biometricattendance.dashboard.DashboardDtos.BranchSummary;
import com.biometricattendance.dashboard.DashboardDtos.Counts;
import com.biometricattendance.dashboard.DashboardDtos.DashboardSummary;
import com.biometricattendance.dashboard.DashboardDtos.RecentScan;
import com.biometricattendance.device.DeviceService;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;
import com.biometricattendance.leave.LeaveRequestRepository;
import com.biometricattendance.leave.LeaveStatuses;

/** Ringkasan dashboard untuk satu tanggal (bawaan hari ini menurut zona waktu perusahaan). */
@Service
public class DashboardService {

	static final int RECENT_LIMIT = 15;

	private final AttendanceService attendance;
	private final DeviceService devices;
	private final AttendanceScanRepository scans;
	private final EmployeeRepository employees;
	private final LeaveRequestRepository leaves;
	private final HeldScanService heldScans;
	private final ZoneId companyZone;

	public DashboardService(AttendanceService attendance, DeviceService devices, AttendanceScanRepository scans,
			EmployeeRepository employees, LeaveRequestRepository leaves, HeldScanService heldScans,
			@Value("${app.company-timezone}") String companyZone) {
		this.attendance = attendance;
		this.devices = devices;
		this.scans = scans;
		this.employees = employees;
		this.leaves = leaves;
		this.heldScans = heldScans;
		this.companyZone = ZoneId.of(companyZone);
	}

	@Transactional(readOnly = true)
	public DashboardSummary summary(LocalDate requested) {
		LocalDate date = requested == null ? attendance.today() : requested;
		List<DailyRow> rows = attendance.daily(date, null).rows();

		Map<Long, List<DailyRow>> byBranch = rows.stream()
			.sorted(Comparator.comparing(DailyRow::branchName))
			.collect(Collectors.groupingBy(DailyRow::branchId, LinkedHashMap::new, Collectors.toList()));
		List<BranchSummary> branches = byBranch.values()
			.stream()
			.map(list -> new BranchSummary(list.get(0).branchId(), list.get(0).branchName(), Counts.of(list)))
			.toList();

		Instant start = date.atStartOfDay(companyZone).toInstant();
		Instant end = date.plusDays(1).atStartOfDay(companyZone).toInstant();
		List<AttendanceScan> recent = scans.findRecent(start, PageRequest.of(0, RECENT_LIMIT * 4))
			.stream()
			.filter(s -> s.getScannedAt().isBefore(end))
			.limit(RECENT_LIMIT)
			.toList();
		Map<Long, String> names = employees
			.findAllById(recent.stream().map(AttendanceScan::getEmployeeId).filter(id -> id != null).distinct().toList())
			.stream()
			.collect(Collectors.toMap(Employee::getId, Employee::getFullName));
		List<RecentScan> recentScans = recent.stream()
			.map(s -> new RecentScan(s.getId(), s.getScannedAt(), s.getDevicePin(), s.getEmployeeId(),
					s.getEmployeeId() == null ? null : names.get(s.getEmployeeId()), s.getDevice().getName(),
					s.getFlags()))
			.toList();

		return new DashboardSummary(date, Counts.of(rows), branches, devices.list(), recentScans,
				scans.countUnmatched(start, end), heldScans.countPending(), leaves.countByStatusIn(LeaveStatuses.PENDING));
	}

}
