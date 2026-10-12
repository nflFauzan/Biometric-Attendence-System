# Rencana Teknis — Fase 3: Absensi

**Status: draft 2026-10-12, menunggu persetujuan pengguna.**
Acuan: [rencana-pengerjaan.md](rencana-pengerjaan.md) Fase 3, [kebutuhan.md](kebutuhan.md) ATT/COR/DASH/DEV, [aturan-bisnis.md](aturan-bisnis.md) §4, [arsitektur.md](arsitektur.md) §11, K-054/K-055, dan hasil uji mesin di [panduan-mesin-x401.md](panduan-mesin-x401.md) §7.

Tujuan: scan dari mesin (X401 sekarang, alat rakitan nanti) tersimpan tanpa hilang atau ganda, langsung terlihat di dashboard, dan menjadi rekap harian memakai mesin perhitungan Fase 2. Koreksi absensi disimpan terpisah dari scan asli dengan alasan dan audit.

## 1. Keputusan yang sudah dipilih pengguna (2026-10-12)

| ID usulan | Keputusan |
|---|---|
| K-056 | **Satu PIN mesin per karyawan** (kolom `device_pin` di karyawan, unik, berlaku di semua mesin/cabang). Menjawab Q-031. |
| K-057 | Setiap mesin punya **"Terima scan sejak"** (bawaan: waktu mesin didaftarkan). Scan yang lebih lama dibalas OK agar mesin berhenti mengirim, tetapi **tidak disimpan**; hanya jumlahnya yang dicatat. Menjawab Q-032. |
| K-058 | Rekap harian **dihitung saat dibuka** dari scan asli + koreksi + aturan berlaku (mesin perhitungan Fase 2). Tidak ada tabel rekap. Snapshot baru disimpan saat payroll final (Fase 5). |
| K-059 | Koreksi absensi dilakukan **Owner dan HR/Finance**. Alasan wajib, tercatat di audit log, tanpa persetujuan kedua (COR-008 masih perlu konfirmasi). |

## 2. Usulan tambahan yang perlu disetujui

| ID usulan | Usulan | Alasan |
|---|---|---|
| K-060 | Endpoint mesin juga dibuka di **port 8888** (`docker-compose`: `8888:8080`), selain 8080. | Tombol 0 di keypad X401 rusak (panduan §7). Di produksi semua lewat reverse proxy (Fase 7). |
| K-061 | **ACK = teks `OK` setelah transaksi commit.** Satu request ATTLOG disimpan dalam satu transaksi; duplikat diabaikan oleh unique constraint (`ON CONFLICT DO NOTHING`). Gagal simpan → HTTP 500 tanpa `OK`, mesin mengirim ulang (terbukti di uji X401). | K-020, ATT-004, ATT-005. |
| K-062 | **Baris scan rusak** (format salah) disimpan apa adanya di `rejected_scan_lines` lalu request tetap di-ACK. | Kalau ditolak, mesin mengirim batch yang sama terus-menerus dan scan valid di batch itu ikut tertahan. Data mentah tetap tersimpan, jadi tidak ada yang hilang. |
| K-063 | Handshake meminta **hanya data absensi** (`TransFlag=TransData AttLog`): mesin tidak diminta mengirim data user, foto, atau template sidik jari. | K-024, arsitektur §10.5: minimalkan data biometrik di server. |
| K-064 | Mesin dianggap **offline** bila tidak ada request selama `DEVICE_OFFLINE_AFTER` (bawaan 5 menit, bisa diatur). | Heartbeat X401 teramati ±10–107 detik; ini parameter teknis, bukan aturan bisnis. |
| K-065 | **Penanda scan:** `PIN_TIDAK_DIKENAL` (PIN belum dipetakan), `WAKTU_DI_MASA_DEPAN` (waktu scan > waktu terima + 5 menit), `CABANG_BERBEDA` (cabang mesin ≠ cabang karyawan). Scan tetap disimpan dan tetap dipakai rekap kecuali `PIN_TIDAK_DIKENAL`. | ATT-008, DASH-007. Aturan absen di cabang lain belum diketahui (Q-022), jadi hanya ditandai. |
| K-066 | **Satu koreksi aktif per karyawan per tanggal.** Koreksi baru menggantikan yang lama; yang lama tetap tersimpan (riwayat) dan audit mencatat nilai sebelum/sesudah. | COR-004, COR-005; mesin perhitungan Fase 2 menerima satu `Correction` per hari. |
| K-067 | **Mesin belum terdaftar** dibalas HTTP 401 tanpa `OK` (mesin menyimpan scan untuk dikirim ulang). Nomor seri yang mencoba terhubung ditampilkan di halaman Mesin agar mudah didaftarkan. | DEV-003, arsitektur §10.3; memudahkan setup tanpa menebak SN. |
| K-068 | Saat PIN diisi/diubah pada karyawan, scan `PIN_TIDAK_DIKENAL` dengan PIN itu **dihubungkan** ke karyawan tersebut dalam transaksi yang sama dan dicatat di audit. | Karyawan sering didaftarkan di mesin sebelum PIN diisi di web; isi scan asli tidak berubah, hanya relasinya. |

## 3. Schema — `V4__absensi.sql`

```sql
-- K-056: satu PIN mesin per karyawan (teks; X401 memakai PIN hingga 8 digit).
ALTER TABLE employees ADD COLUMN device_pin VARCHAR(20) UNIQUE;

ALTER TABLE devices
    ADD COLUMN protocol           VARCHAR(20)  NOT NULL DEFAULT 'ZK_PUSH',   -- K-054
    ADD COLUMN accept_scans_from  TIMESTAMPTZ  NOT NULL DEFAULT now(),       -- K-057
    ADD COLUMN ignored_scan_count BIGINT       NOT NULL DEFAULT 0,           -- K-057
    ADD COLUMN last_seen_at       TIMESTAMPTZ,                               -- K-064
    ADD COLUMN last_ip            VARCHAR(45),
    ADD COLUMN firmware           VARCHAR(100),
    ADD COLUMN last_info          VARCHAR(255),                              -- INFO dari heartbeat
    ADD COLUMN last_attlog_stamp  VARCHAR(30);

-- Scan asli: hanya INSERT, tidak pernah UPDATE/DELETE (K-019), kecuali relasi employee_id (K-068).
CREATE TABLE attendance_scans (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id          BIGINT       NOT NULL REFERENCES devices (id),
    device_pin         VARCHAR(20)  NOT NULL,
    employee_id        BIGINT       REFERENCES employees (id),   -- NULL = PIN_TIDAK_DIKENAL
    scanned_at         TIMESTAMPTZ  NOT NULL,                    -- waktu lokal mesin + zona cabang mesin
    device_local_time  TIMESTAMP    NOT NULL,                    -- persis seperti dikirim mesin
    status_code        VARCHAR(10),
    verify_mode        VARCHAR(10),
    flags              VARCHAR(200),
    raw_line           TEXT         NOT NULL,
    received_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT attendance_scans_uq UNIQUE (device_id, device_pin, scanned_at)   -- K-055, K-061
);
CREATE INDEX attendance_scans_employee_idx ON attendance_scans (employee_id, scanned_at);
CREATE INDEX attendance_scans_unmatched_idx ON attendance_scans (device_pin) WHERE employee_id IS NULL;

CREATE TABLE rejected_scan_lines (                                     -- K-062
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id    BIGINT       NOT NULL REFERENCES devices (id),
    raw_line     TEXT         NOT NULL,
    reason       VARCHAR(200) NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE attendance_corrections (                                  -- K-059, K-066
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id    BIGINT       NOT NULL REFERENCES employees (id),
    work_date      DATE         NOT NULL,
    check_in       TIME,
    check_out      TIME,
    reason         TEXT         NOT NULL CHECK (length(trim(reason)) > 0),
    created_by     VARCHAR(50)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    superseded_at  TIMESTAMPTZ,
    CONSTRAINT attendance_corrections_has_value CHECK (check_in IS NOT NULL OR check_out IS NOT NULL)
);
CREATE UNIQUE INDEX attendance_corrections_active_uq
    ON attendance_corrections (employee_id, work_date) WHERE superseded_at IS NULL;
```

## 4. Backend

| Paket | Isi |
|---|---|
| `ingest` | **Inti penerimaan scan** (K-054): `ScanIngestService.ingest(device, List<ScanEvent>)` → batas "Terima scan sejak", pemetaan PIN, penanda, simpan (`ON CONFLICT DO NOTHING`), lalu publikasi event setelah commit. `ScanEvent` = PIN, waktu lokal mesin, status, verifikasi, baris mentah. Tidak tahu protokol. |
| `ingest.zkpush` | **Adapter ZK-Push**: `ZkPushController` (`/iclock/cdata`, `/iclock/getrequest`, `/iclock/devicecmd`), `ZkPushParser` (baris dipisah tab, format dari panduan §7), `ZkPushOptions` (teks handshake; `TimeZone` diturunkan dari zona cabang mesin, bukan hardcode). Memperbarui `last_seen_at`, IP, firmware, INFO. |
| `attendance` | `AttendanceService`: memuat scan + koreksi aktif per karyawan per tanggal (batas hari menurut zona cabang karyawan) lalu memanggil `DailyAttendanceCalculator` (K-058). `CorrectionService` (K-059, K-066, audit). `UnmatchedScanService` (K-068). Controller rekap dan koreksi. |
| `dashboard` | `DashboardService` (ringkasan hari ini per cabang), `DashboardStream` (SSE, `SseEmitter`, dikirim pada `@TransactionalEventListener(AFTER_COMMIT)` saat scan tersimpan atau status mesin berubah). |
| `device` (ubah) | Status online/offline (K-064), "Terima scan sejak", jumlah scan diabaikan, daftar SN tak terdaftar yang mencoba terhubung (K-067, disimpan di memori, tidak di DB). |
| `employee` (ubah) | Kolom PIN mesin + validasi unik + K-068. |

### Alur ATTLOG

1. `POST /iclock/cdata?SN=…&table=ATTLOG&Stamp=…` → cari mesin berdasarkan SN. Tidak ada/nonaktif → 401 (K-067).
2. Parse setiap baris. Rusak → `rejected_scan_lines` (K-062).
3. Waktu lokal mesin + zona cabang mesin → `scanned_at`. Lebih lama dari `accept_scans_from` → tidak disimpan, `ignored_scan_count` bertambah (K-057).
4. PIN → karyawan (K-056); tidak ketemu → `employee_id NULL` + `PIN_TIDAK_DIKENAL`. Penanda lain sesuai K-065.
5. Simpan semua dalam satu transaksi dengan `ON CONFLICT DO NOTHING` (K-061), simpan `Stamp`.
6. Setelah commit: balas `OK`, kirim event SSE. Gagal → 500 tanpa `OK`.

Tabel lain (`OPERLOG`, `USERINFO`, `FINGERTMP`, dst.) seharusnya tidak dikirim karena K-063; jika tetap datang, dibalas `OK` **tanpa disimpan** dan hanya jumlahnya dicatat di log aplikasi.

### API

| Method | Path | Owner | HR/Finance | Karyawan | Tanpa login |
|---|---|:-:|:-:|:-:|:-:|
| GET/POST | `/iclock/cdata`, `GET /iclock/getrequest`, `POST /iclock/devicecmd` | – | – | – | ✓ (hanya SN terdaftar & aktif; tanpa CSRF; batas body 1 MB) |
| GET | `/api/attendance/daily?date=&branchId=` (rekap semua karyawan aktif) | ✓ | ✓ | ✗ | ✗ |
| GET | `/api/attendance/employees/{id}?from=&to=` (rekap per hari + scan asli + riwayat koreksi) | ✓ | ✓ | ✗ | ✗ |
| GET | `/api/attendance/unmatched-scans?from=&to=` | ✓ | ✓ | ✗ | ✗ |
| POST | `/api/attendance/corrections` (karyawan, tanggal, jam masuk/pulang, alasan) | ✓ | ✓ | ✗ | ✗ |
| GET | `/api/dashboard/summary?date=` | ✓ | ✓ | ✗ | ✗ |
| GET | `/api/dashboard/stream` (SSE) | ✓ | ✓ | ✗ | ✗ |
| GET | `/api/devices/unregistered-attempts` | ✓ | ✓ | ✗ | ✗ |

Semua masuk `SecurityConfig` dan `AccessControlTests`. Riwayat absensi mandiri untuk Karyawan (ATT-021) **tidak** termasuk karena aksesnya masih perlu konfirmasi.

## 5. Simulator — `tools/device-simulator/simulate.mjs`

Node tanpa dependency, berbicara protokol ZK-Push yang sama dengan X401 (format dari panduan §7, data sintetis).

| Perintah | Meniru |
|---|---|
| `handshake` / `heartbeat` | Mesin menyala dan heartbeat berkala |
| `scan --pin 9001 [--time "2026-10-13 08:01:02"]` | Satu scan realtime |
| `offline --count 3` | Simpan 3 scan selama "offline", lalu kirim sekaligus setelah "online" |
| `duplicate` | Kirim batch yang sama dua kali |
| `history --count 500 --before "2026-01-01"` | Riwayat lama (uji K-057) |
| `bad-line` | Baris rusak (uji K-062) |
| `--sn SALAH` | Mesin tak terdaftar (uji K-067) |
| `retry-until-ok` | Ulangi kirim seperti X401 sampai dibalas `OK` (uji server mati lalu menyala) |

Simulator dipakai untuk uji manual dan checkpoint. Test otomatis memanggil endpoint yang sama lewat MockMvc dengan isi yang identik.

## 6. Frontend

- **Dashboard** (ganti isi sekarang): hadir hari ini, terlambat, tidak lengkap/perlu tinjauan, kandidat lembur, belum hadir; ringkasan per cabang; status mesin; daftar scan terbaru. Berubah otomatis lewat SSE (DASH-001–007).
- **Absensi Harian** (`/absensi`): tabel semua karyawan untuk satu tanggal dan cabang: jam masuk/pulang (dengan sumber `SCAN`/`KOREKSI`), keterlambatan, jam normal, kandidat lembur, penanda.
- **Detail absensi karyawan** (`/absensi/karyawan/:id`): per hari dalam rentang tanggal, scan asli, dan riwayat koreksi. Tombol **Koreksi** membuka dialog jam masuk/pulang + alasan wajib.
- **Scan tak dikenal**: daftar scan `PIN_TIDAK_DIKENAL` agar PIN bisa dipetakan.
- **Mesin**: status Online/Offline, terakhir terhubung, firmware, "Terima scan sejak", jumlah scan diabaikan, dan banner "Mesin belum terdaftar mencoba terhubung: SN …" dengan tombol daftarkan.
- **Karyawan**: kolom PIN mesin.
- Nginx frontend: `proxy_buffering off` untuk `/api/dashboard/stream` agar SSE tidak tertahan.

## 7. Pengujian

| Test | Jenis |
|---|---|
| `ZkPushParser`: baris valid, kolom kurang, tanggal salah, PIN kosong, CRLF | Unit |
| ACK hanya setelah tersimpan; gagal simpan → tanpa `OK` | Integration |
| Batch yang sama dikirim 2× dan 2 request paralel → tepat 1 baris per scan (ATT-005) | Integration |
| Skenario offline lalu kirim ulang: semua scan tiba, waktu = waktu scan, bukan waktu terima | Integration |
| Mesin tak terdaftar / nonaktif → 401, tidak ada baris tersimpan | Integration |
| Riwayat sebelum "Terima scan sejak" tidak disimpan, dihitung (K-057) | Integration |
| Baris rusak masuk `rejected_scan_lines`, baris valid di batch sama tersimpan (K-062) | Integration |
| PIN tak dikenal tersimpan + penanda; PIN diisi kemudian → terhubung + audit (K-068) | Integration |
| **TC-08 lengkap**: koreksi tersimpan, scan asli utuh, audit sebelum/sesudah, rekap memakai koreksi | Integration |
| Koreksi baru menggantikan yang lama, riwayat tetap ada (K-066); alasan kosong ditolak | Integration |
| Rekap berubah setelah scan baru / koreksi / aturan baru (ATT-010, COR-006) | Integration |
| Batas hari memakai zona cabang (scan 23.59 vs 00.01) | Integration |
| Status online/offline dari `last_seen_at` (K-064) | Unit |
| Event SSE terkirim setelah commit, tidak terkirim bila rollback | Integration |
| Hak akses semua endpoint baru + `/iclock/**` tanpa login hanya untuk SN terdaftar | `AccessControlTests` |
| Halaman Dashboard, Absensi Harian, Detail, Koreksi, Mesin | Vitest |
| Checkpoint: simulator → dashboard berubah dalam hitungan detik | Playwright + simulator |
| Checkpoint: skenario X-01 sampai X-08 dengan X401 sungguhan | Manual, hasil dicatat di panduan X401 |

## 8. Urutan pengerjaan

1. Migration V4 + entity + `ScanIngestService` + test integrasi inti.
2. Adapter ZK-Push + parser + `SecurityConfig` + test.
3. Simulator; uji dengan X401 (port 8888, mesin didaftarkan dengan SN `BRYV181860187`).
4. Rekap (`AttendanceService`) + koreksi + scan tak dikenal + test TC-08.
5. Dashboard SSE + status mesin.
6. Frontend semua halaman + Vitest.
7. Checkpoint: Playwright + simulator, lalu X-01–X-08 dengan X401.

## 9. Risiko dan batasan

- Perilaku `ATTLOGStamp` pada X401 belum pasti: server menyimpan `Stamp` terakhir dan memakainya di handshake; dibuktikan lewat X-03. Bila mesin tetap mengirim ulang, unique constraint mencegah duplikat.
- X401 tanpa secret dan TLS (K-055): endpoint `/iclock/**` hanya untuk jaringan lokal sampai Fase 7.
- Deteksi jam mesin salah (X-07) terbatas pada `WAKTU_DI_MASA_DEPAN`; jam mesin yang terlambat tidak bisa dideteksi dari protokol ini (Q-020).
- ADMS mesin uji milik dosen sudah diarahkan ke laptop. Begitu backend Fase 3 berjalan di port 8888, riwayat lama dibalas OK dan tidak disimpan (K-057), dan setelah itu mesin tidak mengirimnya lagi. Ini perlu sepengetahuan dosen.

## 10. Dokumen yang diperbarui setelah implementasi

- `keputusan.md`: K-056 sampai K-068.
- `aturan-bisnis.md` §4: penanda scan, batas hari, model koreksi.
- `pertanyaan-terbuka.md`: Q-031 dan Q-032 dijawab; catat temuan baru.
- `panduan-mesin-x401.md`: Tahap 5 disesuaikan (port 8888) dan hasil X-01–X-08.
- `arsitektur.md` §11.1, `rencana-pengerjaan.md`: status Fase 3.
