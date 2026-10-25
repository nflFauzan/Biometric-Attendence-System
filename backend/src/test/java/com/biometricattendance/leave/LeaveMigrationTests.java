package com.biometricattendance.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.biometricattendance.AttendanceFixtures;

/** Migrasi V12: status baru, draft di luar aturan tumpang tindih, dan data awal templat per jenis izin. */
class LeaveMigrationTests extends AttendanceFixtures {

	@Autowired
	private DataSource dataSource;

	@Test
	void pendingStatusIsRenamedAndConstraintsAcceptNewStatuses() throws Exception {
		long branch = createBranch();
		long type = seededType("izin");
		insert(createEmployee(branch, null), type, "PERLU_TINJAU_HR", "2026-10-05", "2026-10-05");
		insert(createEmployee(branch, null), type, "PERLU_TINJAU_OWNER", "2026-10-05", "2026-10-05");
		insert(createEmployee(branch, null), type, "DRAFT", "2026-10-05", "2026-10-05");

		long other = createEmployee(branch, null);
		assertThatThrownBy(() -> insert(other, type, "MENUNGGU", "2026-10-05", "2026-10-05"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("leave_requests_status_ck");
	}

	@Test
	void draftsAreOutsideOverlapConstraint() throws Exception {
		long type = seededType("izin");
		long employee = createEmployee(createBranch(), null);

		insert(employee, type, "DRAFT", "2026-11-02", "2026-11-04");
		insert(employee, type, "DRAFT", "2026-11-02", "2026-11-04");
		insert(employee, type, "PERLU_TINJAU_HR", "2026-11-03", "2026-11-05");

		assertThatThrownBy(() -> insert(employee, type, "PERLU_TINJAU_HR", "2026-11-04", "2026-11-06"))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("leave_requests_no_overlap");
	}

	/** K-126 (V16): satu pengajuan yang ditolak hanya punya satu pengajuan ulang. */
	@Test
	void resubmittedFromIsUniquePerSource() throws Exception {
		long type = seededType("izin");
		long employee = createEmployee(createBranch(), null);
		insert(employee, type, "DITOLAK", "2026-12-01", "2026-12-01");
		long source = jdbc.queryForObject("select max(id) from leave_requests", Long.class);
		insert(employee, type, "DRAFT", "2026-12-01", "2026-12-01");
		long first = jdbc.queryForObject("select max(id) from leave_requests", Long.class);
		insert(employee, type, "DRAFT", "2026-12-01", "2026-12-01");
		long second = jdbc.queryForObject("select max(id) from leave_requests", Long.class);

		jdbc.update("update leave_requests set resubmitted_from_id = ? where id = ?", source, first);
		assertThatThrownBy(
				() -> jdbc.update("update leave_requests set resubmitted_from_id = ? where id = ?", source, second))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("ux_leave_requests_resubmitted_from");
	}

	@Test
	void seedTypesGetTemplates() {
		assertThat(templateOf("cuti")).containsEntry("letter_template", "CUTI")
			.containsEntry("requires_owner_approval", true);
		assertThat(templateOf("sakit")).containsEntry("letter_template", "SAKIT")
			.containsEntry("requires_owner_approval", true);
		assertThat(templateOf("izin")).containsEntry("letter_template", "IZIN")
			.containsEntry("requires_owner_approval", false);
	}

	/** V13: keputusan lama (satu tahap) menjadi satu baris tahap HR. Dijalankan di schema terpisah: V1-V12 lalu V13. */
	@Test
	void legacyDecisionsBecomeApprovalRows() {
		jdbc.execute("drop schema if exists legacy_test cascade");
		Flyway upTo12 = Flyway.configure().dataSource(dataSource).schemas("legacy_test").target("12").load();
		upTo12.migrate();

		jdbc.update("insert into legacy_test.branches (code, name) values ('LG', 'Cabang lama')");
		jdbc.update("""
				insert into legacy_test.employees (employee_number, full_name, branch_id, category_id, join_date)
				select 'LG-1', 'Karyawan Lama', (select min(id) from legacy_test.branches),
				       (select min(id) from legacy_test.employee_categories), date '2026-01-01'
				""");
		long employee = jdbc.queryForObject("select id from legacy_test.employees where employee_number = 'LG-1'",
				Long.class);
		long type = jdbc.queryForObject("select min(id) from legacy_test.leave_types", Long.class);
		legacy(employee, type, "DISETUJUI", "2026-10-05", "hr-lama", "Silakan");
		legacy(employee, type, "DITOLAK", "2026-10-06", "owner-lama", "Bentrok");
		legacy(employee, type, "DIBATALKAN", "2026-10-07", null, null);
		legacy(employee, type, "PERLU_TINJAU_HR", "2026-10-08", null, null);

		Flyway.configure().dataSource(dataSource).schemas("legacy_test").target("13").load().migrate();

		List<Map<String, Object>> rows = jdbc.queryForList("""
				select r.status, a.stage, a.approved, a.decided_by, a.method, a.recorded_by, a.note,
				       a.decided_at = r.decided_at as same_time
				from legacy_test.leave_approvals a join legacy_test.leave_requests r on r.id = a.leave_request_id
				order by r.start_date
				""");
		assertThat(rows).hasSize(2);
		assertThat(rows.get(0)).containsEntry("status", "DISETUJUI")
			.containsEntry("stage", "HR")
			.containsEntry("approved", true)
			.containsEntry("decided_by", "hr-lama")
			.containsEntry("method", "WEB")
			.containsEntry("recorded_by", "hr-lama")
			.containsEntry("note", "Silakan")
			.containsEntry("same_time", true);
		assertThat(rows.get(1)).containsEntry("status", "DITOLAK")
			.containsEntry("approved", false)
			.containsEntry("decided_by", "owner-lama")
			.containsEntry("note", "Bentrok");
		jdbc.execute("drop schema legacy_test cascade");
	}

	/**
	 * V11 -> terbaru (V12, V13, V15) pada schema terpisah: status lama diganti nama, keputusan lama dan pembatalan
	 * pascapersetujuan mendapat satu baris tahap HR, pembatalan tanpa keputusan tidak mendapat riwayat.
	 */
	@Test
	void migrationFromV11ToLatestBackfillsApprovalHistory() {
		jdbc.execute("drop schema if exists legacy_full cascade");
		Flyway.configure().dataSource(dataSource).schemas("legacy_full").target("11").load().migrate();

		jdbc.update("insert into legacy_full.branches (code, name) values ('LF', 'Cabang lama')");
		jdbc.update("""
				insert into legacy_full.employees (employee_number, full_name, branch_id, category_id, join_date)
				select 'LF-1', 'Karyawan Lama', (select min(id) from legacy_full.branches),
				       (select min(id) from legacy_full.employee_categories), date '2026-01-01'
				""");
		long employee = jdbc.queryForObject("select id from legacy_full.employees where employee_number = 'LF-1'",
				Long.class);
		long type = jdbc.queryForObject("select min(id) from legacy_full.leave_types", Long.class);
		legacyFull(employee, type, "MENUNGGU", "2026-10-05", null, null, false);
		legacyFull(employee, type, "DISETUJUI", "2026-10-06", "hr-lama", "Silakan", false);
		legacyFull(employee, type, "DITOLAK", "2026-10-07", "owner-lama", "Bentrok", false);
		legacyFull(employee, type, "DIBATALKAN", "2026-10-08", "hr-lama", "Boleh", true);
		legacyFull(employee, type, "DIBATALKAN", "2026-10-09", null, null, true);

		Flyway.configure().dataSource(dataSource).schemas("legacy_full").load().migrate();

		List<Map<String, Object>> statuses = jdbc.queryForList(
				"select start_date::text as d, status from legacy_full.leave_requests order by start_date");
		assertThat(statuses).extracting(m -> m.get("status"))
			.containsExactly("PERLU_TINJAU_HR", "DISETUJUI", "DITOLAK", "DIBATALKAN", "DIBATALKAN");

		List<Map<String, Object>> rows = jdbc.queryForList("""
				select r.start_date::text as d, r.status, a.stage, a.approved, a.decided_by, a.method, a.recorded_by,
				       a.note, a.decided_at = r.decided_at as same_time
				from legacy_full.leave_approvals a join legacy_full.leave_requests r on r.id = a.leave_request_id
				order by r.start_date
				""");
		assertThat(rows).extracting(m -> m.get("d")).containsExactly("2026-10-06", "2026-10-07", "2026-10-08");
		assertThat(rows.get(0)).containsEntry("approved", true).containsEntry("stage", "HR");
		assertThat(rows.get(1)).containsEntry("approved", false).containsEntry("decided_by", "owner-lama");
		assertThat(rows.get(2)).containsEntry("status", "DIBATALKAN")
			.containsEntry("stage", "HR")
			.containsEntry("approved", true)
			.containsEntry("decided_by", "hr-lama")
			.containsEntry("method", "WEB")
			.containsEntry("recorded_by", "hr-lama")
			.containsEntry("note", "Boleh")
			.containsEntry("same_time", true);
		jdbc.execute("drop schema legacy_full cascade");
	}

	private void legacyFull(long employee, long type, String status, String date, String decidedBy, String note,
			boolean cancelled) {
		jdbc.update("""
				insert into legacy_full.leave_requests (employee_id, leave_type_id, start_date, end_date, reason, status,
				  submitted_by, submitted_at, decided_by, decided_at, decision_note, cancelled_by, cancelled_at,
				  cancel_reason)
				values (?, ?, ?::date, ?::date, 'uji', ?, 'tester', now(), ?, case when ?::varchar is null then null
				  else now() end, ?, case when ? then 'owner-lama' end, case when ? then now() end,
				  case when ? then 'dibatalkan' end)
				""", employee, type, date, date, status, decidedBy, decidedBy, note, cancelled, cancelled, cancelled);
	}

	private void legacy(long employee, long type, String status, String date, String decidedBy, String note) {
		jdbc.update("""
				insert into legacy_test.leave_requests (employee_id, leave_type_id, start_date, end_date, reason, status,
				  submitted_by, submitted_at, decided_by, decided_at, decision_note)
				values (?, ?, ?::date, ?::date, 'uji', ?, 'tester', now(), ?, case when ?::varchar is null then null
				  else now() end, ?)
				""", employee, type, date, date, status, decidedBy, decidedBy, note);
	}

	private Map<String, Object> templateOf(String name) {
		return jdbc.queryForMap(
				"select letter_template, requires_owner_approval from leave_types where lower(name) = ?", name);
	}

	private long seededType(String name) {
		return jdbc.queryForObject("select id from leave_types where lower(name) = ?", Long.class, name);
	}

	private void insert(long employee, long type, String status, String from, String to) {
		jdbc.update("""
				insert into leave_requests (employee_id, leave_type_id, start_date, end_date, reason, status,
				  submitted_by, submitted_at)
				values (?, ?, ?::date, ?::date, 'uji', ?, 'tester', now())
				""", employee, type, from, to, status);
	}

}
