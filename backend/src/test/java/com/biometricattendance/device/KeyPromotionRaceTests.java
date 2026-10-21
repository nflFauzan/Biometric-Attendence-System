package com.biometricattendance.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Heartbeat dengan kunci baru yang kalah balapan dari request lain: saat ia mencari kunci utama, perpindahan belum
 * di-commit; saat ia mencari kunci tertunda, perpindahan sudah di-commit. Request itu tetap diterima (review akhir I2).
 */
class KeyPromotionRaceTests {

	@Test
	void newKeyAcceptedWhenPromotionCommitsBetweenLookups() {
		DeviceRepository devices = mock(DeviceRepository.class);
		Device device = mock(Device.class);
		when(device.isRakitan()).thenReturn(true);
		when(device.isActive()).thenReturn(true);
		when(device.getKeyFirstUsedAt()).thenReturn(Instant.now());
		String hash = DeviceKeys.hash("kunci-baru");
		when(devices.findByKeyHash(hash)).thenReturn(Optional.empty(), Optional.of(device));
		when(devices.findByPendingKeyHash(hash)).thenReturn(Optional.empty());
		DeviceContactService service = new DeviceContactService(devices, mock(DeviceStatus.class),
				mock(UnregisteredDeviceTracker.class), mock(ApplicationEventPublisher.class));

		KeyContact contact = service.contactByKey("kunci-baru", "127.0.0.1", "sim", null, null, null);

		assertThat(contact.status()).isEqualTo(KeyContact.Status.ACCEPTED);
	}

}
