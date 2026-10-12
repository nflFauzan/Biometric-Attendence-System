package com.biometricattendance.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.biometricattendance.AttendanceFixtures;

/**
 * K-020/K-061: bila penyimpanan gagal, mesin tidak boleh menerima OK agar scan dikirim ulang (X-03).
 * Context terpisah karena penyimpanan dibuat gagal dengan spy.
 */
class IngestFailureTests extends AttendanceFixtures {

	@MockitoSpyBean
	private ScanIngestService ingest;

	@Test
	void storageFailureIsNotAcknowledged() throws Exception {
		long branch = createBranch();
		String sn = uniqueSerial();
		long device = createDevice(branch, sn, ACCEPT_FROM);
		doThrow(new DataAccessResourceFailureException("database tidak tersedia")).when(ingest)
			.ingest(any(), any(), any(), any());

		postAttlog(sn, lines(line(uniquePin(), "2026-10-05 08:00:00"))).andExpect(status().isInternalServerError())
			.andExpect(content().string("ERROR"));
		assertThat(scanCount(device)).isZero();
	}

}
