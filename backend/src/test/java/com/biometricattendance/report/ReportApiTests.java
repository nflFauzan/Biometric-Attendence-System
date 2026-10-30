package com.biometricattendance.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.biometricattendance.AttendanceFixtures;

/** Endpoint laporan: pratinjau JSON tidak dicatat, setiap unduhan dicatat sebagai EXPORT (K-105, AUD-009). */
class ReportApiTests extends AttendanceFixtures {

	private static final String RANGE = "from=2026-10-05&to=2026-10-09";

	private long exportCount(String slug) {
		return jdbc.queryForObject(
				"select count(*) from audit_logs where action = 'EXPORT' and entity_type = 'REPORT' and entity_id = ?",
				Long.class, slug);
	}

	@Test
	void previewReturnsModelJson() throws Exception {
		long branch = createBranch();
		createEmployee(branch, uniquePin());
		mockMvc.perform(get("/api/reports/rekap-absensi?" + RANGE + "&branchId=" + branch).with(HR))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Rekap Absensi"))
			.andExpect(jsonPath("$.dates.length()").value(5))
			.andExpect(jsonPath("$.employees.length()").value(1));
	}

	@Test
	void previewIsNotAudited() throws Exception {
		mockMvc.perform(get("/api/reports/detail-absensi?" + RANGE).with(HR)).andExpect(status().isOk());
		assertThat(exportCount("detail-absensi")).isZero();
	}

	@Test
	void xlsxDownloadHasHeadersAndIsAudited() throws Exception {
		long before = exportCount("rekap-absensi");
		MvcResult result = mockMvc.perform(get("/api/reports/rekap-absensi.xlsx?" + RANGE).with(HR))
			.andExpect(status().isOk())
			.andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
			.andExpect(header().string("Content-Disposition",
					"attachment; filename=\"rekap-absensi_2026-10-05_2026-10-09.xlsx\""))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andReturn();
		byte[] body = result.getResponse().getContentAsByteArray();
		assertThat(new String(body, 0, 2)).isEqualTo("PK");
		assertThat(exportCount("rekap-absensi")).isEqualTo(before + 1);
		String after = jdbc.queryForObject("select after_data from audit_logs where action = 'EXPORT' "
				+ "and entity_id = 'rekap-absensi' order by id desc limit 1", String.class);
		assertThat(after).contains("2026-10-05").contains("2026-10-09");
	}

	@Test
	void pdfDownloadIsAudited() throws Exception {
		long before = exportCount("rekap-lembur");
		MvcResult result = mockMvc.perform(get("/api/reports/rekap-lembur.pdf?" + RANGE).with(OWNER))
			.andExpect(status().isOk())
			.andExpect(content().contentType("application/pdf"))
			.andExpect(header().string("Content-Disposition",
					"attachment; filename=\"rekap-lembur_2026-10-05_2026-10-09.pdf\""))
			.andReturn();
		assertThat(new String(result.getResponse().getContentAsByteArray(), 0, 4)).isEqualTo("%PDF");
		assertThat(exportCount("rekap-lembur")).isEqualTo(before + 1);
	}

	@Test
	void unknownSlugIs404() throws Exception {
		mockMvc.perform(get("/api/reports/tidak-ada?" + RANGE).with(HR))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Jenis laporan tidak dikenal"));
		mockMvc.perform(get("/api/reports/tidak-ada.xlsx?" + RANGE).with(HR)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/reports/tidak-ada.pdf?" + RANGE).with(HR)).andExpect(status().isNotFound());
	}

	@Test
	void missingPeriodIs400() throws Exception {
		mockMvc.perform(get("/api/reports/rekap-absensi?from=2026-10-05").with(HR))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Periode wajib diisi"));
		mockMvc.perform(get("/api/reports/rekap-absensi.xlsx?to=2026-10-05").with(HR))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Periode wajib diisi"));
	}

	@Test
	void tooLongRangeIs400() throws Exception {
		mockMvc.perform(get("/api/reports/rekap-absensi?from=2026-01-01&to=2026-12-31").with(HR))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/reports/rekap-absensi.pdf?from=2026-01-01&to=2026-12-31").with(HR))
			.andExpect(status().isBadRequest());
	}

}
