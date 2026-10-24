-- Persetujuan ganda surat izin (spec 2026-10-24 bagian 5 dan 12): riwayat keputusan per tahap.
-- Satu baris per tahap (HR, OWNER). Keputusan lewat web: method WEB dan pencatat = pemberi keputusan.
-- Kolom bukti dan spesimen dipakai tahap berikutnya (keputusan Owner yang dicatat HR, PDF).

CREATE TABLE leave_approvals (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    leave_request_id        BIGINT        NOT NULL REFERENCES leave_requests (id),
    stage                   VARCHAR(10)   NOT NULL,
    approved                BOOLEAN       NOT NULL,
    decided_by              VARCHAR(100)  NOT NULL,
    decided_at              TIMESTAMPTZ   NOT NULL,
    method                  VARCHAR(10)   NOT NULL,
    recorded_by             VARCHAR(100)  NOT NULL,
    recorded_at             TIMESTAMPTZ   NOT NULL,
    note                    VARCHAR(1000),
    evidence_storage_key    VARCHAR(100),
    evidence_original_name  VARCHAR(255),
    evidence_content_type   VARCHAR(100),
    evidence_size_bytes     BIGINT,
    evidence_sha256         VARCHAR(64),
    signature_storage_key   VARCHAR(100),
    CONSTRAINT leave_approvals_stage_ck CHECK (stage IN ('HR', 'OWNER')),
    CONSTRAINT leave_approvals_method_ck CHECK (method IN ('WEB', 'WHATSAPP', 'LISAN', 'TERTULIS')),
    CONSTRAINT leave_approvals_stage_uq UNIQUE (leave_request_id, stage)
);

-- Keputusan lama (satu tahap, lewat web) menjadi satu baris tahap HR.
INSERT INTO leave_approvals (leave_request_id, stage, approved, decided_by, decided_at, method, recorded_by,
                             recorded_at, note)
SELECT id, 'HR', status = 'DISETUJUI', decided_by, decided_at, 'WEB', decided_by, decided_at, decision_note
FROM leave_requests
WHERE status IN ('DISETUJUI', 'DITOLAK') AND decided_by IS NOT NULL AND decided_at IS NOT NULL;
