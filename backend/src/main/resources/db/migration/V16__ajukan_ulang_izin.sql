-- Ajukan ulang pengajuan izin yang ditolak (K-126): draft baru menunjuk pengajuan asalnya.
-- Satu pengajuan yang ditolak hanya punya satu pengajuan ulang; klik ganda ditahan unique index.
alter table leave_requests add column resubmitted_from_id bigint references leave_requests(id);
create unique index ux_leave_requests_resubmitted_from on leave_requests(resubmitted_from_id)
    where resubmitted_from_id is not null;
