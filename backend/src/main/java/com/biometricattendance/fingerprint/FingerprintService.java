package com.biometricattendance.fingerprint;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.biometricattendance.audit.AuditService;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.device.Device;
import com.biometricattendance.device.DeviceRepository;
import com.biometricattendance.device.DeviceStatus;
import com.biometricattendance.devicecommand.DeviceCommand;
import com.biometricattendance.devicecommand.DeviceCommandDtos.CommandResponse;
import com.biometricattendance.devicecommand.DeviceCommandRepository;
import com.biometricattendance.devicecommand.DeviceCommandMessages;
import com.biometricattendance.devicecommand.DeviceCommandService;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;
import com.biometricattendance.fingerprint.FingerprintDtos.DeletionResponse;
import com.biometricattendance.fingerprint.FingerprintDtos.EnrollRequest;
import com.biometricattendance.fingerprint.FingerprintDtos.FingerResponse;
import com.biometricattendance.fingerprint.FingerprintDtos.FingerprintOverview;

/**
 * Persetujuan (K-091) dan pembuatan perintah ENROLL (spec §3.2, §16). Satu ENROLL aktif per mesin dan per karyawan +
 * jari (K-094) diperiksa di sini dan dijaga unique index di database untuk klik bersamaan.
 */
@Service
public class FingerprintService {

	static final String DEVICE_BUSY = "Mesin sedang dipakai untuk pendaftaran.";
	static final String FINGER_BUSY = "Jari ini sedang didaftarkan.";
	private static final String DEVICE_INDEX = "ux_device_commands_enroll_device";

	private final EmployeeRepository employees;
	private final DeviceRepository devices;
	private final DeviceStatus deviceStatus;
	private final DeviceCommandRepository commands;
	private final DeviceCommandService commandService;
	private final EmployeeFingerprintRepository fingerprints;
	private final AuditService audit;
	private final FingerprintRemoval removal;

	public FingerprintService(EmployeeRepository employees, DeviceRepository devices, DeviceStatus deviceStatus,
			DeviceCommandRepository commands, DeviceCommandService commandService,
			EmployeeFingerprintRepository fingerprints, AuditService audit, FingerprintRemoval removal) {
		this.employees = employees;
		this.devices = devices;
		this.deviceStatus = deviceStatus;
		this.commands = commands;
		this.commandService = commandService;
		this.fingerprints = fingerprints;
		this.audit = audit;
		this.removal = removal;
	}

	@Transactional(readOnly = true)
	public FingerprintOverview overview(Long employeeId) {
		return overview(employee(employeeId));
	}

	/** Sekali per karyawan; centang berikutnya tidak mengubah siapa dan kapan. */
	@Transactional
	public FingerprintOverview consent(Long employeeId) {
		Employee employee = employee(employeeId);
		if (employee.getFingerprintConsentAt() == null) {
			// Presisi mikrodetik seperti kolom TIMESTAMPTZ, agar nilai yang dibalas sama dengan yang tersimpan.
			Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
			employee.recordFingerprintConsent(CurrentUser.username(), now);
			audit.record("FINGERPRINT_CONSENT", "Employee", employeeId, null,
					Map.of("consentBy", employee.getFingerprintConsentBy(), "consentAt", now.toString()));
		}
		return overview(employee);
	}

	@Transactional
	public CommandResponse startEnroll(Long employeeId, EnrollRequest request) {
		Employee employee = employee(employeeId);
		if (removal.isDeparted(employee, Instant.now())) {
			throw ApiException.badRequest("Karyawan nonaktif tidak bisa didaftarkan sidik jarinya.");
		}
		if (!StringUtils.hasText(employee.getDevicePin())) {
			throw ApiException.badRequest("Isi PIN mesin karyawan dulu.");
		}
		if (employee.getFingerprintConsentAt() == null) {
			throw ApiException.badRequest("Centang persetujuan karyawan dulu.");
		}
		Device device = devices.findWithBranch(request.deviceId()).orElseThrow(() -> ApiException.notFound("Mesin"));
		if (!employee.isAllowedAt(device.getBranch().getId())) {
			throw ApiException.badRequest("Karyawan tidak diizinkan absen di cabang mesin ini.");
		}
		Instant now = Instant.now();
		if (!device.isRakitan()) {
			throw ApiException.badRequest("Mesin ini tidak mendukung pendaftaran dari web; daftarkan langsung di mesin.");
		}
		if (!device.isActive()) {
			throw ApiException.badRequest("Mesin nonaktif.");
		}
		if (!deviceStatus.isOnline(device, now)) {
			throw ApiException.badRequest("Mesin sedang offline.");
		}
		// Perintah lama yang sudah lewat batas waktu tidak boleh menghalangi pendaftaran baru.
		commandService.expireStale(now);
		if (commands.existsActiveEnrollForDevice(device.getId())) {
			throw ApiException.conflict(DEVICE_BUSY);
		}
		// Ganti jaringan atau kunci sedang berjalan: pendaftaran bisa terputus di tengah (spec §18.4).
		if (commands.existsActiveRisky(device.getId())) {
			throw ApiException.conflict(DeviceCommandService.RISKY_BUSY);
		}
		if (commands.existsUnfinishedDeleteOnDevice(device.getId(), employee.getDevicePin())) {
			throw ApiException.conflict("Tunggu sampai sidik jari lama selesai dihapus di mesin ini.");
		}
		if (commands.existsActiveEnroll(employeeId, request.finger())) {
			throw ApiException.conflict(FINGER_BUSY);
		}
		DeviceCommand command = DeviceCommand.enroll(device, employee, request.finger(), CurrentUser.username(), now);
		try {
			commandService.create(command);
		}
		catch (DataIntegrityViolationException ex) {
			String cause = String.valueOf(ex.getMostSpecificCause().getMessage());
			throw ApiException.conflict(cause.contains(DEVICE_INDEX) ? DEVICE_BUSY : FINGER_BUSY);
		}
		return CommandResponse.from(command);
	}

	/** Tombol "Hapus" per jari (spec §17.3). */
	@Transactional
	public FingerprintOverview deleteFinger(Long employeeId, int finger) {
		Employee employee = employee(employeeId);
		EmployeeFingerprint record = fingerprints.findByEmployeeIdAndFinger(employeeId, finger)
			.orElseThrow(() -> ApiException.notFound("Sidik jari"));
		if (commands.existsActiveEnroll(employeeId, finger)) {
			throw ApiException.conflict(FINGER_BUSY);
		}
		removal.deleteFinger(employee, record, CurrentUser.username(), Instant.now());
		return overview(employee);
	}

	private FingerprintOverview overview(Employee employee) {
		return new FingerprintOverview(employee.getFingerprintConsentAt(), employee.getFingerprintConsentBy(),
				fingerprints.findByEmployeeIdOrderByFinger(employee.getId())
					.stream()
					.map(f -> new FingerResponse(f.getFinger(), f.getDevice().getId(), f.getDevice().getName(),
							f.getEnrolledAt()))
					.toList(),
				commands.findActiveForEmployee(employee.getId()).stream().map(CommandResponse::from).toList(),
				commands.findUnfinishedDeletesForEmployee(employee.getId()).stream().map(this::deletion).toList(),
				devices.existsByProtocolAndActiveTrue(Device.PROTOCOL_ZK_PUSH));
	}

	private DeletionResponse deletion(DeviceCommand command) {
		Device device = command.getDevice();
		String message = DeviceCommandMessages.message(command);
		if (command.isActive() && !device.isActive()) {
			message = "Menunggu (mesin nonaktif)";
		}
		else if (command.isActive() && !deviceStatus.isOnline(device, Instant.now())) {
			message = "Menunggu (mesin offline)";
		}
		return new DeletionResponse(command.getId(), command.getType(), command.getFinger(), device.getId(),
				device.getName(), command.getStatus(), message, DeviceCommand.GAGAL.equals(command.getStatus()));
	}

	private Employee employee(Long id) {
		return employees.findById(id).orElseThrow(() -> ApiException.notFound("Karyawan"));
	}

}
