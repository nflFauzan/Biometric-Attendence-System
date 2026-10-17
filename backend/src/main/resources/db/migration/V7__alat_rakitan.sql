-- Rencana 2 alat rakitan (K-087, K-088, K-099). Kunci disimpan sebagai SHA-256; X401 tidak punya kunci.
ALTER TABLE devices ADD COLUMN key_hash VARCHAR(64) UNIQUE;
-- Kontak pertama yang berhasil dengan kunci saat ini; kosong = kunci baru belum pernah dipakai.
ALTER TABLE devices ADD COLUMN key_first_used_at TIMESTAMPTZ;
ALTER TABLE devices ADD CONSTRAINT devices_protocol_check CHECK (protocol IN ('ZK_PUSH', 'RAKITAN_V1'));

-- ID scan buatan alat rakitan. X401 selalu kosong; UNIQUE mengabaikan NULL sehingga X401 tetap memakai
-- attendance_scans_uq (mesin + PIN + waktu).
ALTER TABLE attendance_scans ADD COLUMN device_scan_id UUID;
ALTER TABLE attendance_scans ADD CONSTRAINT attendance_scans_device_scan_uq UNIQUE (device_id, device_scan_id);
