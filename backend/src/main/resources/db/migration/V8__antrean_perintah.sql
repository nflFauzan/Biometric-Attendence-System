-- Rencana 3 (spec §4, §16): antrean perintah ke alat rakitan, persetujuan sidik jari (K-091), dan catatan
-- sidik jari terdaftar. Template sidik jari belum disimpan di server (Rencana 4, K-089).

ALTER TABLE employees
    ADD COLUMN fingerprint_consent_at TIMESTAMPTZ,
    ADD COLUMN fingerprint_consent_by VARCHAR(100);

CREATE TABLE device_commands (
    id            UUID PRIMARY KEY,
    device_id     BIGINT       NOT NULL REFERENCES devices (id),
    type          VARCHAR(30)  NOT NULL CHECK (type IN ('ENROLL')),
    status        VARCHAR(20)  NOT NULL
        CHECK (status IN ('MENUNGGU', 'DIKIRIM', 'BERHASIL', 'GAGAL', 'KEDALUWARSA', 'DIBATALKAN')),
    employee_id   BIGINT REFERENCES employees (id),
    finger        INTEGER CHECK (finger IN (1, 2)),
    device_pin    VARCHAR(20),
    display_name  VARCHAR(150),
    step          INTEGER,
    error_code    VARCHAR(40),
    error_detail  VARCHAR(255),
    created_by    VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    sent_at       TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ,
    version       BIGINT       NOT NULL DEFAULT 0
);

-- K-094: satu ENROLL aktif per mesin dan per karyawan + jari, dijaga database agar aman terhadap klik bersamaan.
CREATE UNIQUE INDEX ux_device_commands_enroll_device ON device_commands (device_id)
    WHERE type = 'ENROLL' AND status IN ('MENUNGGU', 'DIKIRIM');
CREATE UNIQUE INDEX ux_device_commands_enroll_finger ON device_commands (employee_id, finger)
    WHERE type = 'ENROLL' AND status IN ('MENUNGGU', 'DIKIRIM');
CREATE INDEX ix_device_commands_device_status ON device_commands (device_id, status);

CREATE TABLE employee_fingerprints (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id  BIGINT      NOT NULL REFERENCES employees (id),
    finger       INTEGER     NOT NULL CHECK (finger IN (1, 2)),
    device_id    BIGINT      NOT NULL REFERENCES devices (id),
    command_id   UUID        NOT NULL REFERENCES device_commands (id),
    enrolled_at  TIMESTAMPTZ NOT NULL,
    UNIQUE (employee_id, finger)
);
