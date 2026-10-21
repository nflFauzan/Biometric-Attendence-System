# Kontrak Protokol Alat Rakitan (`/device/v1`)

**Status:** berlaku sejak Rencana 2 (2026-10-17; K-087, K-088, K-099); perintah sejak Rencana 3 (2026-10-18; K-091, K-094, K-100); hapus sidik jari sejak Rencana 5 (K-102); pengaturan jarak jauh sejak Rencana 6 (2026-10-20; K-104) (spec [`superpowers/specs/2026-10-16-pendaftaran-sidik-jari-web-design.md`](./superpowers/specs/2026-10-16-pendaftaran-sidik-jari-web-design.md) §5, §6, §16, §17, §18). Dokumen ini acuan bersama untuk backend, simulator (`tools/device-simulator`), dan firmware ESP32.

Cakupan versi ini: pengaturan pertama, autentikasi, heartbeat, pengiriman scan, perintah `ENROLL` (pendaftaran sidik jari dari web, §6), penghapusan sidik jari (§6a), pengaturan ringan (§4), serta perintah jaringan dan kunci (§6b). Penyalinan sidik jari dan update firmware ditambahkan di rencana berikutnya. Field yang belum dikenal **wajib diabaikan** oleh kedua sisi, supaya versi berikutnya tidak memutus mesin lama.

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
- **"Buat kode baru"** di web (wajib konfirmasi password Owner) membuat kunci baru dan **langsung membatalkan kunci lama** (cabut kunci, spec §6.1). Kunci tertunda dan perintah `ROTATE_KEY` yang aktif ikut dibuang. Untuk mengganti kunci tanpa memutus mesin, pakai **"Ganti kunci"** (`ROTATE_KEY`, §6b).

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
  "sensor": { "used": 12, "capacity": 150 },
  "settingsVersion": 3
}
```

Semua field opsional. `sensor` boleh tidak dikirim jika belum tersedia. Server menyimpan `used` dan `capacity` terakhir (hanya jika keduanya ada dan `capacity` > 0) dan menampilkan *"Sensor 12/150"* di halaman Mesin. Di atas 90% terisi muncul peringatan *"Kapasitas sensor hampir penuh."* (`app.device.sensor-warning-percent`, parameter teknis; K-102).

`settingsVersion` adalah versi paket pengaturan ringan yang sedang dipakai mesin: `0` jika belum pernah menerima paket (memakai bawaan firmware). Tidak dikirim dianggap `0`.

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
      "timeoutSeconds": 60,
      "network": null,
      "key": null
    }
  ],
  "cancel": [],
  "settings": {
    "version": 3,
    "headerText": "",
    "greeting": "Silakan tempelkan jari",
    "nameDisplay": "FULL",
    "brightness": 80,
    "buzzerVolume": 50,
    "duplicateNoticeSeconds": 60,
    "heartbeatSeconds": 5
  }
}
```

- `serverTime` memakai zona waktu cabang mesin. Mesin menyetel RTC jika selisihnya lebih dari 2 detik.
- `deviceName` dan `branchName` ditampilkan di layar "Siap absen".
- `commands` berisi perintah yang belum selesai untuk mesin ini (kosong jika tidak ada). Server **mengirim ulang** perintah yang sama di setiap heartbeat sampai mesin melaporkan hasil, jadi mesin wajib **mengerjakan setiap `id` sekali saja** dan mengabaikan ID yang sedang dikerjakan atau yang hasilnya belum diterima server. Setelah server membalas `200` untuk hasilnya, mesin boleh melupakan ID itu: perintah hapus yang muncul lagi dengan ID yang sama berarti admin menekan **"Coba lagi"** dan harus dikerjakan ulang (§6a). Jenis perintah yang tidak dikenal firmware diabaikan.
- `cancel` berisi ID perintah yang dibatalkan dari web setelah sempat dikirim ke mesin (selama 2 menit setelah dibatalkan). Jika mesin sedang mengerjakan salah satunya, mesin menghentikannya tanpa mengirim hasil.
- Setiap perintah memuat semua field (`id`, `type`, `pin`, `finger`, `displayName`, `timeoutSeconds`, `network`, `key`); field yang tidak dipakai jenis perintah itu bernilai `null`. `network` dan `key` hanya terisi untuk `APPLY_NETWORK` dan `ROTATE_KEY` (§6b).
- `settings` berisi **paket pengaturan ringan lengkap** jika versi server lebih dari 0 dan berbeda dari `settingsVersion` mesin; selain itu `null`. Paket juga dikirim jika versi mesin *lebih baru* dari server (misalnya setelah database dipulihkan). Mesin menerapkan paket, menyimpannya di memori sendiri (tetap berlaku saat offline dan setelah listrik mati), lalu melaporkan `version`-nya sebagai `settingsVersion` di heartbeat berikutnya. Selama server belum pernah menyimpan pengaturan untuk mesin itu (versi 0), tidak ada paket yang dikirim dan mesin memakai bawaan firmware, yang **wajib sama** dengan `app.device.settings.default.*` di server.

| Isian paket | Arti | Batas |
|---|---|---|
| `headerText` | Judul layar; kosong = nama cabang (`branchName`) | ≤ 32 karakter (sementara, Q-037) |
| `greeting` | Pesan sapaan di layar "Siap absen" | ≤ 64 karakter (sementara, Q-037) |
| `nameDisplay` | Nama yang tampil setelah scan: `FULL` (nama lengkap), `FIRST` (nama depan), `PIN` | salah satu dari tiga |
| `brightness` | Kecerahan layar (%) | 10–100 |
| `buzzerVolume` | Volume buzzer (%); 0 = mati | 0–100 |
| `duplicateNoticeSeconds` | Jeda tampilan "Sudah tercatat" untuk scan berulang; 0 = mati | 0–600 |
| `heartbeatSeconds` | Interval heartbeat | 2–20 (perintah `ENROLL` kedaluwarsa jika tidak diambil dalam 30 detik) |

Batas waktu pendaftaran tidak ada di paket (sudah di `timeoutSeconds` setiap `ENROLL`), dan zona waktu tidak ada di paket (sudah di `timezone` setiap heartbeat).

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
- Sudah diambil tanpa hasil dalam 75 detik (60 + 15 detik cadangan, misalnya mesin putus) → server menandai `GAGAL` + `TIDAK_ADA_HASIL` (K-101).

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

- `status` wajib `BERHASIL` atau `GAGAL`; `GAGAL` wajib membawa `error`. Field `detail` (opsional) dipakai perintah selain `ENROLL` untuk keterangan kegagalan, misalnya SSID yang gagal (§6b). Selain itu dibalas 400 `FORMAT_SALAH`.
- Balasan `200 {}`, **dikirim hanya setelah hasil tersimpan**. Hasil yang dikirim ulang, atau hasil untuk perintah yang sudah selesai/dibatalkan, juga dibalas `200 {}` **tanpa efek**, supaya mesin berhenti mengirim. Jika admin membatalkan tepat saat mesin selesai, yang tersimpan lebih dulu yang berlaku. Batal lebih dulu: status `DIBATALKAN`, hasil diabaikan, dan selisih isi sensor dibereskan lewat rekonsiliasi (Rencana 4). Hasil lebih dulu: status mengikuti hasil, dan admin mendapat pesan *"Perintah sudah selesai."*
- **Template sidik jari tidak dikirim di versi ini.** Jika body membawa field `template`, server mengabaikannya: tidak disimpan, tidak dicatat di log maupun audit (K-100).

**Kode error** (`error`):

| Kode | Arti | Pesan di web |
|---|---|---|
| `JARI_TIDAK_COCOK` | Tiga tempelan tidak cocok satu sama lain | Tiga tempelan tidak cocok satu sama lain. Ulangi dengan jari yang sama. |
| `KUALITAS_BURUK` | Gambar sidik jari tidak terbaca jelas | Sidik jari tidak terbaca jelas. Bersihkan jari atau sensor, lalu ulangi. |
| `JARI_SUDAH_TERDAFTAR` | Sidik jari sudah ada di sensor; sertakan `pin` pemiliknya | Sidik jari ini sudah terdaftar atas nama {nama}. (`PIN {pin}` jika PIN tidak dikenal server) |
| `SENSOR_PENUH` | Kapasitas sensor penuh | Kapasitas sensor mesin penuh. |
| `WAKTU_HABIS` | Tidak ada jari dalam `timeoutSeconds` | Tidak ada jari yang ditempelkan. Ulangi pendaftaran. |
| `SENSOR_ERROR` | Sensor tidak menjawab atau error lain | Sensor bermasalah. Hubungi teknisi. |

`TIDAK_ADA_HASIL` hanya diisi server (batas waktu 75 detik) dan tidak pernah dikirim mesin; pesannya *"Mesin tidak merespons. Periksa koneksi mesin."* Kode lain disimpan apa adanya (maksimal 40 karakter) dan tampil sebagai *"Pendaftaran gagal ({kode})."*

## 6a. Perintah hapus sidik jari (K-102)

Server mengirim `DELETE_TEMPLATE` saat admin menghapus satu jari, dan `DELETE_EMPLOYEE` saat karyawan dinonaktifkan atau tanggal keluarnya lewat. Perintah dikirim ke setiap alat rakitan yang pernah tersambung, termasuk yang nonaktif (diterima saat diaktifkan lagi), karena server tidak tahu pasti mesin mana yang masih menyimpan jari itu.

```json
{ "id": "…", "type": "DELETE_TEMPLATE", "pin": "1696", "finger": 1, "displayName": null, "timeoutSeconds": null }
{ "id": "…", "type": "DELETE_EMPLOYEE", "pin": "1696", "finger": null, "displayName": null, "timeoutSeconds": null }
```

- `DELETE_TEMPLATE` menghapus jari `finger` milik `pin`; `DELETE_EMPLOYEE` menghapus semua jari milik `pin`.
- **Idempoten:** jari atau PIN yang tidak ada di sensor tetap dilaporkan `BERHASIL`.
- Dikerjakan **sesuai urutan** di `commands`, tanpa layar langkah dan tanpa jeda absensi. Jika sedang ada `ENROLL`, perintah hapus menunggu sampai pendaftaran selesai.
- **Tanpa laporan langkah:** `progress` untuk perintah hapus dibalas 409 `PERINTAH_TIDAK_AKTIF`.
- Hasil memakai `POST /device/v1/commands/{id}/result` yang sama: `{ "status": "BERHASIL" }` atau `{ "status": "GAGAL", "error": "SENSOR_ERROR" }`. Kode lain tampil di web sebagai *"Penghapusan gagal ({kode})."*
- **Tidak kedaluwarsa dan tidak bisa dibatalkan:** perintah tetap dikirim ulang, berhari-hari jika mesin offline, sampai ada hasil. Tidak pernah muncul di `cancel`.
- Setelah `GAGAL`, admin bisa menekan **"Coba lagi"**: perintah dengan **ID yang sama** kembali ke `commands`, dan mesin mengerjakannya lagi (§4).
- Selama penghapusan di mesin aktif belum `BERHASIL`, PIN karyawan terkunci di web, dan `ENROLL` untuk PIN itu di mesin yang sama ditolak.

## 6b. Perintah jaringan dan kunci (K-104)

Hanya Owner yang bisa memulainya dari web (halaman Mesin → **Pengaturan** → bagian **Jaringan** atau **Kunci**), dan setiap permintaan wajib dikonfirmasi ulang dengan password. Aturan bersama untuk kedua perintah:

- **Satu per mesin.** Selama satu perintah jaringan/kunci masih `MENUNGGU` atau `DIKIRIM`, permintaan kedua untuk mesin yang sama ditolak di web.
- **Tidak bersamaan dengan pendaftaran.** Perintah ini tidak dikirim di heartbeat selama mesin punya `ENROLL` aktif, dan `ENROLL` ditolak selama perintah ini aktif.
- **Tidak kedaluwarsa selama `MENUNGGU`** (kecuali batas 24 jam `ROTATE_KEY`, lihat butir 6 di bawah). Mesin yang offline tetap menerimanya saat kembali online. Owner hanya bisa membatalkan selama `MENUNGGU`; setelah `DIKIRIM`, batal ditolak.
- Perintah ini memuat data rahasia (password Wi-Fi, kunci) dan hanya dikirim ke mesin pemiliknya. Produksi wajib HTTPS (§2). Firmware **tidak boleh** menampilkan atau mencatat password maupun kunci.

### `APPLY_NETWORK` (Wi-Fi dan/atau alamat server)

```json
{
  "id": "…", "type": "APPLY_NETWORK", "pin": null, "finger": null, "displayName": null, "timeoutSeconds": null,
  "network": {
    "wifi": [ { "ssid": "Kantor", "password": "rahasia123" }, { "ssid": "HP Kepala Cabang", "password": "" } ],
    "serverUrl": "https://absen.contoh.id:8888"
  },
  "key": null
}
```

- `wifi` adalah daftar Wi-Fi **berurutan** (1–5): mesin mencoba dari yang pertama. Password kosong berarti Wi-Fi terbuka. `wifi: null` berarti Wi-Fi tidak diubah; `serverUrl: null` berarti alamat server tidak diubah.
- **Coba dulu, baru simpan.** Mesin mencoba pengaturan baru sambil tetap menyimpan pengaturan lama. Jika dalam **2 menit** heartbeat dengan pengaturan baru berhasil (`200`), pengaturan baru disimpan permanen dan mesin melaporkan `{ "status": "BERHASIL" }`.
- **Kembali otomatis.** Jika gagal dalam 2 menit, mesin kembali ke pengaturan lama, lalu melaporkan `GAGAL` memakai pengaturan lama:

| `error` | `detail` | Arti |
|---|---|---|
| `WIFI_GAGAL` | SSID yang gagal | Tidak ada Wi-Fi di daftar yang bisa disambungkan. Web: *"Gagal tersambung ke Wi-Fi '{detail}', kembali ke pengaturan lama."* |
| `SERVER_TIDAK_TERJANGKAU` | alamat server baru | Wi-Fi tersambung, tetapi heartbeat ke server baru gagal. Web: *"Server baru tidak terjangkau, kembali ke pengaturan lama."* |

Kode lain disimpan apa adanya dan tampil sebagai *"Perubahan jaringan gagal ({kode})."*

- Server menandai perintah `DIKIRIM` tanpa hasil dalam **10 menit** sebagai `GAGAL` + `TIDAK_ADA_HASIL` (`app.device.network.result-timeout`).
- **Pindah server:** server baru harus sudah berjalan dan mengenali mesin yang sama (data mesin dan kuncinya ikut dipindahkan), karena keberhasilan diukur dari heartbeat ke server baru. Hasil `BERHASIL` dikirim ke server baru.

### `ROTATE_KEY` (ganti kunci tanpa memutus mesin)

```json
{ "id": "…", "type": "ROTATE_KEY", "pin": null, "finger": null, "displayName": null, "timeoutSeconds": null, "network": null, "key": "<kunci baru 43 karakter>" }
```

1. Server membuat kunci baru. Selama masa transisi, **kunci lama dan kunci baru sama-sama diterima**.
2. Mesin menyimpan kunci baru **di samping** kunci lama, lalu memakai kunci baru mulai request berikutnya. Kunci lama baru dihapus dari memori mesin setelah request dengan kunci baru dibalas `200`:
   - tidak ada balasan atau 5xx → tetap simpan keduanya dan coba lagi dengan kunci baru (perpindahan mungkin sudah terjadi di server walau balasannya hilang);
   - 401 dengan kunci baru → coba kunci lama; jika kunci lama diterima, kunci baru dibuang;
   - 401 dengan kedua kunci → kunci sudah dicabut; layar *"Kunci ditolak. Buat kode pengaturan baru di web."*
3. **Kontak pertama dengan kunci baru** memindahkan kunci baru menjadi kunci utama dan menyelesaikan perintah sebagai `BERHASIL`. Setelah itu **kunci lama ditolak** (401 `KUNCI_DITOLAK`), jadi kode pengaturan lama tidak berlaku lagi.
4. **Hasil tidak wajib.** Mesin tidak perlu mengirim `result` untuk `ROTATE_KEY` yang berhasil; hasil yang tetap dikirim dibalas `200 {}` tanpa efek.
5. **Gagal menyimpan.** Mesin yang tidak bisa menyimpan kunci baru melaporkan `{ "status": "GAGAL", "error": "…" }` **memakai kunci lama**. Kunci baru dibuang server, dan kunci lama tetap berlaku.
6. **24 jam.** Jika kunci baru tidak dipakai dalam 24 jam sejak perintah dibuat (`app.device.rotate-key.timeout`), perintah `GAGAL` + `KUNCI_BARU_TIDAK_DIPAKAI` dan kunci lama tetap berlaku.
7. **"Buat kode baru" selama transisi** mencabut kunci lama dan membuang kunci baru sekaligus; `ROTATE_KEY` yang sudah dikirim muncul di `cancel`. Mesin harus diatur ulang dengan kode pengaturan baru (§7).

Kode gagal lain tampil di web sebagai *"Ganti kunci gagal ({kode})."*

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
| `live` lalu **Hapus** jari di Detail Karyawan | Simulator mencetak *"Sidik jari dihapus"*; daftar "Penghapusan di mesin" tidak lagi memuat mesin ini |
| `live --delete-result SENSOR_ERROR` lalu **Hapus** | Web menampilkan *"Gagal: Sensor bermasalah. Hubungi teknisi."*; **Coba lagi** membuat simulator mengerjakannya lagi |
| `live --sensor-used 140 --sensor-capacity 150` | Halaman Mesin menampilkan *"Sensor 140/150"* dan *"Kapasitas sensor hampir penuh."* |
| `live` lalu ubah **Tampilan & suara** di dialog Pengaturan | Simulator mencetak *"Pengaturan versi N diterapkan: …"*; halaman Mesin menampilkan *"Pengaturan terbaru sudah diterapkan ✓"*. Interval heartbeat simulator mengikuti `heartbeatSeconds` |
| `live` lalu **Jaringan** dengan alamat server `http://localhost:8888` | Simulator mencetak Wi-Fi (password disamarkan `***`), mencoba alamat baru, lalu *"Jaringan baru tersimpan"*; web menampilkan *"Jaringan baru diterapkan"* dan jaringan yang berlaku |
| `live` lalu **Jaringan** dengan alamat server yang tidak berjalan, mis. `http://localhost:9999` | Simulator kembali ke alamat lama; web menampilkan *"Server baru tidak terjangkau, kembali ke pengaturan lama."* |
| `live --network-result WIFI_GAGAL --network-detail Kantor` lalu **Jaringan** | Web menampilkan *"Gagal tersambung ke Wi-Fi 'Kantor', kembali ke pengaturan lama."* |
| `live` lalu **Ganti kunci** | Simulator mencetak *"Kunci baru dipakai — kode pengaturan lama tidak lagi berlaku"*; web menampilkan *"Kunci baru dipakai"*. Simulator yang dijalankan ulang dengan kode lama mendapat 401 |
| `live --rotate-result SENSOR_ERROR` lalu **Ganti kunci** | Simulator melapor gagal memakai kunci lama; web menampilkan *"Ganti kunci gagal (SENSOR_ERROR)."* dan mesin tetap Online |
