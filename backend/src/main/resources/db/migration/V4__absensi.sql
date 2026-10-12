-- Fase 3: penerimaan scan dari mesin, rekap absensi, dan koreksi (docs/rencana-fase-3.md).

-- K-056: satu PIN mesin per karyawan, berlaku di semua mesin. Teks karena X401 memakai PIN hingga 8 digit.
ALTER TABLE employees ADD COLUMN device_pin VARCHAR(20) UNIQUE;

ALTER TABLE devices
    ADD COLUMN protocol           VARCHAR(20)  NOT NULL DEFAULT 'ZK_PUSH',   -- K-054
    ADD COLUMN accept_scans_from  TIMESTAMPTZ  NOT NULL DEFAULT now(),       -- K-057
    ADD COLUMN ignored_scan_count BIGINT       NOT NULL DEFAULT 0,           -- K-057
    ADD COLUMN last_seen_at       TIMESTAMPTZ,                               -- K-064
    ADD COLUMN last_ip            VARCHAR(45),
    ADD COLUMN firmware           VARCHAR(100),
    ADD COLUMN last_info          VARCHAR(255),
    ADD COLUMN last_attlog_stamp  VARCHAR(30);

-- Scan asli: hanya INSERT (K-019). Satu-satunya perubahan yang diizinkan adalah menghubungkan scan
-- PIN_TIDAK_DIKENAL ke karyawan saat PIN-nya diisi (K-068).
CREATE TABLE attendance_scans (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id          BIGINT       NOT NULL REFERENCES devices (id),
    device_pin         VARCHAR(20)  NOT NULL,
    employee_id        BIGINT       REFERENCES employees (id),
    scanned_at         TIMESTAMPTZ  NOT NULL,
    device_local_time  TIMESTAMP    NOT NULL,
    status_code        VARCHAR(10),
    verify_mode        VARCHAR(10),
    flags              VARCHAR(200),
    raw_line           TEXT         NOT NULL,
    received_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- K-055/K-061: kunci duplikat untuk protokol tanpa ID scan.
    CONSTRAINT attendance_scans_uq UNIQUE (device_id, device_pin, scanned_at)
);
CREATE INDEX attendance_scans_employee_idx ON attendance_scans (employee_id, scanned_at);
CREATE INDEX attendance_scans_scanned_idx ON attendance_scans (scanned_at);
CREATE INDEX attendance_scans_unmatched_idx ON attendance_scans (device_pin) WHERE employee_id IS NULL;

-- K-062: baris yang tidak bisa dibaca disimpan apa adanya agar tidak ada data yang hilang.
CREATE TABLE rejected_scan_lines (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id    BIGINT       NOT NULL REFERENCES devices (id),
    raw_line     TEXT         NOT NULL,
    reason       VARCHAR(200) NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- K-059/K-066: koreksi jam masuk/pulang; satu koreksi aktif per karyawan per tanggal, riwayat disimpan.
CREATE TABLE attendance_corrections (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id    BIGINT       NOT NULL REFERENCES employees (id),
    work_date      DATE         NOT NULL,
    check_in       TIME,
    check_out      TIME,
    reason         TEXT         NOT NULL CHECK (length(trim(reason)) > 0),
    created_by     VARCHAR(50)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    superseded_at  TIMESTAMPTZ,
    CONSTRAINT attendance_corrections_has_value CHECK (check_in IS NOT NULL OR check_out IS NOT NULL)
);
CREATE UNIQUE INDEX attendance_corrections_active_uq
    ON attendance_corrections (employee_id, work_date) WHERE superseded_at IS NULL;
CREATE INDEX attendance_corrections_employee_idx ON attendance_corrections (employee_id, work_date);
