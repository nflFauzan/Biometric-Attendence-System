package com.biometricattendance.device;

import java.time.Instant;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identifikasi mesin dari nomor seri dan pencatatan kontak terakhir. Dipakai semua adapter protokol (K-054).
 * Mesin yang tidak terdaftar atau nonaktif tidak dikenali (K-067).
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
	 * Mencatat kontak dari mesin dengan nomor seri tersebut. Mengembalikan kosong jika mesin tidak terdaftar
	 * atau nonaktif; nomor seri yang belum terdaftar dicatat untuk ditampilkan di halaman Mesin.
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
		if (!device.isActive()) {
			return Optional.empty();
		}
		boolean wasOnline = status.isOnline(device, now);
		device.recordContact(now, ip, truncate(firmware, 100), truncate(info, 255));
		events.publishEvent(new DeviceContactEvent(device.getId(), !wasOnline));
		return Optional.of(device);
	}

	private static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

}
