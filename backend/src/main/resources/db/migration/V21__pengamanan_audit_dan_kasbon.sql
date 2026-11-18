-- V21: pengamanan jejak audit dan data kasbon di level database (DSN-001 v0.6, temuan F-3, F-4, F-6; K-144).

-- F-3. Jejak audit append-only. Sebelumnya larangan ubah/hapus hanya dijaga aplikasi (tidak ada operasi ubah/hapus
-- di AuditService); siapa pun yang memegang kredensial database tetap bisa mengubah log. Trigger ini menolak UPDATE,
-- DELETE, dan TRUNCATE untuk semua pemakai, termasuk pemilik tabel. Koreksi atas catatan audit ditulis sebagai catatan
-- baru, bukan dengan mengubah catatan lama.
CREATE FUNCTION audit_logs_append_only() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_logs hanya boleh ditambah (append-only); % ditolak', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER audit_logs_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_append_only();

CREATE TRIGGER audit_logs_no_truncate
    BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION audit_logs_append_only();

-- F-4. Riwayat kasbon tidak boleh ikut terhapus. ON DELETE CASCADE dari V19 diganti RESTRICT: karyawan yang punya
-- kasbon tidak bisa dihapus (karyawan dinonaktifkan lewat tanggal keluar, bukan dihapus), dan kasbon yang punya
-- riwayat cicilan tidak bisa dihapus (pembatalan memakai status DIBATALKAN).
ALTER TABLE employee_loans DROP CONSTRAINT employee_loans_employee_id_fkey;
ALTER TABLE employee_loans
    ADD CONSTRAINT employee_loans_employee_id_fkey
        FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE RESTRICT;

ALTER TABLE loan_repayments DROP CONSTRAINT loan_repayments_loan_id_fkey;
ALTER TABLE loan_repayments
    ADD CONSTRAINT loan_repayments_loan_id_fkey
        FOREIGN KEY (loan_id) REFERENCES employee_loans (id) ON DELETE RESTRICT;

ALTER TABLE employee_loans
    ADD CONSTRAINT employee_loans_status_ck CHECK (status IN ('AKTIF', 'LUNAS', 'DIBATALKAN'));

-- F-6. Kunci primer seragam dengan tabel lain (GENERATED ALWAYS AS IDENTITY). V18/V19 memakai bigserial
-- (sequence + DEFAULT). Nilai identity dilanjutkan dari id terbesar agar data yang sudah ada tidak bentrok.
DO
$$
DECLARE
    t       text;
    next_id bigint;
BEGIN
    FOREACH t IN ARRAY ARRAY ['overtime_validations', 'employee_loans', 'loan_repayments']
        LOOP
            EXECUTE format('ALTER TABLE %I ALTER COLUMN id DROP DEFAULT', t);
            EXECUTE format('DROP SEQUENCE IF EXISTS %I', t || '_id_seq');
            EXECUTE format('SELECT coalesce(max(id), 0) + 1 FROM %I', t) INTO next_id;
            EXECUTE format('ALTER TABLE %I ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (START WITH %s)', t,
                           next_id);
        END LOOP;
END;
$$;
