package com.biometricattendance.ingest.zkpush;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.biometricattendance.ingest.ScanEvent;

/** Format ATTLOG dari tangkapan X401 (docs/panduan-mesin-x401.md §7), dengan data sintetis. */
class ZkPushParserTests {

	@Test
	void parsesTabSeparatedLinesLikeX401() {
		ZkPushParser.Batch batch = ZkPushParser.parseAttlog("9001\t2026-10-12 08:01:02\t0\t1\n"
				+ "9002\t2026-10-12 17:05:00\t1\t1\t0\t0\n");

		assertThat(batch.rejected()).isEmpty();
		assertThat(batch.events()).hasSize(2);
		ScanEvent first = batch.events().get(0);
		assertThat(first.pin()).isEqualTo("9001");
		assertThat(first.localTime()).isEqualTo(LocalDateTime.of(2026, 10, 12, 8, 1, 2));
		assertThat(first.statusCode()).isEqualTo("0");
		assertThat(first.verifyMode()).isEqualTo("1");
		assertThat(first.rawLine()).isEqualTo("9001\t2026-10-12 08:01:02\t0\t1");
		assertThat(batch.events().get(1).statusCode()).isEqualTo("1");
	}

	@Test
	void acceptsCrLfAndSkipsBlankLines() {
		ZkPushParser.Batch batch = ZkPushParser.parseAttlog("9001\t2026-10-12 08:01:02\t0\t1\r\n\r\n");
		assertThat(batch.events()).hasSize(1);
		assertThat(batch.events().get(0).rawLine()).doesNotContain("\r");
	}

	@Test
	void statusAndVerifyColumnsAreOptional() {
		ScanEvent event = ZkPushParser.parseAttlog("9001\t2026-10-12 08:01:02").events().get(0);
		assertThat(event.statusCode()).isNull();
		assertThat(event.verifyMode()).isNull();
	}

	@Test
	void brokenLinesAreRejectedWithReasonAndDoNotStopValidOnes() {
		ZkPushParser.Batch batch = ZkPushParser.parseAttlog(String.join("\n", "rusak-tanpa-tab",
				"\t2026-10-12 08:01:02\t0\t1", "9001\t12/10/2026 08:01\t0\t1", "123456789012345678901\t2026-10-12 08:01:02",
				"9002\t2026-10-12 08:01:02\t0\t1"));

		assertThat(batch.events()).extracting(ScanEvent::pin).containsExactly("9002");
		assertThat(batch.rejected()).extracting(r -> r.reason())
			.containsExactly("Kolom kurang: butuh PIN dan waktu", "PIN kosong", "Format waktu tidak dikenali",
					"PIN lebih dari 20 karakter");
		assertThat(batch.rejected().get(0).rawLine()).isEqualTo("rusak-tanpa-tab");
	}

}
