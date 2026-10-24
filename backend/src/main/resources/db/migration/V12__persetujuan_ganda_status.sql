-- Persetujuan ganda surat izin (spec 2026-10-24 §5): status baru, kolom surat, dan pengaturan per jenis izin.
-- Status MENUNGGU diganti PERLU_TINJAU_HR; DRAFT berada di luar aturan tumpang tindih.

ALTER TABLE leave_requests DROP CONSTRAINT leave_requests_status_ck;
ALTER TABLE leave_requests DROP CONSTRAINT leave_requests_no_overlap;

UPDATE leave_requests SET status = 'PERLU_TINJAU_HR' WHERE status = 'MENUNGGU';

ALTER TABLE leave_requests ADD CONSTRAINT leave_requests_status_ck CHECK (status IN
    ('DRAFT', 'PERLU_TINJAU_HR', 'PERLU_TINJAU_OWNER', 'DISETUJUI', 'DITOLAK', 'DIBATALKAN'));

-- K-075: satu karyawan tidak boleh punya dua pengajuan aktif yang tanggalnya beririsan (draft tidak dihitung).
ALTER TABLE leave_requests ADD CONSTRAINT leave_requests_no_overlap EXCLUDE USING gist (
    employee_id WITH =,
    daterange(start_date, end_date, '[]') WITH &&
) WHERE (status IN ('PERLU_TINJAU_HR', 'PERLU_TINJAU_OWNER', 'DISETUJUI'));

ALTER TABLE leave_requests
    ADD COLUMN requires_owner_approval boolean,
    ADD COLUMN letter_template varchar(10),
    ADD COLUMN contact_phone varchar(30),
    ADD COLUMN leave_address varchar(300),
    ADD COLUMN delegate_to varchar(150),
    ADD COLUMN health_facility varchar(150),
    ADD COLUMN verification_token varchar(40) UNIQUE,
    ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE leave_requests ALTER COLUMN submitted_at DROP NOT NULL;
ALTER TABLE leave_requests ALTER COLUMN submitted_by DROP NOT NULL;

-- Draft boleh belum lengkap; kelengkapan diperiksa server saat dikirim.
ALTER TABLE leave_requests ALTER COLUMN leave_type_id DROP NOT NULL;
ALTER TABLE leave_requests ALTER COLUMN start_date DROP NOT NULL;
ALTER TABLE leave_requests ALTER COLUMN end_date DROP NOT NULL;
ALTER TABLE leave_requests ALTER COLUMN reason DROP NOT NULL;

UPDATE leave_requests
SET created_at = submitted_at,
    requires_owner_approval = false,
    letter_template = 'UMUM',
    verification_token = replace(gen_random_uuid()::text, '-', '');

ALTER TABLE leave_types
    ADD COLUMN requires_owner_approval boolean NOT NULL DEFAULT false,
    ADD COLUMN letter_template varchar(10) NOT NULL DEFAULT 'UMUM',
    ADD CONSTRAINT leave_types_letter_template_ck CHECK (letter_template IN ('UMUM', 'IZIN', 'CUTI', 'SAKIT'));

-- Data awal saja; Owner bebas mengubahnya.
UPDATE leave_types SET letter_template = 'CUTI', requires_owner_approval = true WHERE lower(name) = 'cuti';
UPDATE leave_types SET letter_template = 'SAKIT', requires_owner_approval = true WHERE lower(name) = 'sakit';
UPDATE leave_types SET letter_template = 'IZIN', requires_owner_approval = false WHERE lower(name) = 'izin';
