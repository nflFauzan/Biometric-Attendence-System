-- Validasi lembur (FR-REV-003, K-140). Kandidat lembur tetap dihitung dari rekap harian; tabel ini hanya
-- menyimpan keputusan Owner / Finance & Accounting beserta kandidat saat divalidasi. Status tidak disimpan:
-- bila kandidat saat ini berbeda dari candidate_seconds, hari itu perlu divalidasi ulang.
-- approved_seconds = 0 berarti lembur ditolak.
create table overtime_validations (
    id bigserial primary key,
    employee_id bigint not null references employees(id),
    work_date date not null,
    candidate_seconds bigint not null check (candidate_seconds >= 0),
    approved_seconds bigint not null check (approved_seconds >= 0 and approved_seconds <= candidate_seconds),
    note varchar(1000),
    validated_by varchar(100) not null,
    validated_at timestamptz not null,
    version bigint not null default 0,
    constraint ux_overtime_validations_day unique (employee_id, work_date)
);
