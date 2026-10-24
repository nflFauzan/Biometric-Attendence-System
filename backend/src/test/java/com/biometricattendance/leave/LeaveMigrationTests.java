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
