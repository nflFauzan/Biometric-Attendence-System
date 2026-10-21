package com.biometricattendance.device;

import java.time.Instant;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identifikasi mesin dan pencatatan kontak terakhir. Dipakai semua adapter protokol (K-054). Mesin ZK-Push
 * dikenali dari nomor seri, alat rakitan dari kuncinya (K-088). Setiap jalur hanya menerima jenis mesinnya
 * sendiri (K-099). Mesin yang tidak terdaftar atau nonaktif tidak dicatat sebagai kontak (K-067).
 */
@Service
public class DeviceContactService {

	private final DeviceRepository devices;
	private final DeviceStatus status;
	private final UnregisteredDeviceTracker unregistered;
	private final ApplicationEventPublisher events;

	public DeviceContactService(DeviceRepository devices, DeviceStatus status, UnregisteredDeviceTracker unregistered,
			ApplicationEventPublisher events) {
		this.devices = devices;
		this.status = status;
		this.unregistered = unregistered;
		this.events = events;
	}

	/**
	 * Mencatat kontak dari mesin ZK-Push dengan nomor seri tersebut. Mengembalikan kosong jika mesin tidak
	 * terdaftar, nonaktif, atau bukan ZK-Push; nomor seri yang belum terdaftar dicatat untuk ditampilkan di
	 * halaman Mesin.
	 */
	@Transactional
	public Optional<Device> contact(String serialNumber, String ip, String firmware, String info) {
		Instant now = Instant.now();
		if (serialNumber == null || serialNumber.isBlank()) {
			return Optional.empty();
		}
		Optional<Device> found = devices.findBySerialNumberIgnoreCase(serialNumber.trim());
		if (found.isEmpty()) {
			unregistered.record(serialNumber, ip, now);
			return Optional.empty();
		}
		Device device = found.get();
		// K-099: ID alat rakitan terlihat di layarnya; tanpa penjaga ini /iclock bisa dipakai tanpa kunci.
		if (!device.isActive() || !Device.PROTOCOL_ZK_PUSH.equals(device.getProtocol())) {
			return Optional.empty();
		}
		record(device, now, ip, firmware, info, false);
		return Optional.of(device);
	}

	/** Mengenali alat rakitan dari kunci di header {@code Authorization: Bearer} dan mencatat kontaknya. */
	@Transactional
	public KeyContact contactByKey(String bearerKey, String ip, String firmware, String info, Integer sensorUsed,
			Integer sensorCapacity) {
		if (bearerKey == null || bearerKey.isBlank()) {
			return KeyContact.rejected();
		}
		String keyHash = DeviceKeys.hash(bearerKey.trim());
		Instant now = Instant.now();
		Optional<Device> found = devices.findByKeyHash(keyHash);
		if (found.isEmpty()) {
			found = promotePending(keyHash, now);
		}
		if (found.isEmpty() || !found.get().isRakitan()) {
			return KeyContact.rejected();
		}
		Device device = found.get();
		if (!device.isActive()) {
			return new KeyContact(KeyContact.Status.INACTIVE, device);
		}
		// Atomik di database: dua request bersamaan saat mesin menyala hanya memunculkan satu pemberitahuan.
		boolean firstConnect = device.getKeyFirstUsedAt() == null
				&& devices.claimKeyFirstUse(device.getId(), keyHash, now) == 1;
		if (firstConnect) {
			device.markKeyFirstUsed(now);
		}
		record(device, now, ip, firmware, info, firstConnect);
		device.recordSensor(sensorUsed, sensorCapacity);
		return new KeyContact(KeyContact.Status.ACCEPTED, device);
	}

	/**
	 * Kunci yang cocok dengan kunci tertunda mesin aktif: kontak pertama dengan kunci baru menjadikannya kunci utama
	 * (spec §18.6). Request bersamaan yang kalah menemukan mesinnya lewat kunci utama yang baru. Kontak ini tidak
	 * memicu pemberitahuan "mesin terhubung" karena kontak pertama sudah tercatat.
	 */
	private Optional<Device> promotePending(String keyHash, Instant now) {
		Optional<Device> pending = devices.findByPendingKeyHash(keyHash);
		if (pending.isEmpty()) {
			// Request lain bisa saja memindahkan kunci ini di antara pencarian kunci utama dan pencarian ini.
			return devices.findByKeyHash(keyHash);
		}
		if (!pending.get().isRakitan() || !pending.get().isActive()) {
			return pending;
		}
		Device device = pending.get();
		if (devices.promotePendingKey(device.getId(), keyHash, now) == 1) {
			events.publishEvent(new KeyRotatedEvent(device.getId(), device.getSerialNumber()));
		}
		return devices.findByKeyHash(keyHash);
	}

	private void record(Device device, Instant now, String ip, String firmware, String info, boolean firstConnect) {
		boolean wasOnline = status.isOnline(device, now);
		device.recordContact(now, truncate(ip, 45), truncate(firmware, 100), truncate(info, 255));
		events.publishEvent(new DeviceContactEvent(device.getId(), device.getName(), !wasOnline, firstConnect));
	}

	private static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

}
