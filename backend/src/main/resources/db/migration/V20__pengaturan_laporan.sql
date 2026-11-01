-- Isi kop laporan format klien (Manager Name, Department) per jenis laporan (K-142).
CREATE TABLE report_settings (
    report_slug      VARCHAR(64)  PRIMARY KEY,
    manager_name     VARCHAR(150),
    department_name  VARCHAR(150),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
