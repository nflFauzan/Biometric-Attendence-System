-- Spesimen tanda tangan per akun (spec 2026-10-24 bagian 9). Satu spesimen per akun; berkasnya ada di volume
-- lampiran (storage_key). Salinan beku untuk tahap yang disetujui disimpan di leave_approvals.signature_storage_key,
-- dan salinan pemohon saat mengirim di leave_requests.applicant_signature_key.

CREATE TABLE user_signatures (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_account_id  BIGINT       NOT NULL UNIQUE REFERENCES user_accounts (id),
    storage_key      VARCHAR(100) NOT NULL,
    sha256           VARCHAR(64)  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

ALTER TABLE leave_requests ADD COLUMN applicant_signature_key VARCHAR(100);
