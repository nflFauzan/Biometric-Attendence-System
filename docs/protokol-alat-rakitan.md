# Kontrak Protokol Alat Rakitan (`/device/v1`)

**Status:** berlaku sejak Rencana 2 (2026-10-17; K-087, K-088, K-099); perintah sejak Rencana 3 (2026-10-18; K-091, K-094, K-100) (spec [`superpowers/specs/2026-10-16-pendaftaran-sidik-jari-web-design.md`](./superpowers/specs/2026-10-16-pendaftaran-sidik-jari-web-design.md) §5, §6, §16). Dokumen ini acuan bersama untuk backend, simulator (`tools/device-simulator`), dan firmware ESP32.

Cakupan versi ini: pengaturan pertama, autentikasi, heartbeat, pengiriman scan, dan perintah `ENROLL` (pendaftaran sidik jari dari web, §6). Penyalinan dan penghapusan sidik jari, pengaturan jarak jauh, dan update firmware ditambahkan di rencana berikutnya. Field yang belum dikenal **wajib diabaikan** oleh kedua sisi, supaya versi berikutnya tidak memutus mesin lama.

## 1. Identitas mesin dan kode pengaturan

Saat admin menambah mesin berjenis **Alat rakitan** di web:

- Server membuat **ID mesin** `RK-` + 6 karakter heksadesimal huruf besar (contoh `RK-7F2A9C`), disimpan di `devices.serial_number`. ID ini tampil di web dan di layar mesin.
- Server membuat **kunci** 32 byte acak, dikirim ke browser **sekali** sebagai base64url tanpa padding (43 karakter). Server hanya menyimpan SHA-256 dari kunci.
- Browser menyusun **kode pengaturan** dari kunci itu dan alamat server yang diisi admin. Server tidak perlu tahu alamat yang dipakai mesin.

Format kode pengaturan:

```
BAS1.<base64url tanpa padding dari JSON berikut>
{"server":"http://192.168.43.20:8888","device":"RK-7F2A9C","key":"<kunci 43 karakter>"}
```

- `server`: alamat dasar yang dijangkau mesin, tanpa garis miring di akhir. Saat uji: IP laptop di jaringan hotspot HP + port mesin (`8888`, K-060/K-086).
- Firmware menolak kode yang tidak diawali `BAS1.` atau JSON-nya tidak lengkap, dengan pesan *"Kode pengaturan tidak valid"*.
- **"Buat kode baru"** di web membuat kunci baru dan **langsung membatalkan kunci lama** (cabut kunci, spec §6.1). Penggantian dua tahap tanpa memutus mesin dibuat di rencana pengaturan jarak jauh.

## 2. Aturan umum

| Hal | Ketentuan |
|---|---|
| Transport | HTTP saat development dan uji lokal. Produksi wajib HTTPS lewat reverse proxy (Fase 7); firmware memeriksa sertifikat. |
| Format | JSON UTF-8, `Content-Type: application/json`. |
| Autentikasi | Setiap request membawa `Authorization: Bearer <kunci>`. |
| Waktu | ISO-8601 **dengan offset**, presisi detik. Contoh `2026-10-17T08:01:02+07:00`. |
| Status online | Setiap request dari mesin **aktif** dengan kunci benar memperbarui "terakhir terlihat". Mesin nonaktif (403) tidak memperbaruinya (K-067). Mesin dianggap offline setelah `DEVICE_OFFLINE_AFTER` (K-064). |
| Jenis mesin | Endpoint ini hanya menerima mesin berjenis alat rakitan (`RAKITAN_V1`); ID alat rakitan ditolak di `/iclock`. |
| Field tak dikenal | Diabaikan oleh server dan firmware. |

## 3. Balasan gagal

| HTTP | Body | Arti | Yang dilakukan mesin |
|---|---|---|---|
| 401 | `{"error":"KUNCI_DITOLAK"}` | Header kosong, kunci salah, atau kunci sudah dicabut | Layar: *"Kunci ditolak. Buat kode pengaturan baru di web."* Scan tetap disimpan di microSD. |
| 403 | `{"error":"MESIN_NONAKTIF"}` | Kunci benar, tetapi mesin dinonaktifkan di web | Layar: *"Mesin dinonaktifkan di web."* Scan tetap disimpan. |
| 400 | `{"error":"FORMAT_SALAH"}` | Body bukan JSON yang sesuai kontrak | Kiriman tidak dihapus; dicoba lagi. Ini tanda bug firmware. |
| 413 | `{"error":"BATCH_TERLALU_BESAR"}` | Lebih dari 200 scan dalam satu kiriman | Kirim ulang dalam batch lebih kecil. |
| 404 | `{"error":"PERINTAH_TIDAK_DIKENAL"}` | ID perintah tidak ada, bukan UUID, atau milik mesin lain (§6) | Hentikan perintah itu dan jangan kirim ulang laporannya. |
| 409 | `{"error":"PERINTAH_TIDAK_AKTIF"}` | Laporan langkah untuk perintah yang sudah selesai, dibatalkan, atau kedaluwarsa (§6) | Hentikan pendaftaran, kembali ke "Siap absen". |
| 5xx / tidak ada balasan | `{"error":"GAGAL_SIMPAN"}` atau kosong | Server bermasalah atau tidak terjangkau | Coba lagi dengan jeda bertahap sampai maksimal 60 detik. Scan tetap disimpan. |

Mesin yang tidak dikenal tidak dibedakan dari kunci salah (401), supaya kunci tidak bisa ditebak lewat pesan error.

## 4. `POST /device/v1/heartbeat`

Dikirim setiap 5 detik, dan segera setelah mesin menyala atau tersambung kembali.

Request:

```json
{
  "firmware": "0.1.0",
  "ip": "192.168.43.51",
  "uptimeSeconds": 3600,
  "pendingScans": 0,
  "sensor": { "used": 12, "capacity": 150 }
}
```

Semua field opsional. `sensor` boleh tidak dikirim jika belum tersedia.

Response `200`:

```json
{
  "serverTime": "2026-10-17T09:41:00+07:00",
  "timezone": "Asia/Jakarta",
  "deviceName": "Uji Kampus",
  "branchName": "Cabang Uji",
  "commands": [
    {
      "id": "0b6f6c7e-3a43-4c47-9d0e-6d2b8d1f0a11",
      "type": "ENROLL",
      "pin": "1696",
      "finger": 1,
      "displayName": "Budi Santoso",
      "timeoutSeconds": 60
    }
  ],
  "cancel": []
}
```

- `serverTime` memakai zona waktu cabang mesin. Mesin menyetel RTC jika selisihnya lebih dari 2 detik.
- `deviceName` dan `branchName` ditampilkan di layar "Siap absen".
- `commands` berisi perintah yang belum selesai untuk mesin ini (kosong jika tidak ada). Server **mengirim ulang** perintah yang sama di setiap heartbeat sampai mesin melaporkan hasil, jadi mesin wajib **mengerjakan setiap `id` sekali saja** dan mengabaikan ID yang sedang atau sudah dikerjakan. Jenis perintah yang tidak dikenal firmware diabaikan.
- `cancel` berisi ID perintah yang dibatalkan dari web setelah sempat dikirim ke mesin (selama 2 menit setelah dibatalkan). Jika mesin sedang mengerjakan salah satunya, mesin menghentikannya tanpa mengirim hasil.

## 5. `POST /device/v1/scans`

Dikirim segera setelah scan, atau dalam batch saat ada antrean dari microSD (maksimal 200 per kiriman, urut dari yang terlama).

Request:

```json
{
  "scans": [
    { "id": "3f2b8c1e-5d0a-4b7e-9a51-0c6f2d8e4a17", "pin": "1696", "time": "2026-10-17T08:01:02+07:00", "verify": "FP" }
  ]
}
```

| Field | Ketentuan |
|---|---|
| `id` | UUID buatan mesin, unik per scan, tidak berubah saat dikirim ulang. |
| `pin` | PIN karyawan (teks, 1–20 karakter). |
| `time` | Waktu tempel jari dengan offset. |
| `verify` | Cara verifikasi, saat ini selalu `FP` (sidik jari). |

Response `200`, **dikirim hanya setelah scan tersimpan** (K-020):

```json
{ "accepted": ["3f2b8c1e-5d0a-4b7e-9a51-0c6f2d8e4a17"], "saved": 1, "duplicates": 0, "ignored": 0, "rejected": 0 }
```

- `accepted` berisi **semua ID yang sudah diproses server**: baru tersimpan, sudah pernah tersimpan (kiriman ulang), lebih lama dari "Terima scan sejak" (K-057), maupun baris yang ditolak. Mesin menghapus scan dari microSD **hanya** jika ID-nya ada di `accepted`.
- Satu scan yang rusak (tanpa `id`, `id` bukan UUID, `pin` kosong atau lebih dari 20 karakter, `time` tanpa offset, tahun `time` di luar 2000–2100, atau `verify` lebih dari 10 karakter) tidak menahan scan lain. Scan itu disimpan apa adanya di `rejected_scan_lines` (K-062), dihitung di `rejected`, dan ID-nya (jika ada) ikut di `accepted`.
- Scan disimpan lewat inti penerimaan scan yang sama dengan X401 (K-054): penanda `PIN_TIDAK_DIKENAL`, `CABANG_BERBEDA`, `WAKTU_DI_MASA_DEPAN`, rekap, dan dashboard tetap berlaku.
- Pencegahan scan ganda: ID scan dari mesin, ditambah kunci lama mesin + PIN + waktu (K-061).

## 6. Perintah dan pendaftaran sidik jari

Admin memulai pendaftaran dari web (Detail Karyawan → **Sidik Jari**). Server membuat perintah `ENROLL` dan mengirimnya lewat heartbeat (§4). Mesin tidak punya tombol, jadi semua langkah diikuti dari layar mesin dan dari web.

**Siklus status** (dilihat dari web):

| Status | Arti |
|---|---|
| `MENUNGGU` | Dibuat, belum ikut di balasan heartbeat. |
| `DIKIRIM` | Sudah ikut di balasan heartbeat; menunggu laporan mesin. |
| `BERHASIL` / `GAGAL` | Mesin melaporkan hasil. |
| `KEDALUWARSA` | Tidak diambil mesin dalam **30 detik**. |
| `DIBATALKAN` | Admin menekan **Batal** di web. |

**Batas waktu** (K-094; parameter teknis server, bukan aturan bisnis):

- Belum diambil mesin dalam 30 detik → `KEDALUWARSA`.
- Mesin menunggu jari paling lama `timeoutSeconds` (**60 detik**), lalu melaporkan `WAKTU_HABIS`.
- Sudah diambil tanpa hasil dalam 75 detik (60 + 15 detik cadangan, misalnya mesin putus) → server menandai `GAGAL` + `WAKTU_HABIS`.

**Selama pendaftaran** layar menampilkan *"Daftarkan jari {finger} — {displayName}"* lalu *"Tempelkan jari (n/3)"*. Mesin **tidak menerima absensi** sampai pendaftaran selesai, dibatalkan, atau habis waktunya. Satu mesin hanya mengerjakan satu `ENROLL` pada satu waktu.

### `POST /device/v1/commands/{id}/progress`

Dikirim setiap kali satu tempelan berhasil dibaca (3 tempelan per jari).

```json
{ "step": 2 }
```

`step` 1–3. Balasan `200 {}`. Langkah di luar 1–3 dibalas 400 `FORMAT_SALAH`. Perintah yang sudah tidak aktif dibalas 409 `PERINTAH_TIDAK_AKTIF`: mesin menghentikan pendaftaran.

### `POST /device/v1/commands/{id}/result`

Dikirim sekali di akhir. Jika tidak ada balasan atau 5xx, kirim ulang dengan jeda bertahap seperti scan.

```json
{ "status": "BERHASIL" }
{ "status": "GAGAL", "error": "JARI_SUDAH_TERDAFTAR", "pin": "1702" }
```

- `status` wajib `BERHASIL` atau `GAGAL`; `GAGAL` wajib membawa `error`. Selain itu dibalas 400 `FORMAT_SALAH`.
- Balasan `200 {}`, **dikirim hanya setelah hasil tersimpan**. Hasil yang dikirim ulang, atau hasil untuk perintah yang sudah selesai/dibatalkan, juga dibalas `200 {}` **tanpa efek**, supaya mesin berhenti mengirim. Jika admin membatalkan tepat saat mesin selesai, status tetap `DIBATALKAN`; selisih isi sensor dibereskan lewat rekonsiliasi (Rencana 4).
- **Template sidik jari tidak dikirim di versi ini.** Jika body membawa field `template`, server mengabaikannya: tidak disimpan, tidak dicatat di log maupun audit (K-100).

**Kode error** (`error`):

| Kode | Arti | Pesan di web |
|---|---|---|
| `JARI_TIDAK_COCOK` | Tiga tempelan tidak cocok satu sama lain | Tiga tempelan tidak cocok satu sama lain. Ulangi dengan jari yang sama. |
| `KUALITAS_BURUK` | Gambar sidik jari tidak terbaca jelas | Sidik jari tidak terbaca jelas. Bersihkan jari atau sensor, lalu ulangi. |
| `JARI_SUDAH_TERDAFTAR` | Sidik jari sudah ada di sensor; sertakan `pin` pemiliknya | Sidik jari ini sudah terdaftar atas nama {nama}. (`PIN {pin}` jika PIN tidak dikenal server) |
| `SENSOR_PENUH` | Kapasitas sensor penuh | Kapasitas sensor mesin penuh. |
| `WAKTU_HABIS` | Tidak ada jari dalam `timeoutSeconds` | Mesin tidak merespons. Periksa koneksi mesin. |
| `SENSOR_ERROR` | Sensor tidak menjawab atau error lain | Sensor bermasalah. Hubungi teknisi. |

Kode lain disimpan apa adanya (maksimal 40 karakter) dan tampil sebagai *"Pendaftaran gagal ({kode})."*

## 7. Pengaturan pertama di mesin (kontrak firmware)

1. Mesin tanpa pengaturan membuat hotspot `BAS-Setup-<4 hex terakhir MAC>` dengan sandi 8 digit yang tertera di label casing, lalu menampilkan keduanya di layar.
2. HP yang tersambung diarahkan ke halaman pengaturan (captive portal). Halaman itu menampilkan daftar Wi-Fi hasil pindai, isian sandi Wi-Fi, dan isian kode pengaturan.
3. Setelah disimpan, layar menampilkan setiap langkah:

| Langkah | Berhasil | Gagal (pesan di layar) |
|---|---|---|
| Wi-Fi | ✓ Wi-Fi tersambung | "Sandi Wi-Fi salah", atau "Wi-Fi butuh login halaman (seperti Wi-Fi kampus). Pakai hotspot HP." |
| Server | ✓ Server ditemukan | "Server <alamat> tidak terjangkau. Laptop menyala? Satu jaringan?" |
| Kunci | ✓ Kunci diterima | "Kunci ditolak. Buat kode pengaturan baru di web." (401) atau "Mesin dinonaktifkan di web." (403) |

4. Jika semua berhasil, layar menampilkan **"Siap absen"** dengan nama cabang dan jam. Jika ada yang gagal, hotspot pengaturan muncul lagi.

Uji pertama di kampus memakai **hotspot HP**: laptop dan mesin tersambung ke hotspot yang sama, dan alamat server di kode pengaturan memakai IP laptop di jaringan itu. Wi-Fi kampus biasanya butuh login halaman dan sering memblokir koneksi antar perangkat (lihat [`panduan-mesin-x401.md`](./panduan-mesin-x401.md) Tahap 2).

Alamat server bawaan di dialog kode pengaturan diambil dari `DEVICE_PUBLIC_URL` (`.env`) jika diisi, misalnya `http://192.168.43.20:8888`. Jika kosong, web memakai `http://<alamat web>:8888`. Alamat ini bisa diubah sebelum kode disalin.

## 8. Uji dengan simulator

Sampai firmware siap, [`tools/device-simulator/rakitan.mjs`](../tools/device-simulator/rakitan.mjs) berperan sebagai alat rakitan:

1. Jalankan backend versi terbaru (`docker compose up --build`, atau `start-dev.bat`).
2. Login sebagai Owner → **Mesin Absensi** → **Tambah** → Jenis mesin **Alat rakitan**. Isi alamat server `http://localhost:8888`, lalu **Salin kode**.
3. Jalankan, dengan kode tadi:

```
node tools/device-simulator/rakitan.mjs setup --code BAS1....
```

Hasil yang benar: "✓ Server ditemukan", "✓ Kunci diterima", "Siap absen — …", dan di web muncul pop-up "Mesin … baru saja terhubung".

| Perintah | Hasil yang benar |
|---|---|
| `scan --pin 9001` | 200, `saved: 1`; scan tampil di dashboard |
| `duplicate --pin 9001` | Kiriman kedua `duplicates: 1` |
| `offline --pin 9001 --count 3` | Satu batch, `saved: 3` |
| `bad-scan --pin 9001` | `saved: 1`, `rejected: 1`, kedua ID ada di `accepted` |
| `oversize` | 413 `BATCH_TERLALU_BESAR` |
| `wrong-key` | 401 `KUNCI_DITOLAK` |
| `live --pin 9001` | Status mesin Online selama simulator berjalan. Klik **Daftarkan** di Detail Karyawan → layar simulator *"Daftarkan jari 1 — …"*, progress 1–3, lalu dialog web *"Berhasil"* |
| `live --enroll-result KUALITAS_BURUK` | Dialog web menampilkan *"Sidik jari tidak terbaca jelas. Bersihkan jari atau sensor, lalu ulangi."* |
| `live --enroll-result JARI_SUDAH_TERDAFTAR --owner-pin 9002` | Dialog web menyebut nama karyawan pemilik PIN 9002 |
| `live` lalu **Batal** di web saat progress | Simulator mencetak *"Pendaftaran dibatalkan dari web"* dan tidak mengirim hasil |
