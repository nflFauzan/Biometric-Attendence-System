-- Migrasi V19: Buku Kasbon dan Riwayat Pembayaran/Cicilan Karyawan (K-037, K-105)

CREATE TABLE employee_loans (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    loan_number VARCHAR(64) NOT NULL UNIQUE,
    loan_date DATE NOT NULL,
    total_amount NUMERIC(14, 2) NOT NULL CHECK (total_amount > 0),
    installment_amount NUMERIC(14, 2) NOT NULL CHECK (installment_amount > 0),
    remaining_amount NUMERIC(14, 2) NOT NULL CHECK (remaining_amount >= 0),
    status VARCHAR(32) NOT NULL DEFAULT 'AKTIF', -- 'AKTIF', 'LUNAS', 'DIBATALKAN'
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE loan_repayments (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT NOT NULL REFERENCES employee_loans(id) ON DELETE CASCADE,
    repayment_date DATE NOT NULL,
    amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    period_label VARCHAR(128) NOT NULL,
    report_type VARCHAR(64) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_employee_loans_emp_status ON employee_loans(employee_id, status);
CREATE INDEX idx_loan_repayments_loan_id ON loan_repayments(loan_id);

