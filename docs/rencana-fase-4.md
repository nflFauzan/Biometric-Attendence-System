# Rencana Teknis — Fase 4: Izin dan Cuti

**Status: disetujui pengguna dan diimplementasikan 2026-10-13 (K-070–K-082); menunggu checkpoint pengguna.**
Acuan: [rencana-pengerjaan.md](rencana-pengerjaan.md) Fase 4, [kebutuhan.md](kebutuhan.md) §3.10 LEAVE-001–008, [alur.md](alur.md) §8, [arsitektur.md](arsitektur.md) §6.8, Q-013 dan Q-016 di [pertanyaan-terbuka.md](pertanyaan-terbuka.md).

Tujuan: karyawan (atau HR atas nama karyawan) mengajukan izin/cuti untuk rentang tanggal dengan surat pendukung, Owner atau HR/Finance menyetujui atau menolak, dan izin yang disetujui langsung terlihat di rekap harian. Semua aturan yang belum diketahui (jenis, wajib surat, dibayar atau tidak, kuota) **tidak di-hardcode**: disimpan sebagai pengaturan yang bisa diubah Owner atau ditunda.

## 1. Keputusan yang sudah dipilih pengguna (2026-10-13)

| ID usulan | Keputusan |
|---|---|
| K-070 | **Jenis izin/cuti adalah master data yang diatur Owner**: nama, wajib surat atau tidak, dibayar atau tidak (boleh "belum ditentukan"; baru dipakai di Fase 5), aktif. Diisi contoh awal *Sakit*, *Cuti*, *Izin* yang bisa diubah atau dinonaktifkan. |
| K-071 | Pengajuan berupa **rentang tanggal hari penuh** (1 hari atau lebih). Izin datang terlambat/pulang cepat untuk sementara dicatat lewat koreksi absensi (K-059). Izin per jam menunggu kebutuhan klien. |
| K-072 | **Tanpa kuota dulu.** Saat menyetujui, sistem menampilkan pemakaian per jenis pada tahun berjalan (hari kalender dan hari kerja). Kuota dan saldo (LEAVE-008) ditambah setelah kebijakan klien jelas. |
| K-073 | **Karyawan mengajukan sendiri** dari akunnya; **Owner/HR juga bisa mencatatkan atas nama karyawan** (mis. surat dikirim lewat WA, atau karyawan tanpa akun). Persetujuan oleh Owner atau HR/Finance. |

## 2. Usulan tambahan yang perlu disetujui

| ID usulan | Usulan | Alasan |
|---|---|---|
| K-074 | Status pengajuan: **Menunggu → Disetujui / Ditolak**, dan **Dibatalkan**. Penolakan wajib alasan. Pengaju boleh membatalkan selama masih Menunggu. Izin yang sudah Disetujui hanya bisa dibatalkan Owner/HR dengan alasan (mis. karyawan ternyata masuk). Setelah Fase 5, pembatalan pada periode payroll final mengikuti prosedur buka-kembali (Q-014). | LEAVE-004/005, alur.md §8, status di alur.md §10. |
| K-075 | **Tidak boleh tumpang tindih**: satu karyawan tidak bisa punya dua pengajuan Menunggu/Disetujui yang tanggalnya beririsan. Dijaga oleh *exclusion constraint* PostgreSQL (`btree_gist`), jadi tetap aman walau dua pengajuan dikirim bersamaan. | Mencegah satu hari dihitung dua jenis izin. |
| K-076 | **Efek ke rekap harian** (dihitung saat dibuka, K-058): izin **Disetujui** pada hari kerja tanpa scan → status baru **`IZIN`** (dengan nama jenisnya), bukan "Tidak hadir". Jika ada scan pada hari izin → rekap tetap dari scan, diberi penanda **`HADIR_SAAT_IZIN`** untuk ditinjau. Hari libur/bukan hari kerja di dalam rentang tidak berubah. Pengajuan **Menunggu tidak berpengaruh** (LEAVE-006), hanya diberi penanda informasi **`IZIN_MENUNGGU`**. | LEAVE-006/007, alur.md §5 ("tidak ada scan tidak otomatis berarti mangkir"). Tidak menebak dampak ke gaji. |
| K-077 | **"Wajib surat" diperiksa saat persetujuan, bukan saat pengajuan**: pengajuan boleh dikirim dulu (mis. sakit, surat dokter menyusul), tetapi tidak bisa disetujui sebelum ada lampiran. | Praktik umum surat sakit menyusul; LEAVE-003. |
| K-078 | **Lampiran**: PDF/JPG/PNG, maks. 5 MB per file, maks. 5 file per pengajuan. Jenis file diperiksa dari isi file (*magic bytes*), bukan hanya nama. Disimpan di volume disk `uploads` dengan nama acak (UUID); metadata dan hash SHA-256 di database. Diunduh hanya lewat endpoint yang memeriksa hak akses. Lampiran bisa ditambah selama Menunggu, tidak bisa dihapus (dicatat audit). | Surat sakit adalah data pribadi sensitif; file di luar database agar DB tetap ringan; backup volume masuk Fase 7. |
| K-079 | **Tidak bisa menyetujui pengajuan sendiri**: akun Owner/HR yang terhubung ke karyawan tidak bisa menyetujui/menolak pengajuan karyawan itu. | Pemisahan tugas; mencegah penyalahgunaan. |
| K-080 | Pengajuan **boleh untuk tanggal lampau** (sakit dilaporkan setelahnya) tanpa batas hari yang di-hardcode. Rentang maksimum 366 hari sebagai batas teknis (cuti melahirkan bisa ±3 bulan). | Batas lampau adalah aturan bisnis yang belum diketahui (Q-033). |
| K-081 | **Notifikasi langsung di dalam aplikasi** untuk Owner dan HR/Finance: saat karyawan mengirim pengajuan, muncul pop-up dalam hitungan detik (lewat SSE Fase 3), lonceng di header berisi daftar pengajuan yang menunggu, serta jumlahnya di menu dan dashboard. Tidak ada email/WA. Karyawan melihat hasil keputusan di halaman "Izin & Cuti Saya". | Permintaan pengguna 2026-10-13 ("langsung ngasih notif ke owner, HR/Finance"); tanpa layanan luar. |
| K-082 | **Surat pengajuan bisa dicetak / disimpan sebagai PDF**: setiap pengajuan punya halaman surat siap cetak (identitas karyawan, cabang, jenis, tanggal, jumlah hari, alasan, status, kolom tanda tangan pemohon dan penyetuju; nama penyetuju dan waktu persetujuan bila sudah disetujui). Dicetak lewat fitur cetak browser (pilih "Simpan sebagai PDF"), jadi tidak perlu library PDF di server dan bisa dari HP. Karyawan yang sudah membuat surat manual cukup mengunggahnya sebagai lampiran (K-078). Format kop surat bisa disesuaikan setelah ada contoh dari klien. | Permintaan pengguna 2026-10-13. |

## 3. Schema — `V5__izin_cuti.sql`

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE leave_types (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    requires_document BOOLEAN NOT NULL DEFAULT FALSE,
    paid              BOOLEAN,            -- NULL = belum ditentukan (dipakai Fase 5)
    description       VARCHAR(500),
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at, updated_at ...
);
CREATE UNIQUE INDEX ux_leave_types_name ON leave_types (lower(name));
-- contoh awal, boleh diubah Owner: Sakit, Cuti, Izin (requires_document = false, paid = NULL)

CREATE TABLE leave_requests (
    id              BIGSERIAL PRIMARY KEY,
    employee_id     BIGINT NOT NULL REFERENCES employees(id),
    leave_type_id   BIGINT NOT NULL REFERENCES leave_types(id),
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    reason          VARCHAR(1000) NOT NULL,
    status          VARCHAR(20) NOT NULL,   -- MENUNGGU | DISETUJUI | DITOLAK | DIBATALKAN
    submitted_by    BIGINT NOT NULL REFERENCES user_accounts(id),
    submitted_at    TIMESTAMPTZ NOT NULL,
    decided_by      BIGINT REFERENCES user_accounts(id),
    decided_at      TIMESTAMPTZ,
    decision_note   VARCHAR(1000),          -- wajib bila DITOLAK
    cancelled_by    BIGINT REFERENCES user_accounts(id),
    cancelled_at    TIMESTAMPTZ,
    cancel_reason   VARCHAR(1000),
    version         BIGINT NOT NULL DEFAULT 0,
    CHECK (end_date >= start_date),
    CHECK (end_date - start_date < 366),
    EXCLUDE USING gist (employee_id WITH =, daterange(start_date, end_date, '[]') WITH &&)
        WHERE (status IN ('MENUNGGU', 'DISETUJUI'))   -- K-075
);
CREATE INDEX ix_leave_requests_status ON leave_requests (status, start_date);

CREATE TABLE leave_attachments (
    id               BIGSERIAL PRIMARY KEY,
    leave_request_id BIGINT NOT NULL REFERENCES leave_requests(id),
    original_name    VARCHAR(255) NOT NULL,
    content_type     VARCHAR(50) NOT NULL,
    size_bytes       BIGINT NOT NULL,
    storage_key      VARCHAR(64) NOT NULL UNIQUE,  -- UUID, nama file di disk
    sha256           CHAR(64) NOT NULL,
    uploaded_by      BIGINT NOT NULL REFERENCES user_accounts(id),
    uploaded_at      TIMESTAMPTZ NOT NULL
);
```

## 4. Backend — paket `leave`

| Komponen | Isi |
|---|---|
| `LeaveType` + repository/service/controller | CRUD jenis izin (Owner), daftar aktif untuk semua role. Tidak dihapus, hanya dinonaktifkan. |
| `LeaveRequest`, `LeaveStatus`, `LeaveRequestRepository` | Entity dengan `@Version` (persetujuan bersamaan oleh dua orang → satu gagal dengan pesan jelas). |
| `LeaveService` | `submit` (untuk diri sendiri atau atas nama), `approve`, `reject`, `cancel`; validasi K-074–K-080; menerjemahkan pelanggaran exclusion constraint menjadi pesan "tanggal bertabrakan dengan pengajuan …". Audit `CREATE/APPROVE/REJECT/CANCEL` `LEAVE_REQUEST` di transaksi yang sama. Menerbitkan `AttendanceChangedEvent` agar dashboard/rekap ikut berubah (SSE). |
| `AttachmentStorage` | Simpan/baca file di `app.uploads.dir` (bawaan `/app/uploads`), cek magic bytes, hitung SHA-256, tulis ke file sementara lalu pindah (atomic). File ditulis sebelum commit; bila transaksi gagal, file yatim dibersihkan. |
| `LeaveUsageService` | Pemakaian per jenis per tahun untuk satu karyawan: hari kalender dan hari kerja (memakai jadwal hari kerja + kalender libur yang berlaku, Fase 2). |
| Integrasi rekap | `AttendanceService` memuat izin Disetujui/Menunggu untuk rentang yang diminta lalu meneruskannya ke `DailyInput`; `DailyAttendanceCalculator` menambah status `IZIN` dan penanda `HADIR_SAAT_IZIN`, `IZIN_MENUNGGU` (K-076). TC-01–TC-08 tetap lulus tanpa perubahan. |
| Dashboard | Ringkasan ditambah "Izin/cuti hari ini" dan "Pengajuan menunggu" (Owner/HR). |

Konfigurasi baru: `app.uploads.dir`, `spring.servlet.multipart.max-file-size=5MB`, `max-request-size=6MB`. Nginx: `client_max_body_size 6m` (bawaan nginx hanya 1 MB). Docker: volume `uploads`.

### API

| Method | Path | Owner | HR/Finance | Karyawan |
|---|---|---|---|---|
| GET | `/api/leave-types` | ✓ | ✓ | ✓ (aktif saja) |
| POST/PUT | `/api/leave-types[/{id}]` | ✓ | ✗ | ✗ |
| GET | `/api/leave-requests?status&employeeId&from&to` | ✓ | ✓ | ✗ |
| GET | `/api/leave-requests/{id}` | ✓ | ✓ | ✗ |
| POST | `/api/leave-requests` (atas nama, `employeeId` wajib) | ✓ | ✓ | ✗ |
| POST | `/api/leave-requests/{id}/approve` · `/reject` · `/cancel` | ✓ | ✓ | ✗ |
| GET | `/api/leave-requests/usage?employeeId&year` | ✓ | ✓ | ✗ |
| POST | `/api/leave-requests/{id}/attachments` (multipart) | ✓ | ✓ | ✗ |
| GET | `/api/leave-requests/{id}/attachments/{aid}` | ✓ | ✓ | ✗ |
| GET/POST | `/api/me/leave-requests` | ✓* | ✓* | ✓ (milik sendiri) |
| GET | `/api/me/leave-requests/{id}` · POST `/cancel` · POST/GET `/attachments` | ✓* | ✓* | ✓ (milik sendiri; selain itu 404) |

\* Owner/HR hanya bila akunnya terhubung ke data karyawan; selain itu 403. Pengajuan mereka tetap harus diputus oleh Owner/HR lain (K-079).

Akses karyawan lewat `/api/me/...` agar aturan tetap per URL di `SecurityConfig`; kepemilikan diperiksa di service (pengajuan karyawan lain dibalas 404, bukan 403, agar ID tidak bisa ditebak). Unduhan lampiran: `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`.

## 5. Frontend

- **Izin & Cuti** (Owner/HR, menu Operasional, dengan angka pengajuan Menunggu): daftar dengan filter status (bawaan: Menunggu), cabang, karyawan, tanggal; detail berisi alasan, lampiran (lihat/unduh), pemakaian tahun ini per jenis, tombol Setujui / Tolak (alasan wajib) / Batalkan (alasan wajib); tombol **Catat izin** atas nama karyawan.
- **Jenis Izin/Cuti** (Owner, menu Pengaturan): memakai `CrudPage`.
- **Izin & Cuti Saya** (Karyawan): daftar pengajuan sendiri dan statusnya, form ajukan (jenis, tanggal mulai–selesai, alasan, unggah surat), tambah lampiran, batalkan selama Menunggu.
- **Absensi Harian / detail karyawan**: status `IZIN` tampil dengan nama jenisnya; penanda `HADIR_SAAT_IZIN` dan `IZIN_MENUNGGU`.
- **Dashboard**: kartu "Izin/cuti hari ini" dan "Pengajuan menunggu".

## 6. Pengujian

| Yang dibuktikan | Jenis |
|---|---|
| Alur Menunggu → Disetujui / Ditolak (alasan wajib) / Dibatalkan; transisi tidak sah ditolak | Integration |
| Tumpang tindih ditolak, termasuk dua pengajuan dikirim bersamaan (exclusion constraint) | Integration |
| Pengajuan Menunggu tidak mengubah rekap; Disetujui → status `IZIN` di hari kerja; hari libur dalam rentang tetap; scan saat izin → `HADIR_SAAT_IZIN` | Unit (kalkulator) + Integration |
| Jenis wajib surat tidak bisa disetujui tanpa lampiran | Integration |
| Tidak bisa menyetujui pengajuan sendiri (K-079) | Integration |
| Dua penyetuju bersamaan → satu berhasil, satu mendapat konflik (`@Version`) | Integration |
| Lampiran: tipe salah (mis. `.exe` diganti nama `.pdf`), > 5 MB, > 5 file → ditolak; unduhan memeriksa hak akses | Integration |
| Karyawan tidak bisa melihat/membatalkan/mengunduh milik karyawan lain (404) | Integration |
| Pemakaian per tahun: hari kalender vs hari kerja, rentang melewati pergantian tahun | Unit |
| Semua endpoint baru masuk matriks `AccessControlTests` | Integration |
| Audit tercatat untuk setiap perubahan; isi lampiran tidak masuk audit | Integration |
| TC-01–TC-08 tetap lulus | Regresi |
| Halaman Izin & Cuti, Izin & Cuti Saya, form ajukan | Frontend (Vitest) + uji manual Playwright |

## 7. Urutan pengerjaan

1. Migration V5 + jenis izin (CRUD, contoh awal).
2. Pengajuan + alur status + exclusion constraint + audit.
3. Lampiran (storage, validasi, unduh) + konfigurasi multipart/nginx/volume.
4. Integrasi rekap harian + dashboard + SSE.
5. Endpoint `/api/me` untuk karyawan + matriks akses.
6. Frontend: Jenis Izin/Cuti → Izin & Cuti (Owner/HR) → Izin & Cuti Saya → rekap/dashboard.
7. Uji end-to-end dengan Docker (akun Owner, HR, Karyawan) lalu perbarui dokumen.

## 8. Risiko dan batasan

- **Data pribadi sensitif** (surat dokter): hanya Owner, HR/Finance, dan karyawan pemiliknya yang bisa membuka; tidak ada pemindaian virus (di luar cakupan); file tidak ikut audit log.
- **Backup**: volume `uploads` harus ikut dicadangkan bersama database (dicatat untuk Fase 7).
- **Dampak ke gaji belum diterapkan**: kolom "dibayar" hanya disimpan; penerapannya di Fase 5 setelah Q-013 terjawab.
- Kuota dan saldo cuti, izin per jam, batas pengajuan tanggal lampau, dan kebijakan ketidakhadiran tanpa pemberitahuan **belum dibuat** (menunggu Q-013/Q-033).
- Karyawan belum bisa melihat rekap absensinya sendiri (Q-016, di luar Fase 4).

## 9. Pertanyaan terbuka baru

- **Q-033** — Batas pengajuan tanggal lampau: berapa hari setelah kejadian izin/sakit masih boleh diajukan? Apakah pengajuan setelah payroll final ditolak atau masuk prosedur koreksi?
- Q-013 diperbarui: bagian jenis, approver, pengaju, dan satuan sudah dijawab sementara (K-070–K-073); kuota, dokumen wajib per jenis, dibayar/tidak, dan izin mendadak masih terbuka.

## 10. Catatan implementasi

- Menu "Izin & Cuti" untuk Owner/HR tidak ditaruh di bottom navigation HP (sudah penuh); di HP diakses lewat lonceng notifikasi di header atau menu "Lainnya".
- Daftar pengajuan menunggu di lonceng diurutkan dari yang terbaru.
- Koneksi SSE kini dipakai bersama oleh semua komponen di satu tab (lonceng + halaman), bukan satu koneksi per halaman.
- Ditemukan saat uji: tipe kolom `sha256` diganti `VARCHAR(64)` (sebelumnya `CHAR`) agar cocok dengan validasi schema Hibernate; V5 belum pernah dijalankan di database mana pun saat itu.

## 11. Dokumen yang diperbarui setelah implementasi

`keputusan.md` (K-070–K-081), `aturan-bisnis.md` (bagian izin/cuti dan status `IZIN`), `pertanyaan-terbuka.md` (Q-013, Q-033), `arsitektur.md` §6.8, `rencana-pengerjaan.md`, `README.md` (indeks), dan rencana ini (status).
