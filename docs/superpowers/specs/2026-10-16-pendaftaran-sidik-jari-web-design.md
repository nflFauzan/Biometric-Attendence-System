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
| `WAKTU_HABIS` (mesin: tidak ada jari) | Tidak ada jari yang ditempelkan. Ulangi pendaftaran. |
| `TIDAK_ADA_HASIL` (server: tidak ada respons, K-101) | Mesin tidak merespons. Periksa koneksi mesin. |
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
| K-090 | Karyawan nonaktif atau keluar → template dihapus dari semua mesin dan dari server. Rincian: K-102 (§17). |
| K-091 | Pendaftaran sidik jari butuh centang persetujuan karyawan, sekali per karyawan, tercatat di audit. Formulir persetujuan kertas diurus klien. |
| K-092 | Kolom aturan "Jeda minimum antar scan (menit)". Kosong = tanpa penggabungan. Jeda dihitung dari scan pertama setiap kelompok. Scan asli tetap tersimpan. |
| K-093 | PIN mesin terkunci selama karyawan masih punya sidik jari terdaftar, dan sampai penghapusan di semua mesin aktif selesai. Rincian: K-102 (§17). |
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
5. **Batas waktu (K-094) adalah parameter teknis** di `application.properties`: belum diambil 30 detik → `KEDALUWARSA`; sudah diambil tanpa hasil 75 detik (60 detik di mesin + 15 detik cadangan) → `GAGAL` dengan kode `TIDAK_ADA_HASIL` (K-101; awalnya `WAKTU_HABIS`). Pemeriksaan terjadwal setiap 5 detik, dan juga dijalankan sebelum membuat perintah baru.
6. **Satu `ENROLL` aktif per mesin dan per karyawan + jari** dijaga partial unique index di database (aman terhadap klik bersamaan), dengan pesan *"Mesin sedang dipakai untuk pendaftaran."* dan *"Jari ini sedang didaftarkan."*
7. **Syarat:** karyawan aktif, PIN mesin terisi, persetujuan tercatat, mesin `RAKITAN_V1` aktif dan Online. Jika tidak ada alat rakitan Online, web menampilkan bahwa X401 tidak mendukung pendaftaran dari web (§3.7).
8. **API web** (Owner dan HR/Finance, semua tercatat di audit): persetujuan, daftar sidik jari karyawan, mulai pendaftaran, batalkan, dan lihat status perintah. Perubahan status perintah dikirim lewat SSE yang sudah ada (event `DEVICE_COMMAND`). `JARI_SUDAH_TERDAFTAR` membawa PIN pemilik; server menerjemahkannya ke nama karyawan.
9. **Di luar Rencana 3:** kunci PIN (K-093) dan hapus sidik jari (Rencana 5), penyalinan ke mesin lain (Rencana 4), dan jenis perintah lain.
10. Simulator `rakitan.mjs live` menjalankan `ENROLL` (berhasil atau gagal dengan kode pilihan). Kontrak `protokol-alat-rakitan.md` diperbarui; K-091 dan K-094 masuk `keputusan.md`; Q-034–Q-036 masuk `pertanyaan-terbuka.md`.

## 17. Rincian Rencana 5: hapus sidik jari, kunci PIN, dan peringatan kapasitas (2026-10-18)

**Konteks:**
- Template belum disimpan di server (§16), dan penyalinan antar mesin (Rencana 4) masih menunggu uji R503.
- Karena itu, sebuah sidik jari hanya ada di sensor mesin tempat jari itu didaftarkan. Pendaftaran ulang di mesin lain juga bisa meninggalkan sisa jari di mesin lama.
- Rekonsiliasi (§4.4) belum dibangun.

**Keputusan pengguna (2026-10-18):**
- **A. Kunci PIN sampai penghapusan selesai.** PIN tetap terkunci sampai semua mesin melaporkan penghapusan selesai. "Terkunci" berarti PIN tidak bisa diganti atau dikosongkan, dan tidak bisa diberikan ke karyawan lain.
- **B. Mesin nonaktif membuka kunci.** Menonaktifkan mesin membuka kunci PIN: perintah hapus untuk mesin nonaktif tidak ikut dihitung.

**Yang dibangun:**
1. **Jenis perintah baru.** `DELETE_TEMPLATE` berisi PIN dan jari; `DELETE_EMPLOYEE` berisi PIN. Isinya disimpan di kolom `employee_id`, `device_pin`, dan `finger` yang sudah ada; migration baru mengganti `CHECK` jenis perintah. Satu permintaan menghasilkan satu baris per mesin, tanpa penggabungan; perintah ganda tetap aman karena hapus bersifat idempoten. Keduanya:
   - tidak kedaluwarsa dan tidak bisa dibatalkan;
   - dikirim ulang lewat heartbeat sampai ada hasil;
   - tidak punya laporan langkah. Laporan langkah untuk perintah hapus dibalas 409 `PERINTAH_TIDAK_AKTIF`, sama dengan perintah yang tidak aktif.
   - **Pemeriksaan batas waktu (`expireStale`) hanya berlaku untuk `ENROLL`.**
2. **Tujuan perintah.** Perintah dikirim ke **semua** mesin `RAKITAN_V1` yang terdaftar saat penghapusan dibuat, aktif maupun nonaktif. Alasannya, kita tidak tahu pasti mesin mana yang masih menyimpan jari itu. Menghapus jari yang tidak ada tetap dilaporkan `BERHASIL` oleh mesin (§4.2).
   - Mesin nonaktif menerima perintahnya jika suatu saat diaktifkan lagi.
   - Mesin yang **belum pernah tersambung** (belum pernah heartbeat) dilewati, karena sensornya pasti kosong: pendaftaran butuh mesin Online. *(Keputusan Claude.)*
   - Mesin yang didaftarkan kemudian tidak menerima apa pun, karena sensornya kosong.
   - Mesin X401 tidak menerima perintah (§3.7).
3. **Hapus satu jari.** Tombol **"Hapus"** ada di setiap jari yang terdaftar, dengan dialog konfirmasi *"Hapus sidik jari jari ke-{n} {nama} dari semua mesin?"*. Dalam satu transaksi, server:
   - menghapus baris `employee_fingerprints` (audit `FINGERPRINT_DELETED` dengan isi sebelumnya);
   - membuat `DELETE_TEMPLATE` per mesin (audit `DELETE_REQUEST`).

   Penghapusan ditolak dengan *"Jari ini sedang didaftarkan."* kalau ada `ENROLL` aktif untuk karyawan + jari itu.
4. **Karyawan nonaktif atau keluar (K-090).**
   - **Kapan dihapus** (keputusan pengguna 2026-10-18): sidik jari dihapus **saat karyawan dinonaktifkan** atau **saat tanggal keluarnya tiba**, bukan saat tanggal keluar diisi.
   - **Arti tanggal keluar** *(tafsiran Claude)*: tanggal keluar adalah hari kerja terakhir. Penghapusan berjalan setelah tanggal itu lewat, yaitu mulai 00.00 keesokan harinya menurut zona waktu cabang karyawan. Dengan begitu karyawan masih bisa absen di hari terakhirnya.
   - **Pemicunya:**
     - `active` berubah menjadi `false`: langsung, di transaksi yang sama dengan perubahan karyawan.
     - Tanggal keluar yang diisi sudah lewat: langsung, di transaksi yang sama.
     - Tanggal keluar di masa depan: pemeriksaan terjadwal setiap jam menangkapnya setelah tanggal itu lewat. Interval pemeriksaan adalah parameter teknis.
   - **Penanda:** kolom baru `employees.fingerprints_removed_at` diisi saat penghapusan dijalankan, sehingga pemeriksaan terjadwal tidak mengulanginya. Penanda dikosongkan saat karyawan aktif kembali dengan tanggal keluar kosong atau di masa depan.
   - **Tanggal keluar diubah atau dikosongkan sebelum tiba:** tidak ada yang dihapus.
   - **Isi penghapusan:**
     - membatalkan `ENROLL` aktif milik karyawan itu (audit `CANCEL_COMMAND`; pelakunya pengguna yang mengubah karyawan, atau `sistem` untuk pemeriksaan terjadwal);
     - menghapus semua baris `employee_fingerprints` miliknya (audit `FINGERPRINT_DELETED`);
     - membuat satu `DELETE_EMPLOYEE` per mesin, jika karyawan punya PIN **dan** persetujuan sidik jari pernah tercatat. Hanya karyawan dengan persetujuan yang mungkin pernah didaftarkan.
   - **Pendaftaran baru:** syarat karyawan di §16 butir 7 dilonggarkan. `ENROLL` boleh untuk karyawan aktif yang tanggal keluarnya kosong **atau belum lewat**. *(Keputusan Claude, agar konsisten dengan tafsiran di atas.)*
   - **Mengaktifkan kembali karyawan** tidak memulihkan apa pun; sidik jari didaftar ulang.
5. **Kunci PIN (K-093, diperluas).** Sebuah PIN dianggap *menunggu penghapusan* jika ada perintah `DELETE_*` dengan PIN itu, di mesin yang **aktif**, berstatus `MENUNGGU`, `DIKIRIM`, atau `GAGAL`. Aturannya:
   - Mengganti atau mengosongkan PIN ditolak selama karyawan punya `ENROLL` yang masih `MENUNGGU` atau `DIKIRIM`, karena perintah itu membawa PIN lama ke mesin. Pesannya *"Tunggu sampai pendaftaran sidik jari selesai sebelum mengganti PIN."* (tambahan dari review akhir Rencana 5, 2026-10-20)
   - Mengganti atau mengosongkan PIN ditolak jika karyawan masih punya baris `employee_fingerprints`. Pesannya *"Hapus sidik jari dulu sebelum mengganti PIN."*
   - Mengganti atau mengosongkan PIN juga ditolak jika PIN lamanya sedang menunggu penghapusan. Pesannya *"Tunggu sampai sidik jari selesai dihapus di semua mesin."*
   - Membuat karyawan baru atau mengisi PIN karyawan lain dengan PIN yang sedang menunggu penghapusan ditolak. Pesannya *"PIN ini masih menunggu penghapusan sidik jari di mesin."*
   - Aturan ketiga adalah penjagaan cadangan. Dalam praktik, PIN hanya bisa berpindah pemilik setelah pemilik lamanya melepas PIN, dan itu sudah ditahan oleh dua aturan pertama (PIN unik tetap berlaku seperti sekarang).
   - Kunci terbuka sendiri saat mesin terakhir melapor `BERHASIL`, atau saat mesin yang belum selesai dinonaktifkan (keputusan B).
6. **ENROLL setelah hapus.** `ENROLL` ke sebuah mesin ditolak dengan *"Tunggu sampai sidik jari lama selesai dihapus di mesin ini."* selama mesin itu masih punya perintah `DELETE_*` untuk PIN yang sama yang belum `BERHASIL`. Ini mencegah jari baru ikut terhapus.
7. **Gagal dan "Coba lagi".** Mesin bisa melapor `GAGAL` dengan kode error. `SENSOR_ERROR` memakai pesan yang sudah ada; kode lain tampil sebagai *"Penghapusan gagal ({kode})."* Tombol **"Coba lagi"** mengembalikan perintah yang sama ke `MENUNGGU` dan mengosongkan error-nya (audit `COMMAND_RETRY`). Hanya perintah `DELETE_*` berstatus `GAGAL` yang bisa dicoba lagi.
8. **Tampilan di bagian Sidik Jari.** Daftar **"Penghapusan di mesin"** menampilkan perintah `DELETE_*` milik karyawan yang belum `BERHASIL`, per mesin. Statusnya:
   - *Menunggu mesin…*;
   - *Menunggu (mesin offline)*;
   - *Menunggu (mesin nonaktif)*;
   - *Gagal: {pesan}*, dengan tombol "Coba lagi".

   Daftar ini diperbarui lewat SSE `DEVICE_COMMAND` yang sudah ada. Jika ada mesin X401 aktif, tampilkan juga keterangan *"Hapus juga langsung di mesin X401 jika karyawan pernah didaftarkan di sana."*
9. **Kapasitas sensor.** Server menyimpan nilai `sensor.used` dan `sensor.capacity` terakhir dari heartbeat (yang sudah ada di protokol) ke kolom baru di `devices`. Penulisan memakai update terarah, sama seperti "terakhir terlihat", agar tidak bentrok dengan edit mesin oleh Owner.
   - Halaman Mesin menampilkan *"Sensor 12/150"*.
   - Jika terpakai lebih dari 90%, muncul peringatan *"Kapasitas sensor hampir penuh."* Ambang 90% adalah parameter teknis di `application.properties` (`app.device.sensor-warning-percent`), bukan aturan bisnis.
10. **API web** (Owner dan HR/Finance, masuk `AccessControlTests`):
    - `DELETE /api/employees/{id}/fingerprints/{finger}`;
    - `POST /api/device-commands/{id}/retry`;
    - ringkasan sidik jari karyawan ditambah daftar penghapusan;
    - DTO mesin ditambah data sensor dan tanda hampir penuh.
11. **Protokol dan simulator.** `protokol-alat-rakitan.md` menambah format kedua perintah di balasan heartbeat (`{id, type, pin, finger?}`), aturan "hapus yang tidak ada = `BERHASIL`", dan urutan pengerjaan sesuai urutan daftar. `rakitan.mjs live` menjalankan penghapusan (berhasil, atau gagal dengan kode pilihan) dan melaporkan `sensor` di heartbeat.
12. **Di luar Rencana 5:** rekonsiliasi dan penyalinan (Rencana 4), serta penghapusan template di server (belum ada template).

## 18. Rincian Rencana 6: pengaturan ringan, `APPLY_NETWORK`, `ROTATE_KEY`, dan konfirmasi ulang password (2026-10-20)

Pengguna meminta langsung dikerjakan tanpa tanya-jawab ("langsung eksekusi saja"). Karena itu semua rincian di bawah adalah **keputusan Claude** yang mengikuti §4, §6, dan §7. Pengguna bisa mengubahnya saat membaca.

1. **Pengaturan ringan per mesin (§7.1, §7.2).**
   - Tabel baru `device_settings`, satu baris per alat rakitan. Baris dibuat saat pengaturan pertama kali disimpan dari web.
   - Selama baris belum ada, server melaporkan versi 0 dengan nilai bawaan dan **tidak mengirim apa pun**; mesin memakai bawaan firmware. Nilai bawaan adalah parameter teknis di `application.properties` (`app.device.settings.default.*`) dan wajib sama dengan bawaan firmware.
   - Isinya:

     | Isian | Batas | Bawaan |
     |---|---|---|
     | `headerText` (judul layar) | ≤ 32 karakter, boleh kosong (kosong = nama cabang) | kosong |
     | `greeting` (pesan sapaan) | ≤ 64 karakter | "Silakan tempelkan jari" |
     | `nameDisplay` | `FULL`, `FIRST`, `PIN` | `FULL` |
     | `brightness` | 10–100 (%) | 80 |
     | `buzzerVolume` | 0–100 (%), 0 = buzzer mati | 50 |
     | `duplicateNoticeSeconds` (jeda "Sudah tercatat") | 0–600, 0 = mati | 60 |
     | `heartbeatSeconds` | 2–20 | 5 |

   - **Batas waktu pendaftaran tidak dimasukkan ke paket.** Nilai itu sudah dikirim di setiap perintah `ENROLL` (`timeoutSeconds`, K-094), dan batas waktu server bergantung padanya. Dua sumber untuk satu nilai bisa saling bertentangan.
   - **Interval heartbeat dibatasi paling lama 20 detik**, karena `ENROLL` kedaluwarsa jika tidak diambil dalam 30 detik.
   - **Zona waktu tidak masuk paket**, karena sudah dikirim di setiap heartbeat (`timezone`) dan otomatis mengikuti perubahan cabang.
   - Batas panjang teks bergantung pada ukuran layar yang belum diketahui (**Q-037**).
   - Setiap simpan menaikkan `version` per mesin, termasuk saat disimpan untuk semua mesin sekaligus (audit `UPDATE_SETTINGS` per mesin). Simpan tanpa perubahan nilai tidak menaikkan versi.
2. **Protokol pengaturan ringan.**
   - Heartbeat membawa `settingsVersion` (versi yang sedang dipakai mesin, 0 jika belum pernah menerima).
   - Jika versi server lebih baru, balasan berisi `settings` lengkap beserta `version`-nya; jika tidak, `settings` bernilai `null`.
   - Server menyimpan versi yang dilaporkan mesin (`devices.settings_applied_version`) supaya halaman Mesin bisa menampilkan *"Pengaturan terbaru sudah diterapkan ✓"* atau *"Menunggu mesin menerapkan versi N"*.
3. **Konfirmasi ulang password (§7.5, K-098).**
   - Request tindakan berisiko membawa `confirmPassword`, yang dicocokkan dengan password akun yang sedang login.
   - Jika salah, balasannya 400 *"Password salah."* Bukan 401, supaya sesi tidak dianggap habis.
   - Berlaku untuk `APPLY_NETWORK`, "Ganti kunci" (`ROTATE_KEY`), dan **"Buat kode baru"** (cabut kunci, §15) yang sudah ada. Kontrak `POST /api/devices/{id}/key` berubah: sekarang wajib membawa body.
   - Batal perintah berisiko tidak perlu password.
4. **Perintah berisiko di antrean (§4.2).**
   - Jenis baru: `APPLY_NETWORK` dan `ROTATE_KEY`. Unique index menjaga satu perintah berisiko aktif (`MENUNGGU`/`DIKIRIM`) per mesin. Permintaan kedua ditolak dengan *"Mesin masih menerapkan perubahan jaringan atau kunci."*
   - Perintah berisiko tidak dikirim di heartbeat selama mesin punya `ENROLL` aktif.
   - `ENROLL` ditolak selama ada perintah berisiko aktif, dengan pesan yang sama seperti di atas.
   - Perintah berisiko tidak kedaluwarsa selama `MENUNGGU`, jadi mesin offline tetap menerimanya saat kembali online. Hanya Owner yang bisa membatalkannya, dan hanya selama `MENUNGGU`. Setelah `DIKIRIM`, batal ditolak dengan *"Perintah sudah dikirim ke mesin dan tidak bisa dibatalkan."*
5. **`APPLY_NETWORK` (§7.3).**
   - **Isi.** Daftar Wi-Fi berurutan (1–5 jaringan) dan/atau alamat server; minimal salah satunya diisi.
     - SSID 1–32 karakter, tidak boleh ganda.
     - Password kosong (Wi-Fi terbuka) atau 8–63 karakter (WPA2).
     - Alamat server diawali `http://` atau `https://`.
     - Daftar Wi-Fi kosong berarti Wi-Fi tidak diubah; alamat server kosong berarti server tidak diubah.
   - **Penyimpanan.** Setiap permintaan disimpan sebagai *paket jaringan* (`device_network_configs` + `device_network_wifi`). Password Wi-Fi disimpan terenkripsi AES-256-GCM (K-089). Perintah hanya menyimpan rujukan ke paket itu.
   - **Paket yang berlaku.** Saat mesin melapor `BERHASIL`, paket menjadi "jaringan yang berlaku" mesin itu (`devices.network_config_id`). Halaman Mesin menampilkan nama-nama Wi-Fi dan alamat server yang berlaku, atau *"Belum pernah diatur dari web"*.
   - **Password tulis saja.** Saat mengubah, password yang dikosongkan untuk SSID yang sudah ada di jaringan yang berlaku berarti *pakai password tersimpan*. Untuk SSID baru, password kosong berarti Wi-Fi terbuka. Form menjelaskan kedua hal ini.
   - **Heartbeat.** Balasan heartbeat menyisipkan `network: {wifi: [{ssid, password}], serverUrl}`, sudah didekripsi, sehingga password hanya lewat HTTPS ke mesin. `wifi`/`serverUrl` bernilai `null` jika tidak diubah.
   - **Hasil.** Mesin melapor `BERHASIL` setelah heartbeat dengan pengaturan baru berhasil. Jika gagal, mesin kembali ke pengaturan lama lalu melapor `GAGAL` dengan `error` dan `detail` opsional, misalnya `WIFI_GAGAL` + SSID atau `SERVER_TIDAK_TERJANGKAU`.
   - **Tanpa hasil.** Perintah `DIKIRIM` tanpa hasil dalam 10 menit (`app.device.network.result-timeout`) dianggap `GAGAL` dengan `TIDAK_ADA_HASIL`.
6. **`ROTATE_KEY` (§7.3).**
   - **Kunci baru.** Server membuat kunci baru dan menyimpan hash-nya di `devices.pending_key_hash`, serta kunci itu sendiri terenkripsi di `devices.pending_key_enc`, karena heartbeat perlu mengirim ulang kunci sampai dipakai. Balasan heartbeat menyisipkan `key`.
   - **Masa transisi.** Selama itu kunci lama dan kunci baru sama-sama diterima.
   - **Selesai.** Kontak pertama dengan kunci baru memindahkan kunci baru menjadi kunci utama secara atomik, mengosongkan kunci tertunda, dan menyelesaikan perintah sebagai `BERHASIL` (pelaku `mesin <ID>`). Mesin tidak perlu mengirim hasil; hasil yang tetap dikirim dibalas 200 tanpa efek. Kontak ini tidak memunculkan pemberitahuan "mesin terhubung".
   - **Gagal.** Mesin yang tidak bisa menyimpan kunci melapor `GAGAL` dengan kunci lama, dan kunci tertunda dibuang.
   - **24 jam.** Jika kunci baru tidak dipakai dalam 24 jam sejak perintah dibuat (`app.device.rotate-key.timeout`), perintah `GAGAL` dengan `KUNCI_BARU_TIDAK_DIPAKAI`, kunci tertunda dibuang, dan kunci lama tetap berlaku. Membatalkan `ROTATE_KEY` yang masih `MENUNGGU` juga membuang kunci tertunda.
   - **"Buat kode baru" (cabut kunci).** Membuang kunci tertunda dan membatalkan `ROTATE_KEY` yang aktif.
7. **Kunci enkripsi server (K-089).**
   - Dibaca dari environment `DATA_ENCRYPTION_KEY`: 32 byte dalam base64. Kunci tidak disimpan di database dan tidak di-commit.
   - Tanpa kunci ini, `APPLY_NETWORK` dan "Ganti kunci" ditolak dengan *"Kunci enkripsi server belum diatur. Hubungi pengelola server."* Fitur lain tetap berjalan.
   - Format sandi: `v1:` + base64(IV 12 byte ‖ ciphertext + tag), supaya kunci bisa diganti di versi berikutnya.
   - `.env.example` dan `docker-compose.yml` menambah variabelnya.
8. **API web** (semua masuk `SecurityConfig` dan `AccessControlTests`):
   - `GET`/`PUT /api/devices/{id}/settings`: Owner dan HR/Finance.
   - `PUT /api/device-settings`: Owner dan HR/Finance; berlaku untuk semua alat rakitan aktif.
   - `GET /api/devices/{id}/network`: Owner. Berisi jaringan yang berlaku (tanpa password) dan perintah jaringan/kunci terakhir.
   - `POST /api/devices/{id}/network`: Owner, dengan `confirmPassword`.
   - `POST /api/devices/{id}/rotate-key`: Owner, dengan `confirmPassword`.
   - `POST /api/devices/{id}/key`: tetap Owner, sekarang dengan `confirmPassword`.
   - DTO mesin ditambah `settingsVersion`, `settingsAppliedVersion`, dan ringkasan perintah berisiko terakhir.
9. **Audit.**
   - Aksi baru: `UPDATE_SETTINGS`, `NETWORK_REQUEST`, `ROTATE_KEY_REQUEST`, `KEY_ROTATED`.
   - Aksi yang sudah ada tetap dipakai: `COMMAND_SENT`, `COMMAND_RESULT`, `COMMAND_EXPIRED`, `CANCEL_COMMAND`.
   - Audit tidak mencatat password Wi-Fi, kunci, atau hash kunci; snapshot jaringan hanya berisi SSID dan alamat server.
10. **Frontend.**
    - Tombol **"Pengaturan"** di setiap baris alat rakitan membuka dialog dengan bagian **Tampilan & suara** (pengaturan ringan, Owner dan HR/Finance), **Jaringan**, dan **Kunci**. Dua bagian terakhir hanya untuk Owner.
    - Tombol **"Pengaturan semua mesin"** di toolbar menyimpan pengaturan ringan ke semua alat rakitan aktif.
    - Tindakan berisiko membuka dialog konfirmasi password.
    - Status perintah berisiko diperbarui lewat SSE `DEVICE_COMMAND` yang sudah ada.
11. **Simulator.**
    - `rakitan.mjs live` menyimpan versi pengaturan ringan dan melaporkannya di heartbeat.
    - Opsi `--network-result BERHASIL|<kode>` mengerjakan `APPLY_NETWORK` dan melapor sesuai opsi.
    - Untuk `ROTATE_KEY`, simulator memakai kunci baru di heartbeat berikutnya; dengan `--rotate-result <kode>` ia melapor gagal.
12. **Di luar Rencana 6:** update firmware (Rencana 7), dan pindah server ke instance lain (cukup didokumentasikan seperti §7.3).
