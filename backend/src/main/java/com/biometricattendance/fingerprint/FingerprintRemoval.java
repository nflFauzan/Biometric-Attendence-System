package com.biometricattendance.fingerprint;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.audit.AuditService;
import com.biometricattendance.device.Device;
import com.biometricattendance.device.DeviceRepository;
import com.biometricattendance.devicecommand.DeviceCommand;
import com.biometricattendance.devicecommand.DeviceCommandService;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;

/**
 * Penghapusan sidik jari dari server dan dari sensor alat rakitan (spec §17). Catatan jari langsung dihapus; perintah
 * hapus dikirim ke setiap alat rakitan yang pernah tersambung karena server tidak tahu pasti mesin mana yang masih
 * menyimpan jari itu.
 */
@Service
public class FingerprintRemoval {

	private final EmployeeFingerprintRepository fingerprints;
	private final DeviceRepository devices;
	private final DeviceCommandService commands;
	private final AuditService audit;
	private final EmployeeRepository employees;

	FingerprintRemoval(EmployeeFingerprintRepository fingerprints, DeviceRepository devices,
			DeviceCommandService commands, AuditService audit, EmployeeRepository employees) {
		this.fingerprints = fingerprints;
		this.devices = devices;
		this.commands = commands;
		this.audit = audit;
		this.employees = employees;
	}

	@Transactional
	public void deleteFinger(Employee employee, EmployeeFingerprint record, String actor, Instant now) {
		forget(record, actor);
		for (Device device : devices.findConnectedRakitan()) {
			commands.create(DeviceCommand.deleteTemplate(device, employee, employee.getDevicePin(), record.getFinger(),
					actor, now), actor);
		}
	}

	/**
	 * K-103: cabang tambahan dicabut. Batalkan ENROLL aktif di mesin cabang itu, lupakan jari yang direkam di mesin
	 * cabang itu, dan kirim DELETE_TEMPLATE hanya ke mesin tempat jari itu direkam (bukan ke semua mesin).
	 * Mengembalikan jumlah jari yang dilupakan.
	 */
	@Transactional
	public int revokeBranches(Employee employee, Set<Long> revokedBranchIds, String actor, Instant now) {
		if (revokedBranchIds.isEmpty()) {
			return 0;
		}
		commands.cancelActiveEnrollsOnBranches(employee.getId(), revokedBranchIds, actor, now);
		int forgotten = 0;
		for (EmployeeFingerprint record : fingerprints.findByEmployeeIdOrderByFinger(employee.getId())) {
			Device device = record.getDevice();
			if (!revokedBranchIds.contains(device.getBranch().getId())) {
				continue;
			}
			int finger = record.getFinger();
			forget(record, actor);
			if (device.isRakitan()) {
				commands.create(DeviceCommand.deleteTemplate(device, employee, employee.getDevicePin(), finger, actor,
						now), actor);
			}
			forgotten++;
		}
		return forgotten;
	}

	/** Nonaktif, atau tanggal keluar (hari kerja terakhir) sudah lewat menurut zona waktu cabang. */
	public boolean isDeparted(Employee employee, Instant now) {
		if (!employee.isActive()) {
			return true;
		}
		LocalDate end = employee.getEndDate();
		return end != null && end.isBefore(LocalDate.ofInstant(now, ZoneId.of(employee.getBranch().getTimezone())));
	}

	/**
	 * Karyawan keluar/nonaktif (K-090): batalkan ENROLL aktif, hapus semua catatan jari, lalu kirim DELETE_EMPLOYEE
	 * ke setiap alat rakitan jika karyawan pernah menyetujui (hanya mereka yang mungkin pernah didaftarkan).
	 */
	@Transactional
	public void removeAll(Employee employee, String actor, Instant now) {
		commands.cancelActiveEnrolls(employee.getId(), actor, now);
		for (EmployeeFingerprint record : fingerprints.findByEmployeeIdOrderByFinger(employee.getId())) {
			forget(record, actor);
		}
		if (employee.getDevicePin() != null && employee.getFingerprintConsentAt() != null) {
			for (Device device : devices.findConnectedRakitan()) {
				commands.create(DeviceCommand.deleteEmployee(device, employee, employee.getDevicePin(), actor, now),
						actor);
			}
		}
		employee.markFingerprintsRemoved(now);
	}

	/** Pemeriksaan terjadwal untuk tanggal keluar yang tiba belakangan; mengembalikan jumlah karyawan yang diproses. */
	@Transactional
	public int sweepDepartures(Instant now) {
		LocalDate maxEndDate = LocalDate.ofInstant(now, ZoneOffset.UTC).plusDays(1);
		int removed = 0;
		for (Employee employee : employees.findDepartureCandidates(maxEndDate)) {
			if (isDeparted(employee, now)) {
				removeAll(employee, DeviceCommandService.SYSTEM_ACTOR, now);
				removed++;
			}
		}
		return removed;
	}

	private void forget(EmployeeFingerprint record, String actor) {
		audit.record(actor, "FINGERPRINT_DELETED", "EmployeeFingerprint", record.getId(), record.snapshot(), null,
				null);
		fingerprints.delete(record);
	}

}
