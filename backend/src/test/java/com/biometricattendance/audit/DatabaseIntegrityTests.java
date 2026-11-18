package com.biometricattendance.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import com.biometricattendance.AttendanceFixtures;

/**
 * Pengamanan di level database dari V21 (DSN-001 v0.6, F-3/F-4/F-6; K-144): jejak audit append-only, riwayat kasbon
 * tidak ikut terhapus, dan kunci primer kasbon/validasi lembur memakai IDENTITY seperti tabel lain.
 */
class DatabaseIntegrityTests extends AttendanceFixtures {

	@Test
	void auditLogAcceptsInsertButRejectsUpdateDeleteAndTruncate() {
		String entityId = unique("audit-");
		jdbc.update("insert into audit_logs (actor_username, action, entity_type, entity_id) values ('uji', 'UJI', 'UJI', ?)",
				entityId);
		assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_id = ?", Long.class, entityId))
			.isEqualTo(1);

		assertThatThrownBy(() -> jdbc.update("update audit_logs set reason = 'diubah' where entity_id = ?", entityId))
			.isInstanceOf(DataAccessException.class)
			.rootCause()
			.hasMessageContaining("append-only");
		assertThatThrownBy(() -> jdbc.update("delete from audit_logs where entity_id = ?", entityId))
			.isInstanceOf(DataAccessException.class)
			.rootCause()
			.hasMessageContaining("append-only");
		assertThatThrownBy(() -> jdbc.execute("truncate audit_logs"))
			.isInstanceOf(DataAccessException.class)
			.rootCause()
			.hasMessageContaining("append-only");

		assertThat(jdbc.queryForObject("select reason from audit_logs where entity_id = ?", String.class, entityId))
			.isNull();
	}

	@Test
	void employeeWithLoanCannotBeDeleted() throws Exception {
		long employee = createEmployee(createBranch(), uniquePin());
		insertLoan(employee);

		assertThatThrownBy(() -> jdbc.update("delete from employees where id = ?", employee))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("employee_loans_employee_id_fkey");
		assertThat(jdbc.queryForObject("select count(*) from employee_loans where employee_id = ?", Long.class, employee))
			.isEqualTo(1);
	}

	@Test
	void loanWithRepaymentCannotBeDeleted() throws Exception {
		long loan = insertLoan(createEmployee(createBranch(), uniquePin()));
		jdbc.update("""
				insert into loan_repayments (loan_id, repayment_date, amount, period_label, report_type)
				values (?, date '2026-10-08', 100000.00, 'Minggu 1', 'UPAH_MINGGUAN')
				""", loan);

		assertThatThrownBy(() -> jdbc.update("delete from employee_loans where id = ?", loan))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("loan_repayments_loan_id_fkey");
	}

	@Test
	void loanStatusIsRestrictedToKnownValues() throws Exception {
		long loan = insertLoan(createEmployee(createBranch(), uniquePin()));

		assertThatThrownBy(() -> jdbc.update("update employee_loans set status = 'HILANG' where id = ?", loan))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("employee_loans_status_ck");
	}

	@Test
	void loanAndOvertimeTablesUseAlwaysIdentity() {
		List<String> generation = jdbc.queryForList("""
				select identity_generation from information_schema.columns
				where table_schema = current_schema() and column_name = 'id'
				  and table_name in ('overtime_validations', 'employee_loans', 'loan_repayments')
				order by table_name
				""", String.class);
		assertThat(generation).containsExactly("ALWAYS", "ALWAYS", "ALWAYS");
		assertThat(jdbc.queryForList("""
				select column_default from information_schema.columns
				where table_schema = current_schema() and column_name = 'id'
				  and table_name in ('overtime_validations', 'employee_loans', 'loan_repayments')
				""", String.class)).containsOnlyNulls();
	}

	private long insertLoan(long employee) {
		return jdbc.queryForObject("""
				insert into employee_loans (employee_id, loan_number, loan_date, total_amount, installment_amount,
				    remaining_amount, status)
				values (?, ?, date '2026-10-01', 1000000.00, 100000.00, 1000000.00, 'AKTIF')
				returning id
				""", Long.class, employee, unique("KB-"));
	}

}
