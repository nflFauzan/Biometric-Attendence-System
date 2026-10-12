-- Fase 4: izin dan cuti (docs/rencana-fase-4.md).

-- K-075: exclusion constraint butuh operator = untuk BIGINT di indeks GiST.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- K-070: jenis izin/cuti diatur Owner. "paid" NULL = belum ditentukan; baru dipakai di Fase 5 (Q-013).
CREATE TABLE leave_types (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name               VARCHAR(100)  NOT NULL,
    requires_document  BOOLEAN       NOT NULL DEFAULT FALSE,
    paid               BOOLEAN,
    description        VARCHAR(500),
    active             BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX leave_types_name_uq ON leave_types (lower(name));

-- Contoh awal; Owner bebas mengubah, menambah, atau menonaktifkan (bukan aturan bisnis).
INSERT INTO leave_types (name, description) VALUES
    ('Sakit', 'Contoh awal. Atur "wajib surat" sesuai kebijakan perusahaan.'),
    ('Cuti', 'Contoh awal. Kuota belum diterapkan (K-072).'),
    ('Izin', 'Contoh awal untuk keperluan pribadi/keluarga.');

-- K-071/K-074: pengajuan hari penuh untuk rentang tanggal. Pelaku dicatat dengan username (seperti koreksi).
CREATE TABLE leave_requests (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id     BIGINT         NOT NULL REFERENCES employees (id),
    leave_type_id   BIGINT         NOT NULL REFERENCES leave_types (id),
    start_date      DATE           NOT NULL,
    end_date        DATE           NOT NULL,
    reason          VARCHAR(1000)  NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    submitted_by    VARCHAR(100)   NOT NULL,
    submitted_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    decided_by      VARCHAR(100),
    decided_at      TIMESTAMPTZ,
    decision_note   VARCHAR(1000),
    cancelled_by    VARCHAR(100),
    cancelled_at    TIMESTAMPTZ,
    cancel_reason   VARCHAR(1000),
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT leave_requests_status_ck CHECK (status IN ('MENUNGGU', 'DISETUJUI', 'DITOLAK', 'DIBATALKAN')),
    CONSTRAINT leave_requests_range_ck CHECK (end_date >= start_date),
    -- K-080: batas teknis, bukan aturan bisnis.
    CONSTRAINT leave_requests_max_range_ck CHECK (end_date - start_date < 366),
    -- K-075: satu karyawan tidak boleh punya dua pengajuan aktif yang tanggalnya beririsan,
    -- termasuk bila dua pengajuan dikirim bersamaan.
    CONSTRAINT leave_requests_no_overlap EXCLUDE USING gist (
        employee_id WITH =,
        daterange(start_date, end_date, '[]') WITH &&
    ) WHERE (status IN ('MENUNGGU', 'DISETUJUI'))
);
CREATE INDEX leave_requests_status_idx ON leave_requests (status, start_date);
CREATE INDEX leave_requests_employee_idx ON leave_requests (employee_id, start_date);

-- K-078: isi file disimpan di disk (volume uploads) dengan nama acak; di sini hanya metadata.
CREATE TABLE leave_attachments (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    leave_request_id  BIGINT        NOT NULL REFERENCES leave_requests (id),
    original_name     VARCHAR(255)  NOT NULL,
    content_type      VARCHAR(50)   NOT NULL,
    size_bytes        BIGINT        NOT NULL,
    storage_key       VARCHAR(64)   NOT NULL UNIQUE,
    sha256            VARCHAR(64)   NOT NULL,
    uploaded_by       VARCHAR(100)  NOT NULL,
    uploaded_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX leave_attachments_request_idx ON leave_attachments (leave_request_id);
