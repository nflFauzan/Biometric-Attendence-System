package com.biometricattendance.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.biometricattendance.AttendanceFixtures;
import com.biometricattendance.calculation.DailyResult.AttendanceStatus;
import com.biometricattendance.report.ReportDtos.ReportDay;
import com.biometricattendance.report.ReportDtos.ReportEmployee;
import com.biometricattendance.report.ReportDtos.ReportFilter;
import com.biometricattendance.report.ReportDtos.ReportModel;

/**
 * Penyusunan model laporan. Pekan uji 05-09 Oktober 2026 (Senin-Jumat); aturan uji 08.00-17.00 dengan istirahat
 * 12.00-13.00, lembur mulai 17.00.
 */
class ReportServiceTests extends AttendanceFixtures {

	private static final LocalDate MON = LocalDate.parse("2026-10-05");

	private static final LocalDate FRI = LocalDate.parse("2026-10-09");

	@Autowired
	private ReportService reports;

	private record Setup(long branch, long employee, String pin, String sn) {
	}

	private Setup setup() throws Exception {
		long branch = createBranch();
		String pin = uniquePin();
		long employee = createEmployee(branch, pin);
		addFullRule(employee);
		jdbc.update("update work_rules set break_start = time '12:00', break_end = time '13:00' where employee_id = ?",
				employee);
		String sn = uniqueSerial();
		createDevice(branch, sn, ACCEPT_FROM);
		return new Setup(branch, employee, pin, sn);
	}

	private void scans(Setup s, String date, String in, String out) throws Exception {
		String[] rows = out == null ? new String[] { line(s.pin(), date + " " + in) }
				: new String[] { line(s.pin(), date + " " + in), line(s.pin(), date + " " + out) };
		postAttlog(s.sn(), lines(rows)).andExpect(status().isOk());
	}

	private ReportModel build(Setup s) {
		return reports.build(ReportType.REKAP_ABSENSI, new ReportFilter(MON, FRI, s.branch(), null, null));
	}

	private void validateOvertime(Setup s, String date, int minutes, long candidateSeconds) throws Exception {
		mockMvc.perform(post("/api/overtime/validations").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"items\":[{\"employeeId\":" + s.employee() + ",\"workDate\":\"" + date
					+ "\",\"approvedMinutes\":" + minutes + ",\"expectedCandidateSeconds\":" + candidateSeconds
					+ ",\"note\":\"Alasan validasi\"}]}"))
			.andExpect(status().isOk());
	}

	@Test
	void countingDaysCountsOnlyCompleteDays() throws Exception {
		Setup s = setup();
		scans(s, "2026-10-05", "08:00:00", "17:00:00");
		scans(s, "2026-10-06", "08:00:00", "17:00:00");
		scans(s, "2026-10-07", "08:00:00", "17:00:00");
		scans(s, "2026-10-08", "08:00:00", null);
		long type = jdbc.queryForObject("select id from leave_types order by id limit 1", Long.class);
		jdbc.update("""
				insert into leave_requests (employee_id, leave_type_id, start_date, end_date, reason, status,
				  submitted_by, submitted_at, decided_by, decided_at)
				values (?, ?, date '2026-10-09', date '2026-10-09', 'uji', 'DISETUJUI', 'tester', now(), 'tester', now())
				""", s.employee(), type);

		ReportModel model = build(s);

		assertThat(model.employees()).hasSize(1);
		ReportEmployee e = model.employees().get(0);
		assertThat(e.totals().countingDays()).isEqualTo(3);
		assertThat(e.totals().leaveDays()).isEqualTo(1);
		assertThat(e.totals().absentDays()).isZero();
		assertThat(e.days()).extracting(ReportDay::mark).containsExactly("V", "V", "V", "?", "I");
		assertThat(e.days()).extracting(ReportDay::status)
			.containsExactly(AttendanceStatus.LENGKAP, AttendanceStatus.LENGKAP, AttendanceStatus.LENGKAP,
					AttendanceStatus.TIDAK_LENGKAP, AttendanceStatus.IZIN);
		assertThat(e.days().get(0).dayName()).isEqualTo("Senin");
		assertThat(e.days().get(4).dayName()).isEqualTo("Jumat");
		assertThat(e.days().get(0).checkIn()).isEqualTo(LocalTime.of(8, 0));
		assertThat(e.days().get(0).checkOut()).isEqualTo(LocalTime.of(17, 0));
	}

	@Test
	void countingHoursSumsRegularWorkOnly() throws Exception {
		Setup s = setup();
		scans(s, "2026-10-05", "08:00:00", "17:00:00");
		scans(s, "2026-10-06", "09:00:00", "17:00:00");

		ReportEmployee e = build(s).employees().get(0);

		assertThat(e.totals().countingHours()).isEqualByComparingTo(new BigDecimal("15.00"));
		assertThat(e.totals().countingHours().scale()).isEqualTo(2);
		assertThat(e.days().get(0).regularHours()).isEqualByComparingTo("8.00");
		assertThat(e.days().get(1).regularHours()).isEqualByComparingTo("7.00");
		assertThat(e.totals().absentDays()).isEqualTo(3);
		assertThat(e.days().get(2).mark()).isEmpty();
		assertThat(e.days().get(2).status()).isEqualTo(AttendanceStatus.TIDAK_HADIR);
	}

	@Test
	void onlyValidatedOvertimeIsIncluded() throws Exception {
		Setup s = setup();
		scans(s, "2026-10-05", "08:00:00", "19:00:00");
		scans(s, "2026-10-06", "08:00:00", "19:00:00");
		validateOvertime(s, "2026-10-05", 120, 7200);
		validateOvertime(s, "2026-10-06", 0, 7200);

		ReportEmployee e = build(s).employees().get(0);

		assertThat(e.totals().validatedOvertimeHours()).isEqualByComparingTo("2.00");
		assertThat(e.days().get(0).validatedOvertimeHours()).isEqualByComparingTo("2.00");
		assertThat(e.days().get(1).validatedOvertimeHours()).isEqualByComparingTo("0.00");
		assertThat(e.totals().countingHours()).isEqualByComparingTo("16.00");
	}

	@Test
	void revalidationNeededOvertimeIsExcluded() throws Exception {
		Setup s = setup();
		scans(s, "2026-10-05", "08:00:00", "19:00:00");
		validateOvertime(s, "2026-10-05", 120, 7200);
		mockMvc.perform(post("/api/attendance/corrections").with(HR)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"employeeId\":" + s.employee()
					+ ",\"date\":\"2026-10-05\",\"checkOut\":\"20:00\",\"reason\":\"Jam pulang sebenarnya\"}"))
			.andExpect(status().isCreated());

		ReportEmployee e = build(s).employees().get(0);

		assertThat(e.totals().validatedOvertimeHours()).isEqualByComparingTo("0.00");
		assertThat(e.days().get(0).validatedOvertimeHours()).isEqualByComparingTo("0.00");
	}

	@Test
	void filtersAreApplied() throws Exception {
		Setup s = setup();
		long category = idOf(mockMvc.perform(post("/api/employee-categories").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"" + unique("K") + "\",\"name\":\"Supir\",\"active\":true}"))
			.andExpect(status().isCreated()));
		long driver = idOf(mockMvc.perform(post("/api/employees").with(OWNER)
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(employeeJson(s.branch(), unique("E"), uniquePin()).replace("\"categoryId\":1",
					"\"categoryId\":" + category)))
			.andExpect(status().isCreated()));

		ReportModel all = build(s);
		assertThat(all.employees()).hasSize(2);
		assertThat(all.filterLabels()).hasSize(3);
		assertThat(all.filterLabels().get(1)).isEqualTo("Semua kategori");
		assertThat(all.filterLabels().get(2)).isEqualTo("Semua karyawan");

		ReportModel filtered = reports.build(ReportType.DETAIL_ABSENSI,
				new ReportFilter(MON, FRI, s.branch(), category, driver));
		assertThat(filtered.employees()).hasSize(1);
		assertThat(filtered.employees().get(0).categoryName()).isEqualTo("Supir");
		assertThat(filtered.filterLabels()).contains("Supir");
		assertThat(filtered.filterLabels().get(0)).startsWith("Cabang ");
		assertThat(filtered.filterLabels().get(2)).startsWith("Karyawan ");
		assertThat(filtered.type()).isEqualTo(ReportType.DETAIL_ABSENSI);
		assertThat(filtered.title()).isEqualTo("Detail Absensi");
	}

	@Test
	void emptyResultStillHasHeader() throws Exception {
		long branch = createBranch();

		ReportModel model = reports.build(ReportType.REKAP_LEMBUR, new ReportFilter(MON, FRI, branch, null, null));

		assertThat(model.employees()).isEmpty();
		assertThat(model.title()).isEqualTo("Rekap Lembur");
		assertThat(model.periodLabel()).isEqualTo("05-10-2026 s.d. 09-10-2026");
		assertThat(model.dates()).hasSize(5).first().isEqualTo(MON);
		assertThat(model.filterLabels()).hasSize(3);
	}

	/** Ruling controller: hari setelah tanggal keluar kosong, tidak dihitung, meski ada scan. */
	@Test
	void daysAfterEndDateAreBlankAndUncounted() throws Exception {
		Setup s = setup();
		for (String d : List.of("2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09")) {
			scans(s, d, "08:00:00", "19:00:00");
		}
		jdbc.update("update employees set active = false, end_date = date '2026-10-07' where id = ?", s.employee());

		ReportEmployee e = build(s).employees().get(0);

		assertThat(e.days()).hasSize(5);
		assertThat(e.totals().countingDays()).isEqualTo(3);
		assertThat(e.totals().absentDays()).isZero();
		assertThat(e.totals().countingHours()).isEqualByComparingTo("24.00");
		for (ReportDay d : e.days().subList(3, 5)) {
			assertThat(d.status()).isNull();
			assertThat(d.mark()).isEmpty();
			assertThat(d.checkIn()).isNull();
			assertThat(d.checkOut()).isNull();
			assertThat(d.regularHours()).isEqualByComparingTo("0.00");
			assertThat(d.validatedOvertimeHours()).isEqualByComparingTo("0.00");
			assertThat(d.scanTimes()).isEmpty();
		}
		assertThat(e.days().get(2).mark()).isEqualTo("V");
	}

}
