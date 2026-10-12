-- Fase 2: aturan kerja bertanggal berlaku (Global / Kategori / Karyawan) dan kalender libur.
-- Tidak ada seed nilai aturan (K-047): Owner mengisi aturan Global lewat aplikasi.

CREATE TABLE work_rules (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scope                   VARCHAR(10)   NOT NULL,
    category_id             BIGINT        REFERENCES employee_categories (id),
    employee_id             BIGINT        REFERENCES employees (id),
    effective_from          DATE          NOT NULL,
    -- Semua parameter boleh NULL = mewarisi level di atasnya (K-046).
    work_start              TIME,
    work_end                TIME,
    break_start             TIME,
    break_end               TIME,
    late_tolerance_minutes  INTEGER,
    overtime_start          TIME,
    work_days               VARCHAR(30),
    daily_wage              NUMERIC(14, 2),
    overtime_rate_per_hour  NUMERIC(14, 2),
    base_salary             NUMERIC(14, 2),
    note                    VARCHAR(255),
    created_by              VARCHAR(50)   NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT work_rules_scope_valid CHECK (scope IN ('GLOBAL', 'CATEGORY', 'EMPLOYEE')),
    CONSTRAINT work_rules_scope_target CHECK (
        (scope = 'GLOBAL'   AND category_id IS NULL     AND employee_id IS NULL) OR
        (scope = 'CATEGORY' AND category_id IS NOT NULL AND employee_id IS NULL) OR
        (scope = 'EMPLOYEE' AND category_id IS NULL     AND employee_id IS NOT NULL)),
    CONSTRAINT work_rules_work_order  CHECK (work_start IS NULL OR work_end IS NULL OR work_end > work_start),
    CONSTRAINT work_rules_break_pair  CHECK ((break_start IS NULL) = (break_end IS NULL)),
    CONSTRAINT work_rules_break_order CHECK (break_start IS NULL OR break_end > break_start),
    CONSTRAINT work_rules_tolerance_positive CHECK (late_tolerance_minutes IS NULL OR late_tolerance_minutes >= 0),
    CONSTRAINT work_rules_money_positive CHECK (
        (daily_wage IS NULL OR daily_wage >= 0) AND
        (overtime_rate_per_hour IS NULL OR overtime_rate_per_hour >= 0) AND
        (base_salary IS NULL OR base_salary >= 0))
);

-- Satu versi per target per tanggal berlaku.
CREATE UNIQUE INDEX work_rules_version_uq
    ON work_rules (scope, COALESCE(category_id, 0), COALESCE(employee_id, 0), effective_from);
CREATE INDEX work_rules_category_idx ON work_rules (category_id) WHERE category_id IS NOT NULL;
CREATE INDEX work_rules_employee_idx ON work_rules (employee_id) WHERE employee_id IS NOT NULL;

-- Kalender libur berlaku untuk semua cabang (K-051).
CREATE TABLE holidays (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    holiday_date  DATE          NOT NULL UNIQUE,
    name          VARCHAR(100)  NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
