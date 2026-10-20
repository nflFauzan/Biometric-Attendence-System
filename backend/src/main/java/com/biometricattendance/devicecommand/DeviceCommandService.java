package com.biometricattendance.devicecommand;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.audit.AuditService;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.device.Device;
import com.biometricattendance.device.KeyRevokedEvent;
import com.biometricattendance.device.KeyRotatedEvent;
import com.biometricattendance.devicecommand.DeviceCommandDtos.CommandResponse;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;

/**
 * Siklus hidup perintah ke alat rakitan (spec §4, §16). Batas waktu adalah parameter teknis (K-094), bukan aturan
 * bisnis: perintah yang tidak diambil mesin kedaluwarsa, dan perintah yang diambil tanpa hasil dianggap gagal.
 */
@Service
public class DeviceCommandService {

	public static final String SYSTEM_ACTOR = "sistem";
	public static final String RISKY_BUSY = "Mesin masih menerapkan perubahan jaringan atau kunci.";
	private static final String RISKY_INDEX = "ux_device_commands_risky_device";
	private static final String JARI_SUDAH_TERDAFTAR = "JARI_SUDAH_TERDAFTAR";

	private final DeviceCommandRepository commands;
	private final AuditService audit;
	private final ApplicationEventPublisher events;
	private final Duration pickupTimeout;
	private final Duration resultTimeout;
	private final Duration cancelNotice;
	private final Duration networkResultTimeout;
	private final Duration rotateKeyTimeout;
	private final EmployeeRepository employees;

	public DeviceCommandService(DeviceCommandRepository commands, AuditService audit,
			ApplicationEventPublisher events, EmployeeRepository employees,
			@Value("${app.device.enroll.pickup-timeout:30s}") Duration pickupTimeout,
			@Value("${app.device.enroll.result-timeout:75s}") Duration resultTimeout,
			@Value("${app.device.cancel-notice:2m}") Duration cancelNotice,
			@Value("${app.device.network.result-timeout:10m}") Duration networkResultTimeout,
			@Value("${app.device.rotate-key.timeout:24h}") Duration rotateKeyTimeout) {
		this.commands = commands;
		this.audit = audit;
		this.events = events;
		this.employees = employees;
		this.pickupTimeout = pickupTimeout;
		this.resultTimeout = resultTimeout;
		this.cancelNotice = cancelNotice;
		this.networkResultTimeout = networkResultTimeout;
		this.rotateKeyTimeout = rotateKeyTimeout;
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
	 * perintah milik mesin ini (final review I1). Perintah berisiko ditahan selama mesin punya ENROLL aktif, supaya
	 * pendaftaran tidak terputus oleh ganti jaringan atau kunci (spec §18.4).
	 */
	@Transactional
	public Delivery deliver(Device device, Instant now) {
		String actor = machineActor(device);
		List<DeviceCommand> active = commands.findDeliverable(device.getId(), now.minus(pickupTimeout),
				now.minus(resultTimeout));
		if (active.stream().anyMatch(c -> DeviceCommand.TYPE_ENROLL.equals(c.getType()))) {
			active = active.stream().filter(c -> !c.isRisky()).toList();
		}
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
		// Hanya ENROLL yang punya langkah tempelan jari.
		if (!command.isActive() || !DeviceCommand.TYPE_ENROLL.equals(command.getType())) {
			return CommandUpdate.NOT_ACTIVE;
		}
		Map<String, Object> before = snapshot(command);
		command.recordProgress(step);
		changed(machineActor(device), "COMMAND_PROGRESS", command, before);
		return CommandUpdate.OK;
	}

	/**
	 * Hasil akhir. Hasil untuk perintah yang sudah selesai (termasuk yang dibatalkan admin) diabaikan; mesin tetap
	 * dibalas sukses supaya berhenti mengirim ulang (Fokus review #1). ROTATE_KEY selesai saat kunci baru dipakai
	 * (spec §18.6), jadi laporan BERHASIL untuknya hanya dibalas sukses tanpa mengubah apa pun.
	 *
	 * @param detail keterangan mesin untuk perintah selain ENROLL, mis. SSID yang gagal (spec §18.5)
	 */
	@Transactional
	public CommandUpdate result(Device device, String commandId, String status, String error, String pin,
			String detail) {
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
		boolean enroll = DeviceCommand.TYPE_ENROLL.equals(command.getType());
		boolean rotation = DeviceCommand.TYPE_ROTATE_KEY.equals(command.getType());
		if (success && rotation) {
			return CommandUpdate.OK;
		}
		Instant now = Instant.now();
		Map<String, Object> before = snapshot(command);
		if (success) {
			command.succeed(now);
			// Paket jaringan yang berhasil menjadi jaringan yang berlaku mesin ini (spec §18.5).
			if (DeviceCommand.TYPE_APPLY_NETWORK.equals(command.getType())) {
				command.getDevice().applyNetwork(command.getNetworkConfigId());
			}
			if (enroll) {
				events.publishEvent(new EnrollSucceededEvent(command.getId(), command.getEmployee().getId(),
						command.getFinger(), device.getId(), machineActor(device), now));
			}
		}
		else {
			String code = error.trim();
			if (enroll) {
				command.fail(code, JARI_SUDAH_TERDAFTAR.equals(code) ? ownerOf(pin) : null, now);
			}
			else {
				command.fail(code, detail == null || detail.isBlank() ? null : detail.trim(), now);
			}
			if (rotation) {
				command.getDevice().clearPendingKey();
			}
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

	/**
	 * Menandai perintah yang melewati batas waktu; dipanggil terjadwal dan sebelum membuat perintah baru. Perintah
	 * berisiko yang masih MENUNGGU tidak pernah kedaluwarsa (mesin offline tetap menerimanya nanti, spec §18.4),
	 * kecuali ROTATE_KEY yang gagal bila kunci baru tidak dipakai dalam batas waktunya.
	 */
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
			changed += failStale(command, DeviceCommand.TIDAK_ADA_HASIL, now);
		}
		for (DeviceCommand command : commands.findNetworkSentBefore(now.minus(networkResultTimeout))) {
			changed += failStale(command, DeviceCommand.TIDAK_ADA_HASIL, now);
		}
		for (DeviceCommand command : commands.findRotationsBefore(now.minus(rotateKeyTimeout))) {
			command.getDevice().clearPendingKey();
			changed += failStale(command, DeviceCommand.KUNCI_BARU_TIDAK_DIPAKAI, now);
		}
		return changed;
	}

	private int failStale(DeviceCommand command, String code, Instant now) {
		Map<String, Object> before = snapshot(command);
		command.fail(code, null, now);
		changed(SYSTEM_ACTOR, "COMMAND_EXPIRED", command, before);
		return 1;
	}

	/**
	 * Menyimpan perintah baru segera (flush) agar pelanggaran unique index muncul di sini dan bisa dijawab 409 oleh
	 * pemanggil.
	 */
	@Transactional
	public void create(DeviceCommand command) {
		create(command, CurrentUser.username());
	}

	/** Seperti {@link #create(DeviceCommand)} dengan pelaku audit eksplisit, mis. {@code sistem} untuk tugas terjadwal. */
	@Transactional
	public void create(DeviceCommand command, String actor) {
		save(command, command.isDelete() ? "DELETE_REQUEST" : "ENROLL_REQUEST", actor, Map.of());
	}

	/** Perintah berisiko oleh pengguna yang login; lihat {@link #createRisky(DeviceCommand, String, String)}. */
	@Transactional
	public void createRisky(DeviceCommand command, String auditAction) {
		createRisky(command, auditAction, CurrentUser.username());
	}

	/**
	 * Perintah berisiko: satu aktif per mesin (unique index). Pelanggaran index dijawab 409; pelanggaran lain
	 * diteruskan apa adanya.
	 */
	@Transactional
	public void createRisky(DeviceCommand command, String auditAction, String actor) {
		createRisky(command, auditAction, actor, Map.of());
	}

	/** @param details data tidak rahasia yang ikut dicatat di audit, mis. nama Wi-Fi dan alamat server */
	@Transactional
	public void createRisky(DeviceCommand command, String auditAction, String actor, Map<String, Object> details) {
		if (commands.existsActiveRisky(command.getDevice().getId())) {
			throw ApiException.conflict(RISKY_BUSY);
		}
		try {
			save(command, auditAction, actor, details);
		}
		catch (DataIntegrityViolationException ex) {
			if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains(RISKY_INDEX)) {
				throw ApiException.conflict(RISKY_BUSY);
			}
			throw ex;
		}
	}

	private void save(DeviceCommand command, String auditAction, String actor, Map<String, Object> details) {
		commands.saveAndFlush(command);
		Map<String, Object> after = snapshot(command);
		after.putAll(details);
		audit.record(actor, auditAction, "DeviceCommand", command.getId(), null, after, null);
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
		if (command.isDelete()) {
			throw ApiException.conflict("Perintah penghapusan tidak bisa dibatalkan.");
		}
		if (command.isRisky() && !CurrentUser.hasRole("OWNER")) {
			throw ApiException.forbidden("Hanya Owner yang bisa membatalkan perintah ini");
		}
		if (!command.isActive()) {
			throw ApiException.conflict("Perintah sudah selesai.");
		}
		// Perintah berisiko yang sudah dikirim mungkin sedang diterapkan mesin; membatalkannya tidak bisa ditarik.
		if (command.isRisky() && !DeviceCommand.MENUNGGU.equals(command.getStatus())) {
			throw ApiException.conflict("Perintah sudah dikirim ke mesin dan tidak bisa dibatalkan.");
		}
		Map<String, Object> before = snapshot(command);
		command.cancel(Instant.now());
		if (DeviceCommand.TYPE_ROTATE_KEY.equals(command.getType())) {
			command.getDevice().clearPendingKey();
		}
		changed(CurrentUser.username(), "CANCEL_COMMAND", command, before);
		return CommandResponse.from(command);
	}

	/** "Coba lagi" untuk penghapusan yang gagal (spec §17.7): perintah yang sama kembali MENUNGGU. */
	@Transactional
	public CommandResponse retry(String id) {
		DeviceCommand command = find(id);
		if (!command.isDelete() || !DeviceCommand.GAGAL.equals(command.getStatus())) {
			throw ApiException.conflict("Hanya penghapusan yang gagal yang bisa dicoba lagi.");
		}
		Map<String, Object> before = snapshot(command);
		command.requeue();
		changed(CurrentUser.username(), "COMMAND_RETRY", command, before);
		return CommandResponse.from(command);
	}

	/** Kunci baru dipakai mesin: ROTATE_KEY selesai, dicatat sebagai tindakan mesin (spec §18.6). */
	@EventListener
	public void onKeyRotated(KeyRotatedEvent event) {
		Instant now = Instant.now();
		for (DeviceCommand command : commands.findActiveRotations(event.deviceId())) {
			Map<String, Object> before = snapshot(command);
			command.succeed(now);
			changed("mesin " + event.serialNumber(), "KEY_ROTATED", command, before);
		}
	}

	/** "Buat kode baru" membatalkan ROTATE_KEY yang sedang berjalan, termasuk yang sudah dikirim. */
	@EventListener
	public void onKeyRevoked(KeyRevokedEvent event) {
		Instant now = Instant.now();
		for (DeviceCommand command : commands.findActiveRotations(event.deviceId())) {
			Map<String, Object> before = snapshot(command);
			command.cancel(now);
			changed(CurrentUser.username(), "CANCEL_COMMAND", command, before);
		}
	}

	/** Membatalkan ENROLL aktif karyawan yang keluar/nonaktif (spec §17.4); perintah hapus tidak disentuh. */
	@Transactional
	public int cancelActiveEnrolls(Long employeeId, String actor, Instant now) {
		List<DeviceCommand> active = commands.findActiveForEmployee(employeeId);
		for (DeviceCommand command : active) {
			Map<String, Object> before = snapshot(command);
			command.cancel(now);
			changed(actor, "CANCEL_COMMAND", command, before);
		}
		return active.size();
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
