-- Profil lengkap karyawan dari template data client (K-129). Semua opsional.
ALTER TABLE employees
    ADD COLUMN email      VARCHAR(150),
    ADD COLUMN gender     VARCHAR(20),
    ADD COLUMN birth_date DATE,
    ADD COLUMN address    VARCHAR(500),
    ADD COLUMN notes      VARCHAR(500),
    ADD CONSTRAINT employees_gender_valid CHECK (gender IN ('LAKI_LAKI', 'PEREMPUAN', 'TIDAK_DISEBUTKAN'));
