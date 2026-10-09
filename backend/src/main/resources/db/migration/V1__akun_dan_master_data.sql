-- Fase 1: cabang, kategori karyawan, karyawan, akun, mesin, audit log.
-- Data karyawan tidak dihapus permanen; gunakan kolom active / end_date.

CREATE TABLE branches (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    address     VARCHAR(255),
    timezone    VARCHAR(50)  NOT NULL DEFAULT 'Asia/Jakarta',
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE employee_categories (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO employee_categories (code, name) VALUES
    ('KANTOR', 'Kantor'),
    ('SUPIR_KENEK', 'Supir/Kenek');

CREATE TABLE employees (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_number  VARCHAR(30)  NOT NULL UNIQUE,
    full_name        VARCHAR(150) NOT NULL,
    phone            VARCHAR(30),
    branch_id        BIGINT       NOT NULL REFERENCES branches (id),
    category_id      BIGINT       NOT NULL REFERENCES employee_categories (id),
    position         VARCHAR(100),
    join_date        DATE         NOT NULL,
    end_date         DATE,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT employees_end_after_join CHECK (end_date IS NULL OR end_date >= join_date)
);

CREATE INDEX employees_branch_idx ON employees (branch_id);

CREATE TABLE user_accounts (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username              VARCHAR(50)  NOT NULL UNIQUE,
    password_hash         VARCHAR(255) NOT NULL,
    role                  VARCHAR(20)  NOT NULL,
    employee_id           BIGINT       UNIQUE REFERENCES employees (id),
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password  BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at         TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT user_accounts_role_valid CHECK (role IN ('OWNER', 'HR_FINANCE', 'EMPLOYEE')),
    CONSTRAINT user_accounts_employee_required CHECK (role <> 'EMPLOYEE' OR employee_id IS NOT NULL)
);

CREATE TABLE devices (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    serial_number  VARCHAR(100) NOT NULL UNIQUE,
    branch_id      BIGINT       NOT NULL REFERENCES branches (id),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE audit_logs (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actor_username  VARCHAR(50),
    action          VARCHAR(50)  NOT NULL,
    entity_type     VARCHAR(50)  NOT NULL,
    entity_id       VARCHAR(50),
    before_data     TEXT,
    after_data      TEXT,
    reason          TEXT
);

CREATE INDEX audit_logs_entity_idx ON audit_logs (entity_type, entity_id);
CREATE INDEX audit_logs_occurred_idx ON audit_logs (occurred_at DESC);
