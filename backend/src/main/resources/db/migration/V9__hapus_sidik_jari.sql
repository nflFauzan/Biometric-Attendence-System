-- Rencana 5 (spec §17, K-102): perintah hapus sidik jari, penanda penghapusan saat karyawan keluar, dan kapasitas
-- sensor terakhir dari heartbeat.

ALTER TABLE device_commands DROP CONSTRAINT device_commands_type_check;
ALTER TABLE device_commands
    ADD CONSTRAINT device_commands_type_check CHECK (type IN ('ENROLL', 'DELETE_TEMPLATE', 'DELETE_EMPLOYEE'));

-- Kunci PIN (K-093) mencari perintah hapus yang belum selesai berdasarkan PIN.
CREATE INDEX ix_device_commands_delete_pin ON device_commands (device_pin, status)
    WHERE type IN ('DELETE_TEMPLATE', 'DELETE_EMPLOYEE');

-- Diisi saat sidik jari karyawan yang keluar/nonaktif sudah dihapus, agar pemeriksaan terjadwal tidak mengulanginya.
ALTER TABLE employees ADD COLUMN fingerprints_removed_at TIMESTAMPTZ;

ALTER TABLE devices
    ADD COLUMN sensor_used     INTEGER,
    ADD COLUMN sensor_capacity INTEGER;
