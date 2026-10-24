package com.biometricattendance.leave;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.biometricattendance.leave.letter.LeaveLetterRenderer;
import com.biometricattendance.leave.letter.LetterModel;

/** Render PDF bersamaan: tiap dokumen hanya berisi data dan glyph miliknya sendiri. */
class LeaveLetterRendererTests {

	@Test
	void concurrentRendersStayIndependent() throws Exception {
		LeaveLetterRenderer renderer = new LeaveLetterRenderer();
		int threads = 4;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<byte[]>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				String name = "Karyawan Uji" + (char) ('A' + i) + "Zq" + i;
				futures.add(pool.submit(() -> {
					start.await();
					byte[] last = null;
					for (int n = 0; n < 5; n++) {
						last = renderer.render(model(name));
					}
					return last;
				}));
			}
			start.countDown();
			for (int i = 0; i < threads; i++) {
				String name = "Karyawan Uji" + (char) ('A' + i) + "Zq" + i;
				String text = LeaveLetterTests.text(futures.get(i).get());
				assertThat(text).contains(name).contains("SURAT PERMOHONAN IZIN");
				for (int j = 0; j < threads; j++) {
					if (j != i) {
						assertThat(text).doesNotContain("Karyawan Uji" + (char) ('A' + j) + "Zq" + j);
					}
				}
			}
		}
		finally {
			pool.shutdownNow();
		}
	}

	private static LetterModel model(String name) {
		return new LetterModel("IZ-000001", "Cabang Uji", "SURAT PERMOHONAN IZIN", name, "E1", "Operator",
				"Dengan ini mengajukan izin.", List.of(new LetterModel.Field("Keperluan", "Uji")), List.of(),
				List.of(new LetterModel.SignatureBox("Pemohon", null, name, null)), null, null, "24-10-2026 10.00 Asia/Jakarta");
	}

}
