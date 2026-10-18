package com.biometricattendance.devicecommand;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import com.biometricattendance.device.Device;
import com.biometricattendance.employee.Employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

/**
 * Perintah server untuk satu alat rakitan, dititipkan lewat heartbeat (spec §4). Isinya hanya data tidak rahasia
 * (PIN, jari, nama tampilan). Status: MENUNGGU → DIKIRIM → BERHASIL | GAGAL | KEDALUWARSA | DIBATALKAN.
 */
@Entity
@Table(name = "device_commands")
public class DeviceCommand implements Persistable<UUID> {

	public static final String TYPE_ENROLL = "ENROLL";

	/** Hapus satu jari (PIN + jari) dari sensor; tidak kedaluwarsa dan tidak bisa dibatalkan (spec §17). */
	public static final String TYPE_DELETE_TEMPLATE = "DELETE_TEMPLATE";

	/** Hapus semua jari milik satu PIN dari sensor (K-090). */
	public static final String TYPE_DELETE_EMPLOYEE = "DELETE_EMPLOYEE";

	public static final String MENUNGGU = "MENUNGGU";
	public static final String DIKIRIM = "DIKIRIM";
	public static final String BERHASIL = "BERHASIL";
	public static final String GAGAL = "GAGAL";
	public static final String KEDALUWARSA = "KEDALUWARSA";
	public static final String DIBATALKAN = "DIBATALKAN";

	/** Kode dari mesin: tidak ada jari yang ditempelkan dalam batas waktunya (spec §9). */
	public static final String WAKTU_HABIS = "WAKTU_HABIS";

	/** Kode yang hanya diisi server: mesin tidak melaporkan hasil dalam batas waktu (K-101). */
	public static final String TIDAK_ADA_HASIL = "TIDAK_ADA_HASIL";

	static final int MAX_ERROR_CODE = 40;
	static final int MAX_ERROR_DETAIL = 255;

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "device_id", nullable = false)
	private Device device;

	@Column(nullable = false)
	private String type;

	@Column(nullable = false)
	private String status;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "employee_id")
	private Employee employee;

	private Integer finger;

	private String devicePin;

	private String displayName;

	private Integer step;

	private String errorCode;

	private String errorDetail;

	@Column(nullable = false)
	private String createdBy;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant sentAt;

	private Instant finishedAt;

	/** Sweeper dan laporan mesin bisa menyentuh baris yang sama; yang kalah dibatalkan, bukan menimpa. */
	@Version
	private long version;

	@Transient
	private boolean isNew;

	protected DeviceCommand() {
	}

	public static DeviceCommand enroll(Device device, Employee employee, int finger, String createdBy, Instant now) {
		DeviceCommand command = new DeviceCommand();
		command.id = UUID.randomUUID();
		command.isNew = true;
		command.device = device;
		command.type = TYPE_ENROLL;
		command.status = MENUNGGU;
		command.employee = employee;
		command.finger = finger;
		command.devicePin = employee.getDevicePin();
		command.displayName = employee.getFullName();
		command.createdBy = createdBy;
		command.createdAt = now;
		return command;
	}

	public static DeviceCommand deleteTemplate(Device device, Employee employee, String pin, int finger,
			String createdBy, Instant now) {
		DeviceCommand command = delete(device, employee, pin, createdBy, now);
		command.type = TYPE_DELETE_TEMPLATE;
		command.finger = finger;
		return command;
	}

	public static DeviceCommand deleteEmployee(Device device, Employee employee, String pin, String createdBy,
			Instant now) {
		DeviceCommand command = delete(device, employee, pin, createdBy, now);
		command.type = TYPE_DELETE_EMPLOYEE;
		return command;
	}

	private static DeviceCommand delete(Device device, Employee employee, String pin, String createdBy, Instant now) {
		DeviceCommand command = new DeviceCommand();
		command.id = UUID.randomUUID();
		command.isNew = true;
		command.device = device;
		command.status = MENUNGGU;
		command.employee = employee;
		command.devicePin = pin;
		command.createdBy = createdBy;
		command.createdAt = now;
		return command;
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		this.isNew = false;
	}

	public boolean isActive() {
		return MENUNGGU.equals(status) || DIKIRIM.equals(status);
	}

	public boolean isDelete() {
		return TYPE_DELETE_TEMPLATE.equals(type) || TYPE_DELETE_EMPLOYEE.equals(type);
	}

	/** "Coba lagi" untuk penghapusan yang gagal: perintah yang sama dikirim ulang dari awal. */
	public void requeue() {
		if (!isDelete() || !GAGAL.equals(status)) {
			throw new IllegalStateException("Perintah " + id + " tidak bisa dicoba lagi");
		}
		status = MENUNGGU;
		step = null;
		errorCode = null;
		errorDetail = null;
		sentAt = null;
		finishedAt = null;
	}

	/** Pertama kali ikut di balasan heartbeat. */
	public void markSent(Instant at) {
		requireActive();
		if (MENUNGGU.equals(status)) {
			status = DIKIRIM;
			sentAt = at;
		}
	}

	public void recordProgress(int step) {
		requireActive();
		this.step = step;
	}

	public void succeed(Instant at) {
		finish(BERHASIL, at);
	}

	public void fail(String code, String detail, Instant at) {
		finish(GAGAL, at);
		this.errorCode = truncate(code, MAX_ERROR_CODE);
		this.errorDetail = truncate(detail, MAX_ERROR_DETAIL);
	}

	public void expire(Instant at) {
		finish(KEDALUWARSA, at);
	}

	public void cancel(Instant at) {
		finish(DIBATALKAN, at);
	}

	private void finish(String finalStatus, Instant at) {
		requireActive();
		this.status = finalStatus;
		this.finishedAt = at;
	}

	private void requireActive() {
		if (!isActive()) {
			throw new IllegalStateException("Perintah " + id + " sudah " + status);
		}
	}

	private static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

	public Device getDevice() {
		return device;
	}

	public String getType() {
		return type;
	}

	public String getStatus() {
		return status;
	}

	public Employee getEmployee() {
		return employee;
	}

	public Integer getFinger() {
		return finger;
	}

	public String getDevicePin() {
		return devicePin;
	}

	public String getDisplayName() {
		return displayName;
	}

	public Integer getStep() {
		return step;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public String getErrorDetail() {
		return errorDetail;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

}
