-- K-103: cabang absen per karyawan. Penempatan tetap di employees.branch_id; cabang tambahan di sini.
CREATE TABLE employee_extra_branches (
    employee_id BIGINT NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    branch_id   BIGINT NOT NULL REFERENCES branches (id),
    PRIMARY KEY (employee_id, branch_id)
);

CREATE INDEX employee_extra_branches_branch_idx ON employee_extra_branches (branch_id);

-- Peninjauan scan yang tertahan karena cabang. Scan asli (attendance_scans) tidak diubah.
CREATE TABLE scan_reviews (
    scan_id    BIGINT       PRIMARY KEY REFERENCES attendance_scans (id),
    decision   VARCHAR(20)  NOT NULL CHECK (decision IN ('DITERIMA', 'DIABAIKAN')),
    reason     VARCHAR(500) NOT NULL,
    decided_by VARCHAR(100) NOT NULL,
    decided_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0
);
