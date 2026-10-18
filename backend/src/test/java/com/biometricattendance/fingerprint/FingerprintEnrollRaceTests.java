package com.biometricattendance.fingerprint;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.biometricattendance.devicecommand.DeviceCommandRepository;
import com.biometricattendance.ingest.RakitanFixtures;

/**
 * Dua admin klik "Daftarkan" bersamaan: pemeriksaan aplikasi sama-sama lolos, lalu unique index database menolak
 * yang kedua. Hasilnya harus 409 dengan pesan Indonesia, bukan 500 (Fokus review #3).
 */
class FingerprintEnrollRaceTests extends RakitanFixtures {

	@MockitoSpyBean
	private DeviceCommandRepository commands;

	@Test
	void concurrentEnrollBackstopReturns409() throws Exception {
		long branch = createBranch();
		long device = onlineRakitan(branch).id();
		enrollOk(readyEmployee(branch), device, 1);
		// Klik kedua tidak melihat perintah pertama (seolah datang bersamaan).
		doReturn(false).when(commands).existsActiveEnrollForDevice(anyLong());

		enroll(readyEmployee(branch), device, 1).andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Mesin sedang dipakai untuk pendaftaran."));
	}

}
