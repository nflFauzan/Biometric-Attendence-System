# Desain: Pendaftaran Karyawan dan Sidik Jari dari Web untuk Alat Rakitan

**Status:** disetujui per bagian dalam sesi brainstorming 2026-10-16, menunggu review spec tertulis. Revisi 2026-10-16: ditambah pengaturan mesin jarak jauh dan update firmware lewat web (§7).
**Cakupan:** server dan web di repo ini, kontrak protokol alat rakitan, dan simulator. Firmware ESP32 di luar repo ini sampai Q-034 dijawab.

## 1. Latar belakang dan tujuan

Alat rakitan (ESP32 DevKit, sensor R503, TFT 2,4" non-touch, RTC DS3231, microSD, buzzer) **tidak punya tombol**. Karena itu semua pengelolaan karyawan, sidik jari, dan pengaturan mesin harus dilakukan dari web. Mesin hanya menjalankan perintah server dan menampilkan petunjuk di layar.

**Firmware** adalah program yang berjalan di ESP32 (membaca sensor, menampilkan layar, menyimpan scan, berbicara dengan server). Firmware adalah klien dari protokol `/device/v1` (§5); backend di repo ini adalah servernya. Prinsip desainnya: **firmware sederhana dan jarang berubah, pengaturan diatur dari web.**

Kondisi saat ini:
- Form karyawan dan kolom PIN mesin (`Employee.devicePin`, K-056) sudah ada.
- Komunikasi mesin hanya satu arah (ZK-Push, mesin → server). `/iclock/getrequest` tidak pernah menitipkan perintah.
- Sidik jari didaftarkan langsung di mesin X401 oleh orang di depan mesin.

Tujuan fitur:
1. Admin mendaftarkan karyawan sekali di web, lalu memicu pendaftaran sidik jari di mesin mana pun dari web.
2. Karyawan bisa absen di semua cabang. Template sidik jari disalin otomatis ke semua mesin aktif.
3. Data biometrik terlindungi: terenkripsi di server, tidak pernah sampai ke browser, dan dihapus saat karyawan berhenti.
4. Menutup celah rekap akibat scan berulang dalam waktu singkat ("spam scan").
5. Setelah pengaturan awal, mesin tidak perlu didatangi atau dibongkar untuk mengubah pengaturan, Wi-Fi, alamat server, kunci, atau firmware.

Kriteria berhasil:
- Admin bisa mendaftarkan sidik jari dari web dan melihat statusnya berubah secara langsung, tanpa menyentuh mesin selain menempelkan jari.
- Sidik jari yang didaftarkan di satu cabang bisa dipakai absen di cabang lain setelah mesin itu online.
- Template tidak pernah muncul di response `/api/**` (dibuktikan dengan test).
- Spam scan pada pagi hari tanpa scan pulang menghasilkan status **Tidak Lengkap**.
- Pengaturan Wi-Fi atau alamat server yang salah tidak membuat mesin kehilangan koneksi permanen (kembali otomatis ke pengaturan lama).
- Firmware baru bisa dipasang dari web, dan firmware yang gagal kembali otomatis ke versi lama.

## 2. Prasyarat hardware (wajib dibuktikan)

Kemampuan berikut **belum terbukti** dan tidak boleh dianggap ada (aturan "jangan mengarang kemampuan mesin"):

| Kemampuan | Dipakai untuk | Bukti saat ini |
|---|---|---|
| Template dikirim dari sensor ke ESP32 | Menyimpan template di server | Fungsi `getModel` di library Adafruit memindahkan template lewat UART (sumber sekunder) |
| Template dikirim dari ESP32 ke sensor lalu disimpan | Menyalin ke mesin lain | Belum ditemukan dokumentasinya |
| Pencarian 1:N di sensor | Cek jari ganda, identifikasi saat absen | Fitur umum seri R50x, belum diuji |
| Password modul sensor | Mengurangi risiko jika mesin dicuri | Belum diuji |
| Kapasitas template | Batas jumlah sidik jari per mesin | Listing R503-S menyebut 150; varian yang dibeli belum dipastikan |
| Update firmware lewat Wi-Fi dengan partisi ganda dan rollback otomatis | Update firmware dari web (§7.4) | Fitur ESP-IDF (OTA + app rollback), belum diuji di board yang dipakai; ukuran flash board menentukan apakah dua partisi muat |

**Uji kelayakan R503** dengan 1 sensor, dikerjakan pengguna:
1. Daftarkan sidik jari ke slot 1.
2. Kirim template dari sensor ke ESP32, lalu simpan.
3. Hapus slot 1.
4. Kirim template itu kembali ke sensor dan simpan di slot 1.
5. Tempelkan jari. **Lulus** jika jari dikenali sebagai slot 1.

**Jika uji gagal:** penyalinan antar mesin dimatikan, dan sidik jari didaftar ulang di setiap mesin (pilihan "C" saat brainstorming). Antrean perintah, alur pendaftaran, dan protokol tetap berlaku; yang tidak dibangun hanya penyimpanan template di server dan perintah `STORE_TEMPLATE`. Hasil uji dicatat di Q-035.

**Uji kelayakan OTA** (dikerjakan pengguna): pasang firmware A, update ke firmware B lewat Wi-Fi, lalu update ke firmware C yang sengaja gagal memeriksa dirinya. **Lulus** jika B berjalan, dan setelah C mesin kembali sendiri ke B. Jika gagal, update firmware tetap lewat kabel USB dan §7.4 tidak dibangun. Hasil dicatat di Q-036.

Kecukupan komponen untuk prototipe: cukup. Kebutuhan yang belum tercakup daftar komponen, yaitu pengaturan Wi-Fi dan reset tanpa tombol, diselesaikan lewat software (§6.4).

## 3. Alur pengguna

### 3.1 Tambah karyawan
Form karyawan tidak berubah. PIN mesin menjadi identitas karyawan di semua mesin rakitan; karyawan tidak pernah mengetiknya. PIN wajib terisi sebelum sidik jari bisa didaftarkan.

### 3.2 Daftarkan sidik jari
Di halaman Detail Karyawan ada bagian baru **"Sidik Jari"**.

1. Admin mencentang **"Karyawan sudah menyetujui penyimpanan sidik jari"** (K-091). Centang ini wajib satu kali per karyawan, dan siapa yang mencentang serta kapan dicatat di audit log. Formulir persetujuan kertas diurus klien.
2. Admin klik **"Daftarkan sidik jari"**, lalu memilih:
   - **mesin:** bawaannya mesin di cabang karyawan. Hanya mesin protokol rakitan yang *Online* yang bisa dipilih.
   - **jari ke-1 atau ke-2:** maksimal 2 jari per karyawan.
3. Dialog web menampilkan status langsung lewat SSE: `Menunggu mesin… → Tempelkan jari (1/3) → (2/3) → (3/3) → Berhasil`.
4. Pada saat yang sama, layar mesin menampilkan nama karyawan dan langkahnya, disertai buzzer dan lampu cincin R503. Selama pendaftaran berlangsung, mesin itu tidak menerima absensi.
5. Batas waktu pendaftaran di mesin 60 detik. Admin bisa menekan **"Batal"**. Kalau waktu habis atau dibatalkan, mesin kembali ke mode absensi.
6. Kalau jari itu sudah terdaftar atas nama karyawan lain, pendaftaran ditolak dan nama pemiliknya ditampilkan.

### 3.3 Salin ke mesin lain
Setelah pendaftaran berhasil, server membuat perintah `STORE_TEMPLATE` untuk setiap mesin rakitan aktif lainnya. Bagian "Sidik Jari" menampilkan status per mesin: *Tersimpan*, *Menunggu (mesin offline)*, atau *Gagal* (dengan tombol **"Coba lagi"**). Mesin rakitan baru otomatis menerima semua sidik jari karyawan aktif.

### 3.4 Hapus sidik jari
Tombol **"Hapus"** per jari membuat perintah `DELETE_TEMPLATE` ke semua mesin dan menghapus template di server.

### 3.5 Karyawan nonaktif atau keluar (K-090)
Saat karyawan dinonaktifkan atau tanggal keluarnya terisi, server membuat perintah `DELETE_EMPLOYEE` ke semua mesin rakitan dan menghapus seluruh templatenya di server. Semuanya terjadi dalam transaksi yang sama dengan perubahan karyawan.

### 3.6 Hak akses
Sama dengan halaman Karyawan saat ini: Owner dan HR/Finance. Semua tindakan (persetujuan, pendaftaran, pembatalan, hapus, coba lagi) dicatat lewat `AuditService` di transaksi yang sama. Hak akses pengaturan mesin ada di §7.5.

### 3.7 Mesin X401 (`ZK_PUSH`)
Mesin X401 tetap berjalan seperti sekarang. Mesin ber-protokol `ZK_PUSH` tidak menerima perintah, dan bagian Sidik Jari menampilkan keterangan *"Mesin ini tidak mendukung pendaftaran dari web; daftarkan langsung di mesin."* Pengaturan jarak jauh (§7) juga tidak berlaku untuk X401.

## 4. Antrean perintah ke mesin

Modul baru `devicecommand` (entity, repository, DTO record, service, controller) dan tabel baru lewat migration Flyway.

### 4.1 Jenis perintah

| Jenis | Isi | Dipicu oleh | Kedaluwarsa |
|---|---|---|---|
| `ENROLL` | PIN, jari (1/2), nama tampilan | Admin | 30 detik jika belum diambil mesin |
| `STORE_TEMPLATE` | PIN, jari, **rujukan** ke template | Otomatis (§3.3, §4.4) | Tidak |
| `DELETE_TEMPLATE` | PIN, jari | Admin | Tidak |
| `DELETE_EMPLOYEE` | PIN | Karyawan nonaktif/keluar | Tidak |
| `APPLY_NETWORK` | Daftar Wi-Fi dan/atau alamat server, sebagai **rujukan** | Owner (§7.3) | Tidak |
| `ROTATE_KEY` | Kunci baru, sebagai **rujukan** | Owner (§7.3) | Tidak |
| `UPDATE_FIRMWARE` | Rujukan ke firmware (versi, URL unduh, SHA-256) | Owner (§7.4) | Tidak |

Status: `MENUNGGU → DIKIRIM → BERHASIL | GAGAL | KEDALUWARSA | DIBATALKAN`. Perintah `APPLY_NETWORK`, `ROTATE_KEY`, dan `UPDATE_FIRMWARE` yang masih `MENUNGGU` bisa dibatalkan Owner.

### 4.2 Aturan
- **Idempoten.** Menjalankan ulang perintah yang sama tidak menimbulkan efek ganda: menyimpan template yang sama menimpa slot yang sama, dan menghapus sesuatu yang sudah tidak ada tetap dianggap berhasil. Laporan hasil yang dikirim ulang diabaikan berdasarkan ID perintah.
- **Satu `ENROLL` aktif per mesin.** Permintaan kedua ditolak dengan pesan *"Mesin sedang dipakai untuk pendaftaran."*
- **Satu `ENROLL` aktif per karyawan + jari.**
- **Satu perintah berisiko aktif per mesin** (`APPLY_NETWORK`, `ROTATE_KEY`, `UPDATE_FIRMWARE`). Perintah berisiko tidak dijalankan selama ada `ENROLL` aktif, dan `ENROLL` ditolak selama ada perintah berisiko yang sedang berjalan.
- Baris perintah hanya berisi rujukan untuk data rahasia (template, password Wi-Fi, kunci). Isinya disisipkan saat respons heartbeat dibuat, sehingga riwayat perintah tidak berisi data rahasia.

### 4.3 Pembagian tanggung jawab
- **Server** hanya mengenal *PIN + jari ke-1/2*, tanpa nomor slot dan tanpa jenis sensor.
- **Mesin** mengelola tabel *slot sensor ↔ PIN + jari* di memorinya sendiri. Mengganti merek sensor tidak mengubah server.

### 4.4 Rekonsiliasi
Heartbeat membawa ringkasan isi mesin: jumlah template dan sidik digital (hash) dari daftar PIN + jari yang tersimpan. Kalau berbeda dengan catatan server, mesin mengirim daftar lengkapnya, lalu server membuat perintah `STORE_TEMPLATE` atau `DELETE_*` sampai isinya cocok. Cara yang sama dipakai untuk:
- mesin baru didaftarkan,
- mesin di-reset (sensor kosong),
- karyawan dinonaktifkan saat mesinnya offline.

## 5. Protokol alat rakitan (K-087)

Adapter kedua menurut K-054, di paket `ingest/rakitan`, dengan endpoint di bawah `/device/v1`. Protokol ini tidak meniru ZK-Push (menjawab Q-030).

| Endpoint | Fungsi |
|---|---|
| `POST /device/v1/heartbeat` | Dikirim setiap 5 detik (nilai dari pengaturan mesin, §7.2). Request membawa versi firmware, versi pengaturan yang dipakai, kapasitas sensor (terpakai/total), ringkasan isi (§4.4), dan jumlah scan yang belum terkirim. Response berisi jam server (untuk menyetel RTC), paket pengaturan jika ada versi baru, dan daftar perintah yang menunggu. |
| `POST /device/v1/scans` | Batch scan. Setiap scan membawa **ID unik** buatan mesin (UUID), PIN, dan waktu **beserta offset zona waktu**. Response berisi daftar ID yang sudah tersimpan. Mesin menghapus scan dari microSD hanya jika ID-nya ada di daftar itu (K-020). |
| `POST /device/v1/commands/{id}/progress` | Tahap pendaftaran (1/3, 2/3, 3/3) atau tahap update firmware (mengunduh, memasang, restart), diteruskan ke web lewat SSE. |
| `POST /device/v1/commands/{id}/result` | Hasil akhir: `BERHASIL` atau `GAGAL` + kode error. Untuk `ENROLL` yang berhasil, hasilnya membawa template (base64). |
| `POST /device/v1/inventory` | Daftar lengkap PIN + jari di mesin, dikirim saat ringkasan isi berbeda (§4.4). |
| `GET /device/v1/firmware/{id}` | Unduh file firmware untuk perintah `UPDATE_FIRMWARE` (§7.4). Hanya untuk mesin yang punya perintah aktif untuk firmware itu. |

- Pencegahan scan ganda memakai ID scan dari mesin, sehingga lebih kuat daripada kunci SN + PIN + waktu pada X401 (K-055). Scan tetap melewati inti penerimaan scan yang sama (validasi mesin, simpan scan asli, penanda, rekap, dashboard).
- Interval heartbeat adalah parameter teknis, bukan aturan bisnis. Polling dipilih daripada WebSocket atau MQTT karena dengan 3 mesin bebannya kurang dari 1 request per detik, lebih mudah dibuat di ESP32, dan lebih tahan saat Wi-Fi putus-sambung.
- Kolom `devices.protocol` yang sudah ada mendapat nilai baru untuk alat rakitan (misalnya `RAKITAN_V1`).
- Rincian format request dan response (nama field, kode HTTP, contoh) ditulis sebagai dokumen kontrak terpisah di `docs/` saat implementasi, dan menjadi acuan firmware dan simulator.

## 6. Keamanan

### 6.1 Kunci rahasia per mesin (K-088)
- Saat admin menambah mesin protokol rakitan, server membuat ID mesin + kunci acak 32 byte. Kunci **hanya ditampilkan sekali**, untuk diisi lewat hotspot saat pengaturan awal (§6.4).
- Server hanya menyimpan hash kunci. Setiap request mesin membawa `Authorization: Bearer <kunci>`.
- Mesin tak dikenal, kunci salah, atau mesin nonaktif dibalas 401 tanpa sukses (sejalan dengan K-067).
- Dua cara mengganti kunci:
  - **"Ganti kunci"** (mesin online, rutin): dua tahap lewat `ROTATE_KEY` (§7.3). Mesin tidak perlu didatangi.
  - **"Cabut kunci"** (mesin hilang atau dicuri): kunci lama langsung tidak berlaku. Mesin harus diatur ulang lewat hotspot dengan kunci baru.

### 6.2 Jalur koneksi
HTTPS wajib di produksi, dan ESP32 memeriksa sertifikat server. HTTP biasa hanya boleh lewat pengaturan development yang mati secara bawaan.

### 6.3 Data rahasia di server (K-089)
- **Template sidik jari** dan **password Wi-Fi mesin** disimpan terenkripsi **AES-256-GCM**. Kunci enkripsi dibaca dari environment variable, tidak disimpan di database, dan tidak di-commit.
- Keduanya **tidak pernah** ada di response `/api/**`. Web hanya menerima status. Password Wi-Fi bersifat *tulis saja*: web menampilkan nama Wi-Fi, tetapi password tidak pernah ditampilkan lagi setelah disimpan.
- Data rahasia keluar ke mesin hanya lewat respons heartbeat (HTTPS).
- Penghapusan template adalah hapus permanen. Audit log mencatat kejadiannya tanpa isi template. Ini tidak bertentangan dengan K-019, karena template bukan scan.

### 6.4 Pengaturan awal mesin (kontrak firmware, K-095)
Pengaturan awal dilakukan **sekali per mesin** dan tidak bisa lewat web, karena mesin yang belum tahu Wi-Fi dan alamat server belum bisa dihubungi web.

1. **Teknisi menanam firmware** ke ESP32 lewat kabel USB saat merakit.
2. **Mesin yang belum diatur otomatis membuat hotspot** `BAS-Setup-xxxx` tanpa menekan tombol apa pun. Layar menampilkan nama hotspot dan passwordnya (password juga tertempel di label dalam casing).
3. Dari HP, teknisi atau admin mengisi Wi-Fi, alamat server, ID mesin, dan kunci (§6.1). Setelah disimpan dan mesin berhasil mengirim heartbeat, hotspot mati.

Pengaturan awal bisa dilakukan di kantor sebelum mesin dikirim ke cabang, sekaligus mengisi Wi-Fi cabang tujuan.

**Hotspot cadangan:** kalau mesin tidak bisa tersambung ke Wi-Fi mana pun selama 10 menit, hotspot pengaturan muncul lagi dengan sendirinya, sementara mesin tetap mencoba Wi-Fi yang tersimpan. Ini menangani kasus "password Wi-Fi kantor diganti sebelum sempat diperbarui di web", cukup dengan HP dan tanpa bongkar casing.

**Tombol BOOT** di dalam casing hanya dipakai untuk reset pabrik (ditahan 15 detik). Reset pabrik **ditolak jika masih ada scan yang belum terkirim**, dan layar menampilkan jumlahnya.

### 6.5 Risiko yang diterima
- Jika mesin dicuri, template masih tersimpan di sensor. Penanganan: password modul sensor (jika terbukti didukung), lalu Owner menonaktifkan mesin dan memakai "Cabut kunci".
- Selama hotspot pengaturan aktif, siapa pun di dekat mesin yang tahu passwordnya bisa membuka halaman pengaturan. Penanganan: password hotspot unik per mesin, dan halaman pengaturan tidak menampilkan kunci atau password Wi-Fi yang sudah tersimpan.

## 7. Pengaturan mesin jarak jauh dan update firmware

Setelah pengaturan awal (§6.4), semua pengaturan dilakukan dari halaman **Mesin** di web. Mesin tidak perlu didatangi.

### 7.1 Jenis pengaturan

| Kelompok | Isi | Risiko | Cara diterapkan |
|---|---|---|---|
| **Ringan** | Teks layar (nama perusahaan/cabang, pesan sapaan), tampilan nama (nama lengkap / nama depan / PIN), kecerahan layar, volume buzzer atau buzzer mati, jeda tampilan "Sudah tercatat" (bawaan 1 menit), batas waktu pendaftaran (bawaan 60 detik), interval heartbeat (bawaan 5 detik) | Rendah, karena salah isi tidak memutus koneksi | Paket pengaturan berversi (§7.2) |
| **Jaringan** | Daftar Wi-Fi (beberapa, berurutan; misalnya Wi-Fi utama + hotspot HP kepala cabang sebagai cadangan), alamat server | Tinggi | `APPLY_NETWORK`, coba dulu baru simpan (§7.3) |
| **Kunci** | Kunci rahasia mesin | Tinggi | `ROTATE_KEY`, dua tahap (§7.3) |
| **Firmware** | Versi program ESP32 | Tinggi | `UPDATE_FIRMWARE` dengan rollback (§7.4) |

Zona waktu mesin tidak diatur terpisah; mesin mengikuti zona waktu cabangnya dan menerimanya lewat paket pengaturan.

Nilai bawaan pengaturan ringan adalah parameter teknis firmware, bukan aturan bisnis (berbeda dengan Pengaturan Aturan, K-047).

### 7.2 Paket pengaturan berversi (pengaturan ringan)
- Setiap mesin punya paket pengaturan dengan nomor versi yang naik setiap kali diubah di web.
- Respons heartbeat menyertakan paket lengkap hanya jika versi mesin lebih lama.
- Mesin menyimpan paket di memorinya sendiri, sehingga pengaturan tetap berlaku saat offline atau setelah listrik mati.
- Halaman Mesin menampilkan *"Pengaturan terbaru sudah diterapkan ✓"* atau *"Menunggu mesin menerapkan versi N"*.
- Pengaturan bisa diubah untuk satu mesin atau untuk semua mesin sekaligus.

### 7.3 Jaringan dan kunci: coba dulu, baru simpan (K-096)
**`APPLY_NETWORK`** (Wi-Fi dan/atau alamat server):
1. Mesin menerima pengaturan baru dan mencobanya, sementara pengaturan lama tetap tersimpan.
2. Kalau dalam 2 menit mesin berhasil mengirim heartbeat dengan pengaturan baru, pengaturan baru disimpan dan perintah dilaporkan `BERHASIL`.
3. Kalau gagal, mesin **kembali otomatis ke pengaturan lama** lalu melaporkan `GAGAL` beserta alasannya, misalnya *"Gagal tersambung ke Wi-Fi 'Kantor-5G', kembali ke pengaturan lama."*

Untuk pindah alamat server, server baru harus sudah berjalan dan mengenali mesin yang sama (data mesin dan kuncinya ikut dipindahkan), karena keberhasilan diukur dari heartbeat ke server baru.

**`ROTATE_KEY`:**
1. Server membuat kunci baru dan menerima **kunci lama dan kunci baru** untuk mesin itu.
2. Mesin menerima kunci baru, lalu mengirim heartbeat memakai kunci baru.
3. Begitu heartbeat dengan kunci baru diterima, kunci lama tidak berlaku lagi.
4. Kalau mesin tidak pernah memakai kunci baru dalam 24 jam, perintah dianggap gagal, kunci baru dibuang, dan kunci lama tetap berlaku.

### 7.4 Update firmware lewat web (K-097)
1. **Owner mengunggah file firmware** (`.bin`) di halaman baru **"Firmware"**, lengkap dengan nomor versi dan catatan perubahan. Server menghitung SHA-256 dan menyimpan file (bukan di database).
2. **Owner memilih mesin tujuan.** Web menyarankan **satu mesin dulu** sebagai percobaan, dan update ke mesin lain baru disarankan setelah percobaan berhasil.
3. Mesin menerima `UPDATE_FIRMWARE` dan **hanya menjalankannya saat menganggur** (tidak ada `ENROLL` aktif dan tidak ada karyawan yang sedang menempelkan jari).
4. Mesin mengunduh lewat HTTPS (`GET /device/v1/firmware/{id}`), memeriksa SHA-256, lalu menulis ke **partisi kedua**, sementara firmware lama tetap utuh. Setelah itu mesin restart.
5. Firmware baru memeriksa dirinya: sensor terbaca, layar berjalan, dan heartbeat ke server berhasil. Kalau lulus, firmware baru ditandai sah dan mesin melaporkan `BERHASIL` dengan versi barunya. Kalau gagal, ESP32 **otomatis kembali ke firmware lama**, yang lalu melaporkan `GAGAL`.
6. Scan yang belum terkirim di microSD tetap aman selama dan setelah update.
7. Web menampilkan versi firmware setiap mesin dan progres update lewat SSE (mengunduh, memasang, restart, berhasil/gagal).

Batasan: kalau firmware baru rusak **dan** rollback juga gagal (misalnya listrik mati tepat saat menulis), mesin harus didatangi untuk dipasang ulang lewat kabel USB. UPS di daftar komponen mengurangi kemungkinan ini.

Penandatanganan digital firmware (bukan hanya SHA-256) di luar cakupan versi ini; keaslian file dijaga lewat hak akses unggah (§7.5) dan HTTPS.

### 7.5 Hak akses (K-098)

| Tindakan | Owner | HR/Finance |
|---|---|---|
| Pengaturan ringan | ✅ | ✅ |
| Jaringan (`APPLY_NETWORK`), ganti/cabut kunci, unggah dan pasang firmware | ✅, **dengan konfirmasi ulang password** | ❌ |

- Konfirmasi ulang password: sebelum tindakan berisiko dikirim, Owner memasukkan password akunnya lagi. Ini mencegah salah klik dan penyalahgunaan sesi yang ditinggal terbuka.
- Semua perubahan pengaturan, perintah berisiko, unggahan firmware, dan hasilnya dicatat lewat `AuditService` di transaksi yang sama. Audit tidak mencatat password Wi-Fi, kunci, atau isi firmware.
- Semua endpoint baru masuk `config/SecurityConfig` dan `AccessControlTests`.

## 8. Aturan rekap: jeda minimum antar scan (K-092)

**Masalah:** rekap memakai scan pertama sebagai jam masuk dan scan terakhir sebagai jam pulang (`DailyAttendanceCalculator`). Spam scan 08.00.00, 08.00.04, 08.00.08 tanpa scan pulang terbaca sebagai hadir lengkap selama 8 detik, padahal seharusnya Tidak Lengkap. Celah ini juga berlaku untuk X401 saat ini.

**Aturan:**
- Kolom baru di Pengaturan Aturan: **"Jeda minimum antar scan (menit)"**, berlaku per tanggal efektif seperti kolom aturan lainnya. Prioritas per kolom mengikuti K-046.
- Saat menentukan jam masuk dan pulang, scan yang berjarak kurang dari jeda ini dari scan pertama kelompoknya dianggap satu kejadian. Scan asli tetap tersimpan dan tetap terlihat di detail absensi.
- **Kosong = tanpa penggabungan** (perilaku saat ini), sesuai K-047 bahwa tidak ada nilai bawaan. Nilainya diisi klien; contoh yang wajar adalah 5 menit.
- Perubahan schema lewat migration Flyway.

**Di mesin (tampilan saja):** kalau orang yang sama menempelkan jari lagi dalam jeda tampilan (pengaturan ringan, bawaan 1 menit), layar menampilkan *"Sudah tercatat pukul HH.MM"* dan buzzer berbunyi pendek. Scannya **tetap dikirim** ke server. Jeda tampilan ini bukan aturan rekap.

**Test case baru (TC-17), masuk `aturan-bisnis.md`:**

| No | Kondisi | Hasil yang diharapkan |
|---|---|---|
| TC-17 | Jeda minimum 5 menit; scan 08.00.00, 08.00.04, 08.00.08; tidak ada scan lain | Satu kejadian scan, status Tidak Lengkap, ketiga scan asli tetap tersimpan |

Kasus tambahan untuk test otomatis: jeda kosong = perilaku lama; scan tepat di batas jeda; spam saat pulang (jam pulang = scan pertama dari kelompok terakhir); spam tanpa henti lebih lama dari jeda menghasilkan lebih dari satu kejadian; jeda diambil dari versi aturan yang berlaku pada tanggal kejadian.

## 9. Penanganan error

| Kode dari mesin | Pesan di web |
|---|---|
| `JARI_TIDAK_COCOK` | Tiga tempelan tidak cocok satu sama lain. Ulangi dengan jari yang sama. |
| `KUALITAS_BURUK` | Sidik jari tidak terbaca jelas. Bersihkan jari atau sensor, lalu ulangi. |
| `JARI_SUDAH_TERDAFTAR` | Sidik jari ini sudah terdaftar atas nama {nama}. |
| `SENSOR_PENUH` | Kapasitas sensor mesin penuh. |
| `WAKTU_HABIS` / tidak ada respons | Mesin tidak merespons. Periksa koneksi mesin. |
| `SENSOR_ERROR` | Sensor bermasalah. Hubungi teknisi. |
| `WIFI_GAGAL` | Gagal tersambung ke Wi-Fi {nama}. Mesin kembali ke pengaturan lama. |
| `SERVER_GAGAL` | Gagal tersambung ke server baru. Mesin kembali ke pengaturan lama. |
| `FIRMWARE_RUSAK` | File firmware tidak cocok dengan sidik digitalnya. Unggah ulang file firmware. |
| `FIRMWARE_TIDAK_MUAT` | File firmware terlalu besar untuk mesin ini. |
| `FIRMWARE_DIKEMBALIKAN` | Firmware baru gagal berjalan. Mesin kembali ke versi {versi lama}. |

Error dari API web berformat ProblemDetail dengan pesan Bahasa Indonesia.

Aturan tambahan:
- **PIN tidak bisa diubah selama karyawan masih punya sidik jari terdaftar** (K-093). Pesannya: *"Hapus sidik jari dulu sebelum mengganti PIN."*
- Halaman Mesin memberi peringatan kalau kapasitas sensor terpakai di atas 90%.
- Penyalinan yang gagal ke salah satu mesin, dan perintah berisiko yang gagal, ditampilkan sebagai peringatan di dashboard.

## 10. Pengujian

| Lapis | Cakupan |
|---|---|
| Kalkulator rekap | TC-17 dan kasus tambahan di §8 |
| Antrean perintah | Perpindahan status, kedaluwarsa `ENROLL`, laporan hasil ganda, satu `ENROLL` per mesin dan per karyawan + jari, satu perintah berisiko per mesin, larangan perintah berisiko saat `ENROLL` aktif, rekonsiliasi, pembuatan perintah saat karyawan nonaktif dan saat mesin baru didaftarkan |
| Protokol | Autentikasi (kunci benar, salah, mesin nonaktif → 401), ACK scan hanya setelah tersimpan, ID scan ganda tidak tersimpan dua kali, unduh firmware hanya untuk mesin dengan perintah aktif |
| Pengaturan jarak jauh | Paket pengaturan hanya dikirim saat versi mesin lebih lama; `ROTATE_KEY` menerima dua kunci selama masa transisi, lalu hanya kunci baru; kunci baru dibuang setelah 24 jam tanpa dipakai; `APPLY_NETWORK` gagal → status `GAGAL` dan pengaturan lama tercatat tetap berlaku |
| Keamanan | Enkripsi template dan password Wi-Fi bisa dibuka kembali; **template dan password Wi-Fi tidak muncul di response `/api/**`**; HR/Finance ditolak untuk tindakan berisiko; tindakan berisiko ditolak tanpa konfirmasi ulang password; endpoint baru masuk `AccessControlTests`; audit untuk persetujuan dan semua perubahan |
| Simulator | `tools/device-simulator` mode rakitan: heartbeat, enroll (berhasil dan setiap kode gagal), store/delete, rekonsiliasi, antrean offline, ID scan ganda, menerapkan paket pengaturan, `APPLY_NETWORK` (berhasil dan gagal + kembali), `ROTATE_KEY`, `UPDATE_FIRMWARE` (berhasil, file rusak, dikembalikan) |
| Frontend | Dialog pendaftaran mengikuti SSE, tombol terkunci sebelum persetujuan, pesan untuk mesin `ZK_PUSH`, tabel status per mesin, PIN terkunci, form pengaturan per mesin dan untuk semua mesin, dialog konfirmasi ulang password, halaman Firmware dan progres update |
| Hardware (pengguna) | Uji kelayakan R503 dan OTA (§2), dan skenario uji alat rakitan setara X-01–X-08 untuk X401, ditambah: ganti Wi-Fi dari web (benar dan salah password), cabut Wi-Fi 10 menit hingga hotspot cadangan muncul, update firmware, dan firmware sengaja rusak |

## 11. Di luar cakupan

Restart mesin dari web, lebih dari 2 jari per karyawan, pengenalan wajah, rotasi kunci enkripsi data rahasia di server, penandatanganan digital firmware, update firmware terjadwal otomatis, dan penulisan firmware ESP32 itu sendiri (sampai Q-034 dijawab).

## 12. Keputusan baru (masuk `keputusan.md` saat implementasi dimulai)

| No | Keputusan |
|---|---|
| K-087 | Alat rakitan memakai protokol sendiri `/device/v1` sebagai adapter kedua (K-054), bukan meniru ZK-Push. Mesin melakukan polling heartbeat; server tidak pernah menghubungi mesin. Menjawab Q-030. |
| K-088 | Kunci rahasia acak per mesin, hanya ditampilkan sekali, disimpan sebagai hash. Bisa diganti dua tahap (mesin online) atau dicabut langsung (mesin hilang). |
| K-089 | Template sidik jari dan password Wi-Fi mesin disimpan terenkripsi AES-256-GCM (kunci dari environment) dan tidak pernah dikirim ke browser. Template disalin ke semua mesin rakitan aktif. Melengkapi K-024. Penyalinan template bergantung pada hasil uji R503 (Q-035). |
| K-090 | Karyawan nonaktif atau keluar → template dihapus dari semua mesin dan dari server. |
| K-091 | Pendaftaran sidik jari butuh centang persetujuan karyawan, sekali per karyawan, tercatat di audit. Formulir persetujuan kertas diurus klien. |
| K-092 | Kolom aturan "Jeda minimum antar scan (menit)". Kosong = tanpa penggabungan. Jeda dihitung dari scan pertama setiap kelompok. Scan asli tetap tersimpan. |
| K-093 | PIN mesin terkunci selama karyawan masih punya sidik jari terdaftar. |
| K-094 | Maksimal 2 jari per karyawan. Satu `ENROLL` aktif per mesin; batas waktu pengambilan 30 detik dan batas pendaftaran di mesin 60 detik. |
| K-095 | Firmware sederhana, pengaturan dari web. Pengaturan awal sekali per mesin: firmware ditanam lewat USB, lalu Wi-Fi, alamat server, dan kunci diisi lewat hotspot otomatis. Hotspot muncul lagi sendiri setelah 10 menit tanpa Wi-Fi. Tombol BOOT hanya untuk reset pabrik. |
| K-096 | Pengaturan ringan dikirim sebagai paket berversi lewat heartbeat. Perubahan Wi-Fi dan alamat server memakai "coba dulu, baru simpan" dengan kembali otomatis dalam 2 menit. Mesin bisa menyimpan beberapa Wi-Fi berurutan. |
| K-097 | Update firmware lewat web (OTA) dengan partisi ganda, pemeriksaan SHA-256, pemeriksaan diri setelah restart, dan rollback otomatis. Hanya berjalan saat mesin menganggur. Bergantung pada hasil uji OTA (Q-036). |
| K-098 | Pengaturan ringan: Owner dan HR/Finance. Jaringan, kunci, dan firmware: hanya Owner, dengan konfirmasi ulang password. |

## 13. Pertanyaan terbuka baru (masuk `pertanyaan-terbuka.md`)

- **Q-034. Lokasi dan pengerjaan firmware.** Siapa yang menulis firmware ESP32, dan apakah disimpan di repo ini (misalnya `firmware/`) atau repo terpisah? Sementara ini repo ini hanya menyediakan kontrak protokol dan simulator.
- **Q-035. Hasil uji kelayakan R503.** Apakah sensor bisa mengirim dan menerima template, berapa kapasitas varian yang dibeli, apakah pencarian 1:N dan password modul berfungsi? Menentukan apakah penyalinan template (K-089) dijalankan atau diganti dengan pendaftaran ulang per mesin.
- **Q-036. Hasil uji kelayakan OTA.** Berapa ukuran flash ESP32 DevKit yang dipakai, apakah dua partisi firmware muat, dan apakah rollback otomatis berfungsi? Menentukan apakah K-097 dijalankan.
- Q-018 dan Q-019 diperbarui dengan daftar komponen dan keputusan bahwa pendaftaran dipicu dari web.

## 14. Urutan pengerjaan yang diusulkan

Rinciannya dibuat di rencana implementasi (writing-plans). Gambaran urutannya:

1. Jeda minimum antar scan (§8). Mandiri, dan memperbaiki celah X401 sekarang.
2. Kontrak protokol, autentikasi mesin, dan penerimaan scan rakitan, beserta simulator.
3. Antrean perintah, `ENROLL`, dan UI pendaftaran dengan persetujuan.
4. Penyimpanan template, `STORE_TEMPLATE`, rekonsiliasi, dan status per mesin. **Hanya jika uji R503 lulus.**
5. Penghapusan (manual dan saat karyawan nonaktif), kunci PIN, dan peringatan kapasitas.
6. Paket pengaturan ringan, `APPLY_NETWORK`, `ROTATE_KEY`, dan konfirmasi ulang password.
7. Halaman Firmware dan `UPDATE_FIRMWARE`. **Hanya jika uji OTA lulus.**

Uji kelayakan R503 dan OTA (§2) berjalan paralel dengan langkah 1–3.

## 15. Rincian Rencana 2: protokol, autentikasi, dan penerimaan scan (2026-10-17)

Kontrak lengkap ada di [`docs/protokol-alat-rakitan.md`](../../protokol-alat-rakitan.md). Bagian ini mencatat keputusan yang menyertainya.

**Disepakati pengguna:**
- Pengaturan pertama dilakukan pengguna dan tim, pertama kali di kampus. Alurnya 4 langkah: daftarkan mesin di web → nyalakan mesin → isi dari HP lewat hotspot mesin → mesin dan web sama-sama mengonfirmasi.
- **Kode pengaturan** tunggal (alamat server + ID mesin + kunci) yang hanya tampil sekali, supaya di HP cukup ditempel.
- Layar mesin menyebut langkah yang gagal (Wi-Fi, server, kunci), lalu hotspot muncul lagi.
- Uji pertama memakai **hotspot HP**, bukan Wi-Fi kampus.
- Pengguna menyerahkan detail teknis kontrak kepada Claude; detail itu ditandai "keputusan Claude" di bawah dan di kontrak.

**Keputusan Claude (bisa dikoreksi saat review):**
- ID mesin dibuat server (`RK-XXXXXX`) dan disimpan di kolom `serial_number` yang sudah ada. Admin tidak mengetik nomor seri untuk alat rakitan.
- Kunci disimpan sebagai SHA-256, cukup untuk kunci acak 32 byte. Server mencari mesin langsung dari hash kunci.
- **Kode pengaturan disusun di browser** dari kunci (dikirim server sekali) dan alamat server yang diisi admin. Alamat bawaan diambil dari pengaturan `app.device.public-url` jika diisi; alamat tidak disimpan di server.
- **"Buat kode baru"** langsung mencabut kunci lama. Tombol ini hanya untuk Owner, dengan dialog konfirmasi. Konfirmasi ulang password (K-098) dan penggantian dua tahap (`ROTATE_KEY`) dikerjakan di Rencana 6; sampai saat itu, risiko mencabut kunci mesin yang sedang dipakai diterima karena tombol hanya dipakai saat pengaturan.
- Mesin alat rakitan yang nonaktif dibalas **403 `MESIN_NONAKTIF`**, bukan 401 seperti ZK-Push (K-067), supaya layar mesin bisa memberi pesan yang benar. Mesin tak dikenal dan kunci salah tetap 401.
- Schema (migration Flyway baru): `devices.key_hash` (teks 64, unik, boleh kosong untuk X401) dan `attendance_scans.device_scan_id` (UUID, boleh kosong, unik per mesin). Query simpan scan memakai `on conflict do nothing` tanpa target, sehingga kedua aturan unik (ID scan dan mesin + PIN + waktu) sama-sama mencegah duplikat.
- Waktu scan dikirim dengan offset. Adapter mengubahnya ke jam lokal cabang mesin sebelum masuk inti penerimaan scan, sehingga inti tidak berubah. Teks asli kiriman tetap disimpan sebagai baris mentah.
- `accepted` di balasan scan mencakup semua ID yang sudah diproses, termasuk yang ditolak, supaya antrean microSD tidak tersangkut oleh satu scan rusak (sejalan dengan K-062).
- **Pop-up "Mesin … baru saja terhubung"** muncul untuk Owner dan HR/Finance saat mesin pertama kali lolos autentikasi (sebelumnya belum pernah terlihat), lewat SSE yang sudah ada.
- Simulator `tools/device-simulator` ditambah mode rakitan: menerima kode pengaturan, lalu menjalankan heartbeat, scan, scan offline (batch), kirim ulang ID yang sama, dan kunci salah.
- **Hanya Owner yang bisa menambah alat rakitan dan membuat kode pengaturan**, karena keduanya menerbitkan kunci (sejalan dengan K-098). HR/Finance tetap bisa melihat, mengubah nama/cabang, dan menonaktifkan mesin, serta tetap bisa menambah mesin ZK-Push seperti sekarang.
- **Jenis mesin (protokol) tidak bisa diubah setelah mesin dibuat.** Untuk mengganti jenis, buat mesin baru. Nomor seri alat rakitan juga tidak bisa diubah.
- **Endpoint terpisah per jenis mesin:** `/iclock/**` hanya menerima mesin `ZK_PUSH`, dan `/device/v1/**` hanya menerima mesin `RAKITAN_V1`. Tanpa ini, siapa pun yang melihat ID mesin di layar alat rakitan bisa mengirim scan lewat `/iclock` tanpa kunci.
- **Mesin nonaktif tidak memperbarui "terakhir terlihat"** (sama dengan K-067), walaupun kuncinya benar dan dibalas 403.
- **Konfirmasi "baru saja terhubung"** muncul pada kontak pertama yang berhasil **dengan setiap kunci baru**, jadi pengaturan ulang setelah "Buat kode baru" juga mendapat konfirmasi. Untuk itu kolom `devices.key_first_used_at` (kosong saat kunci dibuat) ditambahkan di migration yang sama.
- **Isi heartbeat:** `firmware` dan `ip` disimpan di kolom yang sudah ada (`firmware`, `last_ip`). `pendingScans`, `sensor`, dan `uptimeSeconds` disimpan sebagai teks ringkas di `last_info` dan tampil di detail mesin. Kolom khusus kapasitas sensor dibuat di Rencana 4 saat peringatan kapasitas dibangun.
- Perubahan `on conflict` tanpa target tidak mengubah perilaku X401: `device_scan_id` X401 selalu kosong, dan aturan unik mengabaikan nilai kosong, sehingga X401 tetap hanya memakai kunci mesin + PIN + waktu.
- **Perubahan kontrak API web** `/api/devices`: request tambah mesin mendapat field `protocol` (`ZK_PUSH` bawaan atau `RAKITAN_V1`), `serialNumber` hanya wajib untuk `ZK_PUSH`, dan response tambah mesin alat rakitan membawa `deviceKey` (sekali saja). Endpoint baru `POST /api/devices/{id}/key` (Owner) untuk "Buat kode baru".
- Di luar Rencana 2: perintah ke mesin, sidik jari, pengaturan jarak jauh, update firmware, dan HTTPS produksi (Fase 7).

## 16. Rincian Rencana 3: antrean perintah, `ENROLL`, dan pendaftaran dari web (2026-10-17)

**Konteks:** sensor R503 belum diuji (Q-035). Pengguna menyerahkan semua detail kepada Claude dan menyetujui keputusan di bawah ("approve semua").

**Yang dibangun:** bagian **"Sidik Jari"** di Detail Karyawan: centang persetujuan (K-091), tombol "Daftarkan sidik jari" (pilih jari ke-1/2 dan mesin), dialog status langsung lewat SSE (*Menunggu mesin… → Tempelkan jari 1/3 → 2/3 → 3/3 → Berhasil*) dengan tombol **Batal**, dan status per jari (*Terdaftar di {mesin}, {tanggal}* atau *Belum terdaftar*). Jari yang sudah terdaftar bisa didaftar ulang setelah konfirmasi.

**Keputusan:**
1. **Template belum disimpan di server.** Server hanya mencatat pendaftaran (karyawan, jari, mesin, waktu, perintah asal). Jika hasil `ENROLL` membawa template, server membuangnya tanpa menyimpan atau mencatatnya. Penyimpanan terenkripsi (K-089) dibangun di Rencana 4 jika uji R503 lulus; pendaftaran dari Rencana 3 lalu didaftar ulang.
2. **Tabel antrean perintah umum** `device_commands` dengan ID UUID. Di Rencana 3 hanya jenis `ENROLL`. Payload hanya PIN, jari, dan nama tampilan (tanpa data rahasia). Kolom karyawan dan jari terpisah agar bisa dijaga unique index.
3. **Pengiriman lewat heartbeat.** Perintah yang belum selesai (`MENUNGGU`/`DIKIRIM`) ikut di setiap balasan heartbeat; mesin mengabaikan ID yang sedang atau sudah dikerjakan. Perintah pertama kali ikut di balasan → `DIKIRIM`. Pembatalan dikirim lewat daftar `cancel` di heartbeat; laporan progress untuk perintah yang tidak lagi aktif dibalas 409 `PERINTAH_TIDAK_AKTIF`.
4. **Endpoint mesin:** `POST /device/v1/commands/{id}/progress` (`step` 1–3) dan `POST /device/v1/commands/{id}/result` (`BERHASIL`, atau `GAGAL` + kode §9). Laporan hasil ulang atau untuk perintah yang sudah tidak aktif dibalas 200 tanpa efek. Perintah milik mesin lain atau tidak dikenal: 404 `PERINTAH_TIDAK_DIKENAL`.
5. **Batas waktu (K-094) adalah parameter teknis** di `application.properties`: belum diambil 30 detik → `KEDALUWARSA`; sudah diambil tanpa hasil 75 detik (60 detik di mesin + 15 detik cadangan) → `GAGAL` dengan kode `WAKTU_HABIS`. Pemeriksaan terjadwal setiap 5 detik, dan juga dijalankan sebelum membuat perintah baru.
6. **Satu `ENROLL` aktif per mesin dan per karyawan + jari** dijaga partial unique index di database (aman terhadap klik bersamaan), dengan pesan *"Mesin sedang dipakai untuk pendaftaran."* dan *"Jari ini sedang didaftarkan."*
7. **Syarat:** karyawan aktif, PIN mesin terisi, persetujuan tercatat, mesin `RAKITAN_V1` aktif dan Online. Jika tidak ada alat rakitan Online, web menampilkan bahwa X401 tidak mendukung pendaftaran dari web (§3.7).
8. **API web** (Owner dan HR/Finance, semua tercatat di audit): persetujuan, daftar sidik jari karyawan, mulai pendaftaran, batalkan, dan lihat status perintah. Perubahan status perintah dikirim lewat SSE yang sudah ada (event `DEVICE_COMMAND`). `JARI_SUDAH_TERDAFTAR` membawa PIN pemilik; server menerjemahkannya ke nama karyawan.
9. **Di luar Rencana 3:** kunci PIN (K-093) dan hapus sidik jari (Rencana 5), penyalinan ke mesin lain (Rencana 4), dan jenis perintah lain.
10. Simulator `rakitan.mjs live` menjalankan `ENROLL` (berhasil atau gagal dengan kode pilihan). Kontrak `protokol-alat-rakitan.md` diperbarui; K-091 dan K-094 masuk `keputusan.md`; Q-034–Q-036 masuk `pertanyaan-terbuka.md`.
