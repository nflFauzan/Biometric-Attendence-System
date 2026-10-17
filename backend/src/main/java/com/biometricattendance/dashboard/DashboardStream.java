package com.biometricattendance.dashboard;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.biometricattendance.attendance.AttendanceChangedEvent;
import com.biometricattendance.device.Device;
import com.biometricattendance.device.DeviceContactEvent;
import com.biometricattendance.device.DeviceRepository;
import com.biometricattendance.device.DeviceStatus;
import com.biometricattendance.ingest.ScansSavedEvent;
import com.biometricattendance.leave.LeaveChangedEvent;

/**
 * Pemberitahuan real-time ke dashboard lewat Server-Sent Events (DASH-006). Event hanya dikirim setelah commit,
 * jadi dashboard tidak pernah menampilkan scan yang batal tersimpan. Isinya hanya "ada perubahan"; dashboard
 * lalu mengambil ringkasan terbaru lewat API biasa (yang tetap memeriksa hak akses).
 */
@Component
public class DashboardStream {

	private static final Logger log = LoggerFactory.getLogger(DashboardStream.class);
	static final Duration EMITTER_TIMEOUT = Duration.ofMinutes(30);

	private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
	private final DeviceRepository devices;
	private final DeviceStatus status;
	private volatile Set<Long> lastOnline;

	public DashboardStream(DeviceRepository devices, DeviceStatus status) {
		this.devices = devices;
		this.status = status;
	}

	public SseEmitter subscribe() {
		SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT.toMillis());
		emitters.add(emitter);
		emitter.onCompletion(() -> emitters.remove(emitter));
		emitter.onTimeout(() -> emitters.remove(emitter));
		emitter.onError(ex -> emitters.remove(emitter));
		send(emitter, Map.of("type", "CONNECTED"));
		return emitter;
	}

	int subscriberCount() {
		return emitters.size();
	}

	@TransactionalEventListener
	void onScansSaved(ScansSavedEvent event) {
		broadcast(Map.of("type", "SCAN", "deviceId", event.deviceId(), "saved", event.saved()));
	}

	@TransactionalEventListener
	void onAttendanceChanged(AttendanceChangedEvent event) {
		broadcast(Map.of("type", "CORRECTION", "employeeId", event.employeeId(), "date", event.date().toString()));
	}

	/** Notifikasi izin/cuti untuk Owner/HR (K-081); stream ini memang hanya untuk Owner dan HR/Finance. */
	@TransactionalEventListener
	void onLeaveChanged(LeaveChangedEvent event) {
		broadcast(Map.of("type", "LEAVE", "action", event.action().name(), "requestId", event.requestId(),
				"employeeId", event.employeeId(), "employeeName", event.employeeName(), "leaveType",
				event.leaveTypeName()));
	}

	@TransactionalEventListener
	void onDeviceContact(DeviceContactEvent event) {
		// K-099: konfirmasi "Mesin … baru saja terhubung" saat pengaturan pertama dengan kunci baru.
		if (event.firstConnect()) {
			broadcast(Map.of("type", "DEVICE_CONNECTED", "deviceId", event.deviceId(), "deviceName",
					event.deviceName()));
		}
		if (event.cameOnline()) {
			broadcast(Map.of("type", "DEVICE_STATUS", "deviceId", event.deviceId()));
		}
	}

	/** Keepalive agar koneksi tidak diputus proxy, sekaligus mendeteksi mesin yang berubah offline (K-064). */
	@Scheduled(fixedDelayString = "${app.dashboard.tick:20s}")
	void tick() {
		Instant now = Instant.now();
		Set<Long> online = devices.findAll()
			.stream()
			.filter(d -> status.isOnline(d, now))
			.map(Device::getId)
			.collect(Collectors.toSet());
		Set<Long> previous = lastOnline;
		lastOnline = online;
		if (previous != null && !previous.equals(online)) {
			broadcast(Map.of("type", "DEVICE_STATUS"));
		}
		for (SseEmitter emitter : emitters) {
			try {
				emitter.send(SseEmitter.event().comment("keepalive"));
			}
			catch (IOException | IllegalStateException ex) {
				emitters.remove(emitter);
			}
		}
	}

	private void broadcast(Map<String, Object> payload) {
		emitters.forEach(emitter -> send(emitter, payload));
	}

	private void send(SseEmitter emitter, Map<String, Object> payload) {
		try {
			emitter.send(SseEmitter.event().name("update").data(payload, MediaType.APPLICATION_JSON));
		}
		catch (IOException | IllegalStateException ex) {
			log.debug("Koneksi dashboard terputus: {}", ex.getMessage());
			emitters.remove(emitter);
		}
	}

}
