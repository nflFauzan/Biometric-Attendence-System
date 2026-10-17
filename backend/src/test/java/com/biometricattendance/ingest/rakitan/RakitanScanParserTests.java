package com.biometricattendance.ingest.rakitan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/** Terjemahan scan alat rakitan ke format netral (docs/protokol-alat-rakitan.md §5). */
class RakitanScanParserTests {

	private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
	private static final String ID = "3f2b8c1e-5d0a-4b7e-9a51-0c6f2d8e4a17";

	private final RakitanScanParser parser = new RakitanScanParser(JsonMapper.builder().build());

	@Test
	void validScanIsConvertedToBranchLocalTime() {
		RakitanScanParser.Parsed parsed = parser.parse(List.of(scan(ID, "1696", "2026-10-17T01:01:02Z")), JAKARTA);

		assertThat(parsed.rejected()).isEmpty();
		assertThat(parsed.accepted()).containsExactly(ID);
		assertThat(parsed.events()).hasSize(1);
		assertThat(parsed.events().get(0).pin()).isEqualTo("1696");
		assertThat(parsed.events().get(0).localTime()).isEqualTo(LocalDateTime.of(2026, 10, 17, 8, 1, 2));
		assertThat(parsed.events().get(0).verifyMode()).isEqualTo("FP");
		assertThat(parsed.events().get(0).deviceScanId()).isEqualTo(UUID.fromString(ID));
		assertThat(parsed.events().get(0).rawLine()).contains(ID).contains("2026-10-17T01:01:02Z");
	}

	@Test
	void timeWithoutOffsetIsRejectedButIdAccepted() {
		RakitanScanParser.Parsed parsed = parser.parse(List.of(scan(ID, "1696", "2026-10-17T08:01:02")), JAKARTA);

		assertThat(parsed.events()).isEmpty();
		assertThat(parsed.accepted()).containsExactly(ID);
		assertThat(parsed.rejected()).hasSize(1);
		assertThat(parsed.rejected().get(0).reason()).isEqualTo("Waktu harus ISO-8601 dengan offset");
		assertThat(parsed.rejected().get(0).rawLine()).contains("2026-10-17T08:01:02");
	}

	@Test
	void missingIdIsRejectedAndNotAccepted() {
		RakitanScanParser.Parsed parsed = parser.parse(List.of(scan(null, "1696", "2026-10-17T08:01:02+07:00")),
				JAKARTA);

		assertThat(parsed.events()).isEmpty();
		assertThat(parsed.accepted()).isEmpty();
		assertThat(parsed.rejected().get(0).reason()).isEqualTo("ID scan kosong");
	}

	@Test
	void nonUuidIdIsRejectedButAccepted() {
		RakitanScanParser.Parsed parsed = parser.parse(List.of(scan("abc", "1696", "2026-10-17T08:01:02+07:00")),
				JAKARTA);

		assertThat(parsed.events()).isEmpty();
		assertThat(parsed.accepted()).containsExactly("abc");
		assertThat(parsed.rejected().get(0).reason()).isEqualTo("ID scan bukan UUID");
	}

	@Test
	void blankOrLongPinIsRejected() {
		RakitanScanParser.Parsed parsed = parser.parse(List.of(scan(ID, "", "2026-10-17T08:01:02+07:00"),
				scan(UUID.randomUUID().toString(), "1".repeat(21), "2026-10-17T08:01:02+07:00")), JAKARTA);

		assertThat(parsed.events()).isEmpty();
		assertThat(parsed.accepted()).hasSize(2);
		assertThat(parsed.rejected()).extracting(r -> r.reason())
			.containsExactly("PIN kosong atau lebih dari 20 karakter", "PIN kosong atau lebih dari 20 karakter");
	}

	@Test
	void longVerifyIsRejectedSoBatchCanStillBeSaved() {
		Map<String, Object> bad = scan(UUID.randomUUID().toString(), "1696", "2026-10-17T08:01:02+07:00");
		bad.put("verify", "FINGERPRINT");
		RakitanScanParser.Parsed parsed = parser.parse(List.of(scan(ID, "1696", "2026-10-17T08:01:02+07:00"), bad),
				JAKARTA);

		assertThat(parsed.events()).hasSize(1);
		assertThat(parsed.accepted()).hasSize(2);
		assertThat(parsed.rejected()).extracting(r -> r.reason())
			.containsExactly("Cara verifikasi lebih dari 10 karakter");
	}

	@Test
	void timeOutsideSaneYearsIsRejected() {
		RakitanScanParser.Parsed parsed = parser.parse(List.of(scan(ID, "1696", "+300000-01-01T00:00:00+07:00"),
				scan(UUID.randomUUID().toString(), "1696", "1970-01-01T00:00:00+07:00")), JAKARTA);

		assertThat(parsed.events()).isEmpty();
		assertThat(parsed.accepted()).hasSize(2);
		assertThat(parsed.rejected()).extracting(r -> r.reason())
			.containsExactly("Tahun waktu scan di luar 2000–2100", "Tahun waktu scan di luar 2000–2100");
	}

	private static Map<String, Object> scan(String id, String pin, String time) {
		Map<String, Object> scan = new HashMap<>();
		if (id != null) {
			scan.put("id", id);
		}
		scan.put("pin", pin);
		scan.put("time", time);
		scan.put("verify", "FP");
		return scan;
	}

}
