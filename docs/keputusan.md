# Catatan Keputusan Proyek

## 1. Tujuan

Dokumen ini menjadi catatan resmi keputusan bisnis, arsitektur, teknologi, keamanan, dan implementasi untuk **Biometric Attendance & HR Management System**.

Keputusan yang telah disepakati tidak boleh dihapus. Jika sebuah keputusan berubah, buat entri baru yang menjelaskan perubahan tersebut dan merujuk ke keputusan sebelumnya.

### Status Keputusan

- **Disepakati:** sudah ditetapkan dan menjadi acuan implementasi.
- **Usulan:** direkomendasikan, tetapi belum menjadi keputusan final.
- **Menunggu Konfirmasi:** membutuhkan persetujuan atau informasi tambahan dari klien.
- **Digantikan:** tidak lagi menjadi keputusan aktif karena telah digantikan oleh keputusan baru.

Tanggal menggunakan format `YYYY-MM-DD`.

---

## 2. Daftar Keputusan

| ID | Tanggal | Keputusan | Status |
|---|---|---|---|
| K-001 | 2026-10-09 | Stack Spring Boot, React, PostgreSQL, modular monolith | Disepakati |
| K-002 | 2026-10-09 | Tiga role awal: Owner, HR/Finance, Karyawan | Disepakati |
| K-003 | 2026-10-09 | Perhitungan lembur berdasarkan waktu kerja setelah jam pulang normal, tanpa pengajuan awal sebagai prasyarat | Disepakati, diperjelas K-026 |
| K-004 | 2026-10-09 | Aturan kerja dan gaji dapat diatur Owner dengan tanggal berlaku | Disepakati |
| K-005 | 2026-10-09 | Sistem mengusulkan upah harian; pihak berwenang menentukan kelayakannya | Disepakati |
| K-006 | 2026-10-09 | Sistem digunakan untuk satu perusahaan, tanpa `tenant_id` pada versi awal | Disepakati |
| K-007 | 2026-10-09 | Integritas scan dan penyimpanan hasil harus dijaga | Disepakati, diperjelas K-019, K-020 |
| K-008 | 2026-10-09 | Pengembangan menggunakan simulator sampai prototipe mesin siap | Disepakati |
| K-009 | 2026-10-09 | Eksperimen backend NestJS lama diarsipkan sebelum dihapus | Disepakati |
| K-010 | 2026-10-09 | Maven sebagai build tool backend; Docker Compose untuk lingkungan lokal | Disepakati |
| K-011 | 2026-10-09 | Tim bekerja langsung pada branch `main` | Disepakati |
| K-012 | 2026-10-09 | Login menggunakan username yang tidak membedakan huruf besar/kecil | Disepakati |
| K-013 | 2026-10-09 | UI menggunakan Tailwind CSS dan shadcn/ui | Disepakati |
| K-014 | 2026-10-09 | Autentikasi menggunakan session cookie, Spring Session JDBC, CSRF, dan sesi 8 jam | Disepakati |
| K-015 | 2026-10-09 | Akun Owner awal dibuat melalui konfigurasi environment; akun baru wajib mengganti password | Disepakati |
| K-016 | 2026-10-09 | Pengelolaan akun dibatasi menurut role dan minimal satu Owner aktif dipertahankan | Disepakati |
| K-017 | 2026-10-10 | Design system mengikuti brand Takka Steel | Disepakati |
| K-018 | 2026-10-11 | Backend menjadi gateway antara perangkat, database, dan frontend | Disepakati |
| K-019 | 2026-10-11 | Raw scan tidak ditimpa oleh koreksi absensi | Disepakati |
| K-020 | 2026-10-11 | ACK sukses hanya diberikan setelah data scan tersimpan secara persisten | Disepakati |
| K-021 | 2026-10-11 | Offline queue, retry, dan RTC bergantung pada kemampuan perangkat | Menunggu Konfirmasi |
| K-022 | 2026-10-11 | Nilai uang menggunakan tipe data desimal dengan presisi yang memadai | Disepakati |
| K-023 | 2026-10-11 | Backend wajib memvalidasi hak akses setiap tindakan yang dilindungi | Disepakati |
| K-024 | 2026-10-11 | Data payroll, kasbon, akun, dan biometrik memerlukan pembatasan akses | Disepakati |
| K-025 | 2026-10-11 | Custom role dan pembatasan akses per cabang belum diputuskan | Menunggu Konfirmasi |
| K-026 | 2026-10-11 | Kandidat lembur hari kerja normal dimulai setelah pukul 17.00 | Disepakati |
| K-027 | 2026-10-11 | Lembur dihitung dari scan dan dapat diverifikasi atau dikoreksi secara terkontrol | Disepakati |
| K-028 | 2026-10-11 | Aturan kerja dapat berlaku global maupun khusus karyawan | Disepakati |
| K-029 | 2026-10-11 | Koreksi absensi disimpan terpisah dan diaudit | Disepakati |
| K-030 | 2026-10-11 | Detail kalender kerja, istirahat, pembulatan, dan scan tidak lengkap harus divalidasi | Menunggu Konfirmasi |
| K-031 | 2026-10-11 | Payroll mingguan untuk supir/kenek dan bulanan untuk staf lainnya | Disepakati |
| K-032 | 2026-10-11 | Gaji pokok bulanan dipisahkan dari komponen upah harian | Disepakati |
| K-033 | 2026-10-11 | Tarif memiliki tanggal efektif agar riwayat dapat dipertahankan | Disepakati |
| K-034 | 2026-10-11 | Payroll final dapat dibuka kembali melalui prosedur terkontrol | Disepakati |
| K-035 | 2026-10-11 | Payroll final menyimpan snapshot aturan, tarif, input, dan hasil | Disepakati |
| K-036 | 2026-10-11 | Reopen dan finalisasi ulang mempertahankan riwayat versi sebelumnya | Disepakati |
| K-037 | 2026-10-11 | Kasbon menggunakan ledger transaksi, bukan saldo yang diedit langsung | Disepakati |
| K-038 | 2026-10-11 | Bonus ditetapkan Owner; tidak ada rumus bonus otomatis pada versi pertama | Disepakati |
| K-039 | 2026-10-11 | BPJS, PPh 21, dan potongan lain diterapkan setelah kebijakan dikonfirmasi | Disepakati |
| K-040 | 2026-10-11 | Hasil payroll harus dapat ditelusuri kembali ke data sumber dan aturan | Disepakati |
| K-041 | 2026-10-11 | Kewenangan dan persetujuan pembukaan kembali payroll perlu diperinci | Menunggu Konfirmasi |
| K-042 | 2026-10-11 | Empat laporan payroll aktual menjadi baseline pengujian | Disepakati |
| K-043 | 2026-10-11 | Pengujian regresi wajib mencakup tarif historis dan payroll yang dibuka kembali | Disepakati |

---

## 3. Detail Keputusan Teknis

### K-001 — Stack Teknologi

**Keputusan:** sistem menggunakan Java LTS dengan Spring Boot untuk backend, React dengan Vite dan TypeScript untuk frontend, serta PostgreSQL sebagai database. Arsitektur aplikasi menggunakan modular monolith.

**Alasan:**
- Stack sesuai kemampuan tim.
- Backend dan frontend dapat dikembangkan secara terstruktur.
- Modular monolith menjaga pemisahan modul tanpa kompleksitas microservices sejak awal.
- PostgreSQL sesuai kebutuhan transaksi absensi, konfigurasi, dan payroll.

**Alternatif yang tidak dipilih:** NestJS sebagai backend utama pada implementasi baru.

### K-002 — Role Awal

**Keputusan:** tiga role awal adalah Owner, HR/Finance, dan Karyawan.

Hak akses harus diperiksa pada backend. Role tidak boleh hanya digunakan untuk menyembunyikan menu pada frontend.

Custom role dan pembatasan akses per cabang belum menjadi keputusan final.

### K-004 — Konfigurasi Aturan

**Keputusan:** Owner dapat mengatur aturan kerja dan komponen gaji dengan tanggal mulai berlaku.

Aturan harus mendukung konfigurasi global serta pengecualian khusus karyawan. Prioritas aturan harus didefinisikan secara konsisten.

### K-006 — Satu Perusahaan

**Keputusan:** versi awal digunakan untuk satu perusahaan dan tidak memerlukan `tenant_id`.

Jika kebutuhan berubah menjadi produk multi-perusahaan, arsitektur perlu dievaluasi kembali sebelum perubahan dilakukan.

### K-008 — Simulator Perangkat

**Keputusan:** backend dan frontend dikembangkan menggunakan simulator sampai prototipe mesin tersedia.

Simulator harus mengikuti kontrak integrasi yang dirancang dan tidak boleh dianggap sebagai bukti bahwa seluruh kemampuan hardware telah tersedia.

### K-009 — Pengarsipan Backend Lama

**Keputusan:** eksperimen backend NestJS lama diarsipkan menggunakan Git tag pada commit `26bed0a` sebelum folder lama dihapus.

Implementasi baru mengikuti stack yang telah disepakati.

### K-010 — Build dan Lingkungan Lokal

**Keputusan:** backend menggunakan Maven dan lingkungan lokal disediakan melalui Docker Compose.

Komponen yang direncanakan mencakup backend, frontend, dan PostgreSQL. Konfigurasi produksi, reverse proxy, dan TLS harus ditetapkan pada tahap deployment.

### K-011 — Strategi Branch

**Keputusan tim:** pengerjaan dilakukan langsung pada branch `main`, tanpa branch fitur.

Konsekuensi: anggota tim wajib menjaga working tree, menguji perubahan, melakukan commit dengan jelas, dan berkoordinasi sebelum perubahan besar. Jika risiko konflik meningkat, strategi branch perlu dievaluasi ulang melalui keputusan baru.

### K-012 — Login

**Keputusan:** login menggunakan username, bukan email atau nomor telepon. Username tidak membedakan huruf besar/kecil.

Normalisasi username harus diterapkan konsisten pada pembuatan akun, login, dan validasi keunikan.

### K-013 — UI Framework

**Keputusan:** frontend menggunakan Tailwind CSS dan shadcn/ui.

Komponen antarmuka harus mengikuti `design-system.md`, menjaga keterbacaan tabel operasional, dan mendukung layout responsif.

### K-014 — Sesi Autentikasi

**Keputusan:** autentikasi menggunakan session cookie dengan Spring Session JDBC, proteksi CSRF, dan durasi sesi 8 jam.

JWT tidak digunakan sebagai mekanisme autentikasi utama pada baseline ini karena pengelolaan pencabutan sesi lebih sederhana dengan pendekatan session.

### K-015 — Akun Owner Awal

**Keputusan:** akun Owner pertama dibuat melalui konfigurasi environment, yaitu `OWNER_USERNAME` dan `OWNER_INITIAL_PASSWORD`.

Password tidak boleh ditanam sebagai kredensial tetap di source code. Akun baru atau akun yang password-nya di-reset wajib mengganti password sebelum dapat menggunakan API lain yang dilindungi, sesuai mekanisme autentikasi yang dirancang.

Secret tidak boleh dimasukkan ke repository.

### K-016 — Pengelolaan Akun

**Keputusan:**
- HR/Finance dapat mengelola akun Karyawan.
- Pengelolaan akun Owner dan HR/Finance hanya untuk Owner.
- Audit log dapat diakses Owner.
- Sistem harus mempertahankan minimal satu Owner aktif.

Backend wajib menolak tindakan yang melanggar ketentuan ini.

### K-017 — Design System

**Keputusan:** tampilan mengikuti brand Takka Steel dengan palet biru steel dan gold, font Poppins serta Open Sans, gaya Corporate Clean, tabel Enterprise Data-Dense, dan mode gelap Blueprint.

Rincian visual terdapat di [`design-system.md`](./design-system.md).

---

## 4. Detail Keputusan Integrasi Perangkat

### K-018 — Backend sebagai Gateway

**Keputusan:** komunikasi perangkat dilakukan melalui backend, bukan langsung dari frontend atau database.

Backend bertanggung jawab untuk validasi, autentikasi perangkat sesuai protokol, penyimpanan kejadian scan, pencegahan duplikasi, dan pemrosesan data absensi.

### K-019 — Raw Scan Immutable

**Keputusan:** scan asli tidak boleh ditimpa oleh koreksi absensi.

Koreksi harus disimpan sebagai perubahan terpisah yang mencatat nilai sebelum dan sesudah, pelaku, waktu, serta alasan koreksi.

### K-020 — ACK Setelah Penyimpanan

**Keputusan:** backend hanya boleh memberikan respons sukses setelah kejadian scan tersimpan secara persisten.

Jika penyimpanan gagal, backend tidak boleh memberikan ACK sukses. Mekanisme retry harus dirancang agar pengiriman ulang tidak membuat scan ganda.

### K-021 — Offline dan Sinkronisasi

**Status:** Menunggu Konfirmasi.

Penyimpanan offline, retry otomatis, RTC, dan sinkronisasi setelah koneksi pulih bergantung pada spesifikasi perangkat serta firmware. Implementasi tidak boleh mengasumsikan kemampuan tersebut sebelum divalidasi.

### K-023 — Otorisasi Backend

**Keputusan:** semua tindakan yang memerlukan hak akses harus divalidasi pada backend.

Penyembunyian menu atau tombol di frontend bukan mekanisme keamanan yang memadai.

### K-024 — Data Sensitif

**Keputusan:** data payroll, kasbon, akun, dan biometrik harus memiliki pembatasan akses yang sesuai.

Detail penyimpanan template sidik jari, kebijakan retensi, dan prosedur penghapusan harus ditetapkan setelah desain hardware dan kebijakan privasi dikonfirmasi.

### K-025 — Custom Role dan Akses Cabang

**Status:** Menunggu Konfirmasi.

Versi awal menggunakan tiga role. Kebutuhan membuat role khusus dan membatasi pengguna berdasarkan cabang perlu divalidasi sebelum desain otorisasi final dikunci.

---

## 5. Detail Keputusan Absensi dan Lembur

### K-026 — Batas Mulai Lembur

**Keputusan:** pada hari kerja normal, kandidat lembur dimulai setelah pukul 17.00, tanpa mensyaratkan delapan jam kerja normal telah terpenuhi.

Contoh: jika karyawan bekerja pukul 15.00–18.00, kandidat durasi lemburnya adalah satu jam, sebelum penyesuaian lain yang mungkin berlaku.

Keputusan ini menentukan batas waktu awal, bukan tarif atau nominal pembayaran lembur.

### K-027 — Verifikasi Lembur

**Keputusan:** sistem menghitung kandidat lembur dari catatan scan yang valid dan menyediakan proses verifikasi atau koreksi terkontrol.

Nominal final mengikuti tarif yang berlaku serta aturan yang telah disetujui. Kebutuhan persetujuan sebelum lembur dilakukan belum menjadi prasyarat yang disepakati.

### K-028 — Aturan Global dan Per Karyawan

**Keputusan:** sistem mendukung aturan global dan pengecualian khusus karyawan.

Setiap aturan harus memiliki tanggal efektif. Jika aturan khusus karyawan tersedia, prioritas terhadap aturan global harus diterapkan konsisten.

### K-029 — Koreksi Absensi

**Keputusan:** koreksi absensi tidak mengubah raw scan dan harus memiliki audit trail.

Koreksi yang berdampak pada payroll final mengikuti prosedur pembukaan kembali atau penyesuaian yang diizinkan.

### K-030 — Detail Perhitungan Absensi

**Status:** Menunggu Konfirmasi.

Hal yang belum dikunci:
- Hari kerja dan kalender hari libur.
- Jam serta perlakuan istirahat.
- Pembulatan durasi.
- Penanganan scan tidak lengkap.
- Penentuan scan masuk dan pulang.
- Dampak keterlambatan dan pulang cepat terhadap penghasilan.

Nilai-nilai tersebut harus dicatat dalam konfigurasi atau keputusan bisnis yang disetujui, bukan ditebak oleh developer.

---

## 6. Detail Keputusan Payroll

### K-031 — Periode Payroll

**Keputusan:**
- Supir dan kenek menggunakan payroll mingguan.
- Karyawan lainnya menggunakan payroll bulanan.

Tanggal mulai dan akhir periode serta tanggal pembayaran masih harus mengikuti kalender payroll perusahaan yang dikonfirmasi.

### K-032 — Gaji Pokok dan Upah Harian

**Keputusan:** gaji pokok bulanan dan upah harian adalah komponen berbeda.

Sistem harus menyimpan keduanya secara terpisah dan menampilkan dasar perhitungan masing-masing. Dampak ketidakhadiran terhadap gaji pokok masih menunggu kebijakan perusahaan.

### K-033 — Tarif dengan Tanggal Efektif

**Keputusan:** perubahan tarif harus memiliki tanggal mulai berlaku.

Perhitungan periode historis menggunakan tarif yang sesuai dengan periode tersebut. Perubahan tarif untuk periode baru tidak boleh mengubah hasil payroll historis secara otomatis.

### K-034 — Pembukaan Kembali Payroll

**Keputusan:** payroll final dapat dibuka kembali melalui prosedur terkontrol.

Pembukaan kembali tidak boleh menghapus hasil final sebelumnya. Kewenangan, alasan wajib, dan persetujuan tambahan perlu ditentukan bersama klien.

### K-035 — Snapshot Payroll

**Keputusan:** setiap payroll yang difinalisasi harus menyimpan snapshot atau versi yang setara.

Snapshot sekurang-kurangnya mencakup:
- Periode dan identitas karyawan.
- Komponen penghasilan serta potongan.
- Tarif dan aturan yang digunakan.
- Input absensi dan koreksi yang relevan.
- Durasi serta nominal lembur yang disetujui.
- Nominal bonus dan komponen tambahan.
- Hasil perhitungan bruto, potongan, dan bersih.
- Waktu finalisasi dan identitas pelaku.

### K-036 — Riwayat Reopen dan Finalisasi Ulang

**Keputusan:** pembukaan kembali dan finalisasi ulang harus mempertahankan riwayat versi sebelumnya.

Contoh: tarif upah harian Rp80.000 berlaku pada September, kemudian Rp100.000 berlaku mulai Oktober. Payroll September yang dibuka kembali pada Oktober tetap menggunakan tarif historis September, kecuali terdapat koreksi retroaktif resmi yang disetujui dan tercatat.

### K-037 — Ledger Kasbon

**Keputusan:** kasbon menggunakan ledger transaksi.

Saldo dihitung dari transaksi kasbon dan pelunasan. Sistem tidak boleh mengganti saldo secara langsung tanpa transaksi yang dapat diaudit.

Batas cicilan dan aturan pemotongan payroll menunggu konfirmasi.

### K-038 — Bonus

**Keputusan:** nominal bonus ditetapkan oleh Owner. Pada versi pertama, sistem tidak menghitung bonus otomatis berdasarkan KPI atau rumus yang belum disepakati.

### K-039 — Pajak dan Potongan

**Keputusan:** BPJS, PPh 21, dan potongan lain hanya diterapkan setelah kebijakan perusahaan dikonfirmasi.

Jangan menambahkan komponen potongan secara otomatis hanya karena lazim digunakan oleh sistem payroll lain.

### K-040 — Keterlacakan Payroll

**Keputusan:** setiap hasil payroll harus dapat ditelusuri kembali ke sumber input dan aturan yang digunakan.

Pengguna berwenang harus dapat memeriksa dasar perhitungan tanpa mengubah snapshot historis.

### K-041 — Kewenangan Pembukaan Kembali

**Status:** Menunggu Konfirmasi.

Perlu ditentukan:
- Role yang dapat mengajukan pembukaan kembali.
- Role yang dapat menyetujui.
- Alasan wajib.
- Perlakuan jika payroll sudah dibayarkan.
- Mekanisme koreksi dan penyesuaian pada pembayaran berikutnya jika diperlukan.

### K-042 — Laporan Baseline

**Keputusan:** empat laporan aktual perusahaan menjadi baseline rekonsiliasi dan pengujian:

1. Absensi dan payroll mingguan supir/kenek.
2. Laporan lembur supir/kenek.
3. Absensi dan payroll bulanan staf.
4. Laporan lembur bulanan staf.

### K-043 — Regression Test Payroll

**Keputusan:** pengujian regresi wajib mencakup perubahan tarif, perhitungan ulang payroll historis, pembukaan kembali, dan finalisasi ulang.

Perubahan pada konfigurasi periode baru tidak boleh mengubah snapshot payroll historis tanpa proses koreksi resmi.

---

## 7. Keputusan yang Masih Terbuka

Hal-hal berikut tidak boleh dianggap sudah disepakati:

| Topik | Yang perlu dipastikan | Dokumen acuan |
|---|---|---|
| Jadwal kerja | Hari kerja, jam istirahat, kalender hari libur | `pertanyaan-terbuka.md` |
| Absensi | Penentuan scan masuk/pulang, pembulatan, scan tidak lengkap | `pertanyaan-terbuka.md` |
| Lembur | Tarif, pembulatan, perlakuan hari libur | `pertanyaan-terbuka.md` |
| Payroll | Cutoff, prorata, dampak ketidakhadiran | `pertanyaan-terbuka.md` |
| Potongan | BPJS, PPh 21, cicilan kasbon | `pertanyaan-terbuka.md` |
| Bonus | Komponen dan aturan selain nominal yang ditetapkan Owner | `pertanyaan-terbuka.md` |
| Reopen payroll | Kewenangan, persetujuan, koreksi setelah pembayaran | `pertanyaan-terbuka.md` |
| Role | Custom role dan akses per cabang | `pertanyaan-terbuka.md` |
| Hardware | MCU, sensor, protokol, enrollment, penghapusan | `pertanyaan-terbuka.md` |
| Offline | Antrean lokal, RTC, retry, sinkronisasi | `pertanyaan-terbuka.md` |
| Privasi | Lokasi template biometrik, retensi, penghapusan | `pertanyaan-terbuka.md` |
| Deployment | Hosting, jaringan cabang, TLS, zona waktu | `pertanyaan-terbuka.md` |

---

## 8. Prosedur Mengubah Keputusan

Jika keputusan berubah:

1. Jangan menghapus entri lama.
2. Tambahkan ID keputusan baru.
3. Tulis tanggal, status, alasan, dan alternatif yang dipertimbangkan.
4. Cantumkan ID keputusan yang digantikan.
5. Perbarui dokumen terkait, seperti `aturan-bisnis.md`, `alur.md`, `arsitektur.md`, atau `kebutuhan.md`.
6. Perbarui test case yang terdampak.
7. Pastikan implementasi dan dokumentasi konsisten sebelum perubahan dirilis.

Format yang disarankan:

| ID | Tanggal | Keputusan | Status | Alasan / alternatif |
|---|---|---|---|---|
| K-XXX | YYYY-MM-DD | Isi keputusan | Disepakati / Usulan / Menunggu Konfirmasi / Digantikan | Alasan, alternatif, dan referensi keputusan sebelumnya |

---

## 9. Dokumen Terkait

- [`README.md`](./README.md)
- [`PRD.md`](./PRD.md)
- [`kebutuhan.md`](./kebutuhan.md)
- [`aturan-bisnis.md`](./aturan-bisnis.md)
- [`alur.md`](./alur.md)
- [`arsitektur.md`](./arsitektur.md)
- [`pertanyaan-terbuka.md`](./pertanyaan-terbuka.md)
- [`rencana-pengerjaan.md`](./rencana-pengerjaan.md)
- [`design-system.md`](./design-system.md)