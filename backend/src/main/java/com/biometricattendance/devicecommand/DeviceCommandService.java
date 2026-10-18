package com.biometricattendance.devicecommand;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.audit.AuditService;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.device.Device;
import com.biometricattendance.devicecommand.DeviceCommandDtos.CommandResponse;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;

/**
 * Siklus hidup perintah ke alat rakitan (spec §4, §16). Batas waktu adalah parameter teknis (K-094), bukan aturan
 * bisnis: perintah yang tidak diambil mesin kedaluwarsa, dan perintah yang diambil tanpa hasil dianggap gagal.
 */
@Service
public class DeviceCommandService {

	static final String SYSTEM_ACTOR = "sistem";
	private static final String JARI_SUDAH_TERDAFTAR = "JARI_SUDAH_TERDAFTAR";

	private final DeviceCommandRepository commands;
	private final AuditService audit;
	private final ApplicationEventPublisher events;
	private final Duration pickupTimeout;
	private final Duration resultTimeout;
	private final Duration cancelNotice;
	private final EmployeeRepository employees;

	public DeviceCommandService(DeviceCommandRepository commands, AuditService audit,
			ApplicationEventPublisher events, EmployeeRepository employees,
			@Value("${app.device.enroll.pickup-timeout:30s}") Duration pickupTimeout,
			@Value("${app.device.enroll.result-timeout:75s}") Duration resultTimeout,
			@Value("${app.device.cancel-notice:2m}") Duration cancelNotice) {
		this.commands = commands;
		this.audit = audit;
		this.events = events;
		this.employees = employees;
		this.pickupTimeout = pickupTimeout;
		this.resultTimeout = resultTimeout;
		this.cancelNotice = cancelNotice;
	}

	/** Isi balasan heartbeat: perintah aktif mesin ini dan perintah terkirim yang baru dibatalkan. */
	public record Delivery(List<DeviceCommand> commands, List<UUID> cancel) {
	}

	/** Hasil laporan mesin; dipetakan ke balasan HTTP oleh adapter protokol. */
	public enum CommandUpdate {
		OK, NOT_FOUND, NOT_ACTIVE, BAD_REQUEST
	}

	/**
	 * Dipanggil setiap heartbeat. MENUNGGU menjadi DIKIRIM, dan DIKIRIM dikirim ulang sampai mesin melaporkan hasil.
	 * Perintah yang sudah lewat batas waktu tidak dikirim; menandainya urusan sweeper, sehingga heartbeat hanya menulis
	 * perintah milik mesin ini (final review I1).
	 */
	@Transactional
	public Delivery deliver(Device device, Instant now) {
		String actor = machineActor(device);
		List<DeviceCommand> active = commands.findDeliverable(device.getId(), now.minus(pickupTimeout),
				now.minus(resultTimeout));
		for (DeviceCommand command : active) {
			if (DeviceCommand.MENUNGGU.equals(command.getStatus())) {
				Map<String, Object> before = snapshot(command);
				command.markSent(now);
				changed(actor, "COMMAND_SENT", command, before);
			}
		}
		List<UUID> cancel = commands.findRecentlyCancelledSent(device.getId(), now.minus(cancelNotice))
			.stream()
			.map(DeviceCommand::getId)
			.toList();
		return new Delivery(active, cancel);
	}

	@Transactional
	public CommandUpdate progress(Device device, String commandId, Integer step) {
		DeviceCommand command = forDevice(device, commandId);
		if (command == null) {
			return CommandUpdate.NOT_FOUND;
		}
		if (step == null || step < 1 || step > 3) {
			return CommandUpdate.BAD_REQUEST;
		}
		if (!command.isActive()) {
			return CommandUpdate.NOT_ACTIVE;
		}
		Map<String, Object> before = snapshot(command);
		command.recordProgress(step);
		changed(machineActor(device), "COMMAND_PROGRESS", command, before);
		return CommandUpdate.OK;
	}

	/**
	 * Hasil akhir. Hasil untuk perintah yang sudah selesai (termasuk yang dibatalkan admin) diabaikan; mesin tetap
	 * dibalas sukses supaya berhenti mengirim ulang (Fokus review #1).
	 */
	@Transactional
	public CommandUpdate result(Device device, String commandId, String status, String error, String pin) {
		DeviceCommand command = forDevice(device, commandId);
		if (command == null) {
			return CommandUpdate.NOT_FOUND;
		}
		boolean success = DeviceCommand.BERHASIL.equals(status);
		boolean failure = DeviceCommand.GAGAL.equals(status) && error != null && !error.isBlank();
		if (!success && !failure) {
			return CommandUpdate.BAD_REQUEST;
		}
		if (!command.isActive()) {
			return CommandUpdate.NOT_ACTIVE;
		}
		Instant now = Instant.now();
		Map<String, Object> before = snapshot(command);
		if (success) {
			command.succeed(now);
			events.publishEvent(new EnrollSucceededEvent(command.getId(), command.getEmployee().getId(),
					command.getFinger(), device.getId(), now));
		}
		else {
			String code = error.trim();
			command.fail(code, JARI_SUDAH_TERDAFTAR.equals(code) ? ownerOf(pin) : null, now);
		}
		changed(machineActor(device), "COMMAND_RESULT", command, before);
		return CommandUpdate.OK;
	}

	/** Nama karyawan pemilik PIN, atau "PIN …" jika PIN tidak dikenal server. */
	private String ownerOf(String pin) {
		if (pin == null || pin.isBlank()) {
			return "karyawan lain";
		}
		return employees.findByDevicePin(pin.trim()).map(Employee::getFullName).orElse("PIN " + pin.trim());
	}

	/** Perintah milik mesin ini; ID mesin lain atau bukan UUID dianggap tidak dikenal. */
	private DeviceCommand forDevice(Device device, String commandId) {
		UUID id;
		try {
			id = UUID.fromString(commandId);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
		return commands.findById(id).filter(c -> c.getDevice().getId().equals(device.getId())).orElse(null);
	}

	private static String machineActor(Device device) {
		return "mesin " + device.getSerialNumber();
	}

	/** Menandai perintah yang melewati batas waktu; dipanggil terjadwal dan sebelum membuat perintah baru. */
	@Transactional
	public int expireStale(Instant now) {
		int changed = 0;
		for (DeviceCommand command : commands.findUnpickedBefore(now.minus(pickupTimeout))) {
			Map<String, Object> before = snapshot(command);
			command.expire(now);
			changed(SYSTEM_ACTOR, "COMMAND_EXPIRED", command, before);
			changed++;
		}
		for (DeviceCommand command : commands.findSentBefore(now.minus(resultTimeout))) {
			Map<String, Object> before = snapshot(command);
			command.fail(DeviceCommand.WAKTU_HABIS, null, now);
			changed(SYSTEM_ACTOR, "COMMAND_EXPIRED", command, before);
			changed++;
		}
		return changed;
	}

	/**
	 * Menyimpan perintah baru segera (flush) agar pelanggaran unique index muncul di sini dan bisa dijawab 409 oleh
	 * pemanggil.
	 */
	@Transactional
	public void create(DeviceCommand command) {
		commands.saveAndFlush(command);
		audit.record(CurrentUser.username(), "ENROLL_REQUEST", "DeviceCommand", command.getId(), null,
				snapshot(command), null);
		events.publishEvent(new DeviceCommandChangedEvent(command.getId(),
				command.getEmployee() == null ? null : command.getEmployee().getId()));
	}

	@Transactional(readOnly = true)
	public CommandResponse get(String id) {
		return CommandResponse.from(find(id));
	}

	@Transactional
	public CommandResponse cancel(String id) {
		DeviceCommand command = find(id);
		if (!command.isActive()) {
			throw ApiException.conflict("Perintah sudah selesai.");
		}
		Map<String, Object> before = snapshot(command);
		command.cancel(Instant.now());
		changed(CurrentUser.username(), "CANCEL_COMMAND", command, before);
		return CommandResponse.from(command);
	}

	private DeviceCommand find(String id) {
		UUID uuid;
		try {
			uuid = UUID.fromString(id);
		}
		catch (IllegalArgumentException ex) {
			throw ApiException.notFound("Perintah");
		}
		return commands.findById(uuid).orElseThrow(() -> ApiException.notFound("Perintah"));
	}

	/** Audit di transaksi yang sama, lalu beri tahu web setelah commit (SSE). */
	void changed(String actor, String action, DeviceCommand command, Map<String, Object> before) {
		audit.record(actor, action, "DeviceCommand", command.getId(), before, snapshot(command), null);
		events.publishEvent(new DeviceCommandChangedEvent(command.getId(),
				command.getEmployee() == null ? null : command.getEmployee().getId()));
	}

	static Map<String, Object> snapshot(DeviceCommand command) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("type", command.getType());
		data.put("status", command.getStatus());
		data.put("deviceId", command.getDevice().getId());
		data.put("finger", command.getFinger());
		data.put("step", command.getStep());
		data.put("errorCode", command.getErrorCode());
		return data;
	}

}
