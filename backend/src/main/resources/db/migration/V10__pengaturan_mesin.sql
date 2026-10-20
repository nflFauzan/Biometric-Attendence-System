-- Rencana 6 (spec §7, §18, K-103): paket pengaturan ringan berversi, paket jaringan (APPLY_NETWORK), dan kunci
-- tertunda (ROTATE_KEY). Password Wi-Fi dan kunci tertunda disimpan terenkripsi AES-256-GCM (K-089).

-- Satu baris per alat rakitan, dibuat saat pengaturan pertama kali disimpan dari web. Batas = spec §18.1.
CREATE TABLE device_settings (
    device_id                BIGINT       PRIMARY KEY REFERENCES devices (id),
    version                  INTEGER      NOT NULL CHECK (version > 0),
    header_text              VARCHAR(32)  NOT NULL,
    greeting                 VARCHAR(64)  NOT NULL,
    name_display             VARCHAR(10)  NOT NULL CHECK (name_display IN ('FULL', 'FIRST', 'PIN')),
    brightness               INTEGER      NOT NULL CHECK (brightness BETWEEN 10 AND 100),
    buzzer_volume            INTEGER      NOT NULL CHECK (buzzer_volume BETWEEN 0 AND 100),
    duplicate_notice_seconds INTEGER      NOT NULL CHECK (duplicate_notice_seconds BETWEEN 0 AND 600),
    heartbeat_seconds        INTEGER      NOT NULL CHECK (heartbeat_seconds BETWEEN 2 AND 20),
    updated_by               VARCHAR(100) NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    row_version              BIGINT       NOT NULL DEFAULT 0
);

-- Paket jaringan per permintaan APPLY_NETWORK. server_url kosong = alamat server tidak diubah; tanpa baris wifi =
-- Wi-Fi tidak diubah.
CREATE TABLE device_network_configs (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id   BIGINT       NOT NULL REFERENCES devices (id),
    server_url  VARCHAR(255),
    created_by  VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE device_network_wifi (
    config_id    BIGINT      NOT NULL REFERENCES device_network_configs (id),
    position     INTEGER     NOT NULL,
    ssid         VARCHAR(32) NOT NULL,
    -- Terenkripsi (format v1:...); string kosong terenkripsi = Wi-Fi terbuka.
    password_enc TEXT        NOT NULL,
    PRIMARY KEY (config_id, position)
);

ALTER TABLE devices
    -- Versi paket pengaturan ringan yang terakhir dilaporkan mesin lewat heartbeat.
    ADD COLUMN settings_applied_version INTEGER NOT NULL DEFAULT 0,
    -- Paket jaringan yang terakhir dilaporkan BERHASIL; kosong = belum pernah diatur dari web.
    ADD COLUMN network_config_id BIGINT REFERENCES device_network_configs (id),
    -- ROTATE_KEY: kunci baru yang belum dipakai mesin. Hash untuk autentikasi, salinan terenkripsi untuk dikirim ulang.
    ADD COLUMN pending_key_hash VARCHAR(64) UNIQUE,
    ADD COLUMN pending_key_enc TEXT;

ALTER TABLE device_commands ADD COLUMN network_config_id BIGINT REFERENCES device_network_configs (id);
ALTER TABLE device_commands DROP CONSTRAINT device_commands_type_check;
ALTER TABLE device_commands
    ADD CONSTRAINT device_commands_type_check
        CHECK (type IN ('ENROLL', 'DELETE_TEMPLATE', 'DELETE_EMPLOYEE', 'APPLY_NETWORK', 'ROTATE_KEY'));

-- §4.2: satu perintah berisiko aktif per mesin, dijaga database agar aman terhadap klik bersamaan.
CREATE UNIQUE INDEX ux_device_commands_risky_device ON device_commands (device_id)
    WHERE type IN ('APPLY_NETWORK', 'ROTATE_KEY') AND status IN ('MENUNGGU', 'DIKIRIM');
