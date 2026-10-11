# Catatan Keputusan Sistem

Dokumen ini mencatat keputusan produk, bisnis, arsitektur, keamanan, dan proses pengembangan Biometric Attendance & HR Management System.

## 1. Tujuan dan Aturan Pencatatan

Setiap keputusan memiliki ID unik, tanggal, status, alasan, dan alternatif yang tidak dipilih jika relevan.

Status keputusan:

- **Disepakati:** keputusan yang telah ditetapkan dan menjadi acuan implementasi.
- **Usulan:** rekomendasi yang belum mendapat persetujuan final.
- **Dibatalkan:** keputusan yang tidak lagi berlaku, tetapi tetap disimpan sebagai histori.

Ketentuan:

1. Keputusan yang sudah disepakati tidak dihapus.
2. Jika keputusan berubah, buat keputusan baru yang secara eksplisit menggantikan keputusan sebelumnya.
3. Dokumen ini harus konsisten dengan `PRD.md`, `aturan-bisnis.md`, `kebutuhan.md`, dan dokumen teknis terkait.
4. Jangan menganggap keputusan teknis sebagai persetujuan klien terhadap aturan bisnis yang belum dikonfirmasi.
5. Detail yang masih terbuka harus dicatat di `pertanyaan-terbuka.md`.

## 2. Daftar Keputusan

### A. Arsitektur dan Teknologi

| ID | Tanggal | Keputusan | Status | Alasan dan alternatif |
|---|---|---|---|---|
| K-001 | 2026-10-09 | Stack utama menggunakan Java LTS, Spring Boot, React, dan PostgreSQL dengan arsitektur modular monolith. | Disepakati | Tim menguasai Java dan JavaScript. Modular monolith dipilih agar sistem lebih sederhana untuk dikembangkan dan dirawat dibandingkan microservices. |
| K-006 | 2026-10-09 | Sistem ditujukan untuk satu perusahaan dan tidak menggunakan `tenant_id` pada setiap tabel sebagai mekanisme multi-tenant. | Disepakati | Belum ada kebutuhan SaaS atau multi-perusahaan. Jika kebutuhan tersebut muncul, arsitektur harus dievaluasi kembali. |
| K-008 | 2026-10-09 | Pengembangan web dan backend menggunakan simulator perangkat absensi sampai prototipe hardware tersedia. | Disepakati | Komponen dan protokol hardware belum final. Pengembangan software tidak boleh bergantung pada kesiapan hardware. |
| K-009 | 2026-10-09 | Eksperimen backend lama berbasis NestJS diarsipkan melalui Git tag pada commit `26bed0a` sebelum folder backend lama dihapus dari implementasi aktif. | Disepakati | Menjaga referensi historis tanpa mencampurkan eksperimen lama dengan arsitektur baru. |
| K-010 | 2026-10-09 | Maven digunakan sebagai build tool backend. Lingkungan pengembangan disiapkan agar database, backend, dan frontend dapat dijalankan menggunakan Docker Compose. | Disepakati | Mempermudah setup lingkungan pengembangan dan konsistensi antaranggota tim. |
| K-018 | 2026-10-11 | Backend menjadi perantara tunggal antara perangkat absensi, database, dan aplikasi web. Perangkat tidak berkomunikasi langsung dengan frontend. | Disepakati | Memusatkan autentikasi, validasi, penyimpanan, dan pengolahan data di backend. |
| K-019 | 2026-10-11 | Data scan asli harus dipisahkan dari hasil interpretasi absensi dan koreksi manual. | Disepakati | Menjaga data sumber tetap dapat diaudit ketika terjadi koreksi atau perubahan aturan perhitungan. |
| K-020 | 2026-10-11 | Pengiriman ulang scan harus aman terhadap duplikasi. Backend hanya memberikan acknowledgment berhasil setelah data tersimpan secara persisten. | Disepakati | Menghindari scan ganda dan mencegah perangkat menganggap data berhasil diterima ketika penyimpanan gagal. |
| K-021 | 2026-10-11 | Kemampuan perangkat untuk antrean offline, retry, RTC, dan sinkronisasi ulang harus diverifikasi terhadap spesifikasi hardware sebelum dinyatakan sebagai fitur yang tersedia. | Disepakati | Mencegah asumsi software yang tidak didukung firmware atau kapasitas perangkat. |
| K-022 | 2026-10-11 | Nominal uang dihitung menggunakan tipe desimal dengan presisi yang sesuai, bukan floating-point biasa. | Disepakati | Menjaga konsistensi hasil perhitungan gaji, tarif, lembur, bonus, dan piutang. |

### B. Pengguna, Akun, dan Keamanan

| ID | Tanggal | Keputusan | Status | Alasan dan alternatif |
|---|---|---|---|---|
| K-002 | 2026-10-09 | Tiga template peran utama adalah Owner, HR/Finance sebagai satu role, dan Karyawan. | Disepakati | Mengikuti kebutuhan awal klien. Kebutuhan pembuatan role khusus tetap perlu dikonfirmasi secara terpisah. |
| K-012 | 2026-10-09 | Login menggunakan username, bukan email atau nomor telepon. Username tidak membedakan huruf besar dan kecil. | Disepakati | Tidak semua karyawan, khususnya supir dan kenek, memiliki email atau nomor telepon yang dapat digunakan untuk login. |
| K-014 | 2026-10-09 | Sesi login menggunakan session cookie yang disimpan di PostgreSQL melalui Spring Session JDBC, dilengkapi proteksi CSRF. Masa berlaku sesi adalah delapan jam. | Disepakati | Aplikasi web dan API direncanakan berada pada domain yang sama. Pendekatan session-based dipilih dibandingkan JWT untuk mempermudah pencabutan sesi. |
| K-015 | 2026-10-09 | Akun Owner pertama dibuat menggunakan konfigurasi lingkungan `OWNER_USERNAME` dan `OWNER_INITIAL_PASSWORD`. Akun baru dan akun yang di-reset wajib mengganti password sebelum mengakses API lain yang dilindungi. | Disepakati | Tidak ada password bawaan yang ditanamkan di source code. |
| K-016 | 2026-10-09 | HR/Finance hanya boleh mengelola akun Karyawan. Pengelolaan akun Owner/HR dan akses audit log khusus Owner. Sistem harus mempertahankan minimal satu Owner aktif. | Disepakati | Membatasi perubahan hak akses dan mempertahankan pengawasan oleh Owner. |
| K-023 | 2026-10-11 | Pemeriksaan otorisasi dilakukan di backend. Menyembunyikan menu di frontend tidak dianggap sebagai mekanisme keamanan yang cukup. | Disepakati | API harus menolak tindakan yang tidak diizinkan, terlepas dari antarmuka yang digunakan. |
| K-024 | 2026-10-11 | Data payroll, piutang, akun, dan informasi biometrik dibatasi berdasarkan kebutuhan akses. | Disepakati | Data tersebut bersifat sensitif dan tidak boleh otomatis terlihat oleh semua pengguna. |
| K-025 | 2026-10-11 | Kebutuhan role khusus, permission per fitur, dan pembatasan akses berdasarkan cabang belum ditetapkan sebagai fitur final. | Usulan | Mempertahankan tiga role dasar sambil menunggu validasi kebutuhan operasional klien. |

### C. Aturan Kerja dan Absensi

| ID | Tanggal | Keputusan | Status | Alasan dan alternatif |
|---|---|---|---|---|
| K-003 | 2026-10-09 | Aturan lembur awal menggunakan perhitungan otomatis berdasarkan data absensi, tanpa mewajibkan pengajuan lembur terlebih dahulu. | Diperbarui oleh K-026 | Keputusan awal perlu diselaraskan dengan batas lembur pukul 17.00, verifikasi manual, dan aturan nominal yang berlaku. |
| K-004 | 2026-10-09 | Owner mengelola aturan kerja dan tarif melalui modul pengaturan dengan tanggal efektif. | Disepakati | Aturan dapat berubah dan tidak boleh ditanamkan sebagai nilai tetap di kode program. |
| K-005 | 2026-10-09 | Sistem mengusulkan upah harian berdasarkan hari yang memenuhi aturan kehadiran. Pihak berwenang menentukan apakah upah tersebut dibayarkan sesuai kebijakan perusahaan. | Disepakati | Mempertahankan keputusan awal; detail kriteria hari yang dibayar harus dirumuskan di aturan bisnis. |
| K-026 | 2026-10-11 | Batas mulai perhitungan lembur adalah setelah pukul 17.00, bukan setelah karyawan menyelesaikan delapan jam kerja. | Disepakati | Mengikuti klarifikasi kebutuhan klien. Contoh: masuk pukul 15.00 dan pulang pukul 18.00 menghasilkan satu jam kandidat lembur, dengan asumsi tidak ada pengurangan istirahat. |
| K-027 | 2026-10-11 | Perhitungan lembur menggunakan scan perangkat sebagai sumber data awal dan mendukung verifikasi atau koreksi manual yang dapat diaudit. | Disepakati | Data otomatis membantu perhitungan, tetapi perlu pemeriksaan untuk kondisi scan yang tidak lengkap atau tidak sesuai aktivitas sebenarnya. |
| K-028 | 2026-10-11 | Aturan kerja dapat diterapkan secara global maupun khusus untuk karyawan tertentu. | Disepakati | Mengikuti kebutuhan Owner untuk mengatur aturan secara umum dan melakukan pengecualian sesuai kebutuhan. |
| K-029 | 2026-10-11 | Data scan asli tidak boleh ditimpa oleh koreksi absensi manual. Koreksi disimpan sebagai perubahan terpisah beserta alasan, pelaku, dan waktu. | Disepakati | Memastikan data awal tetap dapat ditelusuri dan mengurangi risiko perubahan tanpa jejak. |
| K-030 | 2026-10-11 | Aturan hari kerja, istirahat, hari libur, pembulatan, dan penanganan scan tidak lengkap tidak boleh difinalisasi berdasarkan asumsi. | Disepakati | Detail tersebut masih memerlukan konfirmasi klien sebelum formula produksi dikunci. |

### D. Penggajian, Tarif Historis, dan Piutang

| ID | Tanggal | Keputusan | Status | Alasan dan alternatif |
|---|---|---|---|---|
| K-031 | 2026-10-11 | Supir dan kenek menggunakan periode penggajian mingguan; karyawan lainnya menggunakan periode penggajian bulanan. | Disepakati | Mengikuti proses penggajian yang berjalan di perusahaan. Tanggal batas setiap periode masih perlu dikonfirmasi. |
| K-032 | 2026-10-11 | Pada skema bulanan, gaji pokok dan upah harian merupakan komponen yang terpisah. | Disepakati | Mengikuti format penggajian perusahaan. Aturan kelayakan dan perhitungan setiap komponen harus dijelaskan di `aturan-bisnis.md`. |
| K-033 | 2026-10-11 | Tarif harian, tarif lembur, dan aturan yang memiliki tanggal efektif harus mempertahankan nilai historis sesuai periode yang dihitung. | Disepakati | Perubahan tarif untuk periode mendatang tidak boleh mengubah nominal pada periode terdahulu. |
| K-034 | 2026-10-11 | Periode payroll yang telah difinalisasi dapat dibuka kembali melalui proses terkontrol. Pembukaan kembali tidak boleh mengganti tarif historis yang digunakan pada periode tersebut. | Disepakati | Mengakomodasi kebutuhan koreksi sambil mempertahankan konsistensi hasil penggajian lama. |
| K-035 | 2026-10-11 | Perhitungan payroll harus menyimpan snapshot atau versi yang setara atas tarif, aturan, komponen, input, dan hasil perhitungan yang digunakan. | Disepakati | Memungkinkan audit, reproduksi perhitungan, dan perbandingan sebelum serta sesudah koreksi. |
| K-036 | 2026-10-11 | Pembukaan kembali, koreksi, dan finalisasi ulang payroll harus memiliki jejak audit. Snapshot hasil lama tetap dipertahankan. | Disepakati | Mencegah perubahan diam-diam pada hasil penggajian yang sudah pernah disahkan. |
| K-037 | 2026-10-11 | Piutang/kasbon karyawan dikelola sebagai ledger transaksi, bukan sekadar kolom saldo atau input potongan bebas. | Disepakati | Saldo harus dapat ditelusuri melalui transaksi, pembayaran, dan potongan payroll. |
| K-038 | 2026-10-11 | Bonus/insentif merupakan komponen terpisah yang nominalnya ditentukan Owner. Tidak ada formula bonus otomatis pada versi awal. | Disepakati | Mengikuti klarifikasi bahwa bonus merupakan keputusan Owner. |
| K-039 | 2026-10-11 | Perhitungan BPJS, PPh 21, dan potongan lain hanya diimplementasikan setelah komponen, tarif, dan kebijakan yang berlaku dikonfirmasi. | Disepakati | Menghindari asumsi yang dapat menyebabkan hasil payroll tidak sesuai ketentuan atau kebijakan perusahaan. |
| K-040 | 2026-10-11 | Hasil payroll harus dapat ditelusuri ke data absensi, tarif, lembur, bonus, piutang, dan komponen lain yang digunakan. | Disepakati | Mendukung verifikasi dan rekonsiliasi terhadap formulir penggajian perusahaan. |
| K-041 | 2026-10-11 | Kewenangan untuk membuka kembali periode final, batas koreksi, dan mekanisme persetujuan pembukaan kembali harus ditetapkan secara eksplisit sebelum fitur dikunci. | Usulan | Prinsip pembukaan kembali sudah disepakati, tetapi matriks otorisasi dan prosedurnya belum final. |

### E. UI/UX dan Proses Pengembangan

| ID | Tanggal | Keputusan | Status | Alasan dan alternatif |
|---|---|---|---|---|
| K-011 | 2026-10-09 | Tim bekerja langsung pada branch `main`, tanpa branch fitur. | Disepakati | Mengikuti pilihan proses kerja tim. Risiko konflik dan kebutuhan review harus tetap diperhatikan. |
| K-013 | 2026-10-09 | Frontend menggunakan Tailwind CSS dan shadcn/ui. | Disepakati | Pilihan tim untuk membangun antarmuka yang konsisten dan mudah dikustomisasi. Ant Design dan MUI tidak dipilih. |
| K-017 | 2026-10-09 | Design system mengikuti brand Takka Steel dengan warna biru steel dan gold, Poppins dan Open Sans, gaya Corporate Clean, tabel padat untuk data operasional, serta mode gelap Blueprint. | Disepakati | Aplikasi digunakan setiap hari pada perangkat mobile hingga desktop. Detail visual dicatat di `design-system.md`. |
| K-042 | 2026-10-11 | Empat formulir penggajian aktual perusahaan menjadi baseline untuk pengujian dan rekonsiliasi hasil payroll. | Disepakati | Sistem harus menghasilkan komponen dan total yang dapat dicocokkan dengan proses kerja perusahaan. |
| K-043 | 2026-10-11 | Perhitungan payroll wajib memiliki regression test untuk tarif historis, lembur, upah harian, gaji pokok, bonus, piutang, dan pembukaan kembali periode. | Disepakati | Kesalahan perhitungan dapat berdampak langsung pada pembayaran karyawan. |

## 3. Keputusan yang Digantikan atau Diperjelas

| Keputusan lama | Status baru | Penjelasan |
|---|---|---|
| K-003: lembur otomatis setelah jam pulang terjadwal | Diperjelas oleh K-026 dan K-027 | Batas lembur adalah pukul 17.00; scan menjadi sumber perhitungan awal dan hasil dapat diverifikasi manual. |
| K-007: periode final dikunci | Diperjelas oleh K-034 sampai K-036 | Periode final tetap terlindungi, tetapi dapat dibuka kembali melalui prosedur terkontrol tanpa menghilangkan snapshot atau mengubah tarif historis. |
| K-004: semua aturan kerja dan gaji diatur Owner | Tetap berlaku dengan batasan K-028 dan K-033 | Aturan memiliki tanggal efektif dan dapat diterapkan global maupun per karyawan. Perubahan tidak menimpa histori. |

Keputusan lama tetap dipertahankan untuk histori. Implementasi harus mengikuti keputusan terbaru yang secara eksplisit memperbarui atau memperjelasnya.

## 4. Hal yang Belum Menjadi Keputusan Final

Hal berikut tidak boleh diperlakukan sebagai keputusan yang sudah disepakati:

1. Hari kerja resmi dan kalender libur.
2. Durasi serta perlakuan waktu istirahat.
3. Toleransi keterlambatan dan aturan pulang lebih awal.
4. Aturan pembulatan jam dan nominal rupiah.
5. Tarif lembur untuk hari libur atau kategori karyawan tertentu.
6. Kriteria hari yang berhak mendapatkan upah harian.
7. Tanggal batas periode mingguan dan bulanan.
8. Perlakuan gaji untuk izin, sakit, dan cuti.
9. Komponen serta aturan BPJS, PPh 21, dan potongan lain.
10. Skema cicilan piutang dan batas potongan payroll.
11. Kewenangan final untuk membuka kembali payroll.
12. Role khusus dan pembatasan akses berdasarkan cabang.
13. Spesifikasi perangkat, protokol komunikasi, RTC, antrean offline, dan retry.
14. Lokasi penyimpanan template sidik jari serta mekanisme pencocokan biometrik.
15. Hosting, target ketersediaan, backup, dan retensi data.
16. Kebijakan retensi, akses, dan penghapusan data biometrik.

Pertanyaan tersebut dikelola di `pertanyaan-terbuka.md` dan diperbarui setelah memperoleh jawaban dari klien.

## 5. Aturan Pemeliharaan Dokumen

Setiap perubahan kebutuhan harus mengikuti urutan berikut:

1. Catat perubahan kebutuhan dan alasan.
2. Tentukan apakah perubahan sudah disetujui atau masih berupa usulan.
3. Tambahkan keputusan baru jika mengubah keputusan sebelumnya.
4. Perbarui `PRD.md` jika ruang lingkup atau kebutuhan produk berubah.
5. Perbarui `aturan-bisnis.md`, `kebutuhan.md`, `alur.md`, atau `arsitektur.md` sesuai dampaknya.
6. Perbarui `pertanyaan-terbuka.md` agar hanya berisi hal yang belum terjawab.
7. Perbarui `rencana-pengerjaan.md` jika perubahan memengaruhi urutan implementasi atau pengujian.

**Prinsip utama:** jangan mengubah keputusan bisnis secara diam-diam hanya untuk menyesuaikan kode. Jika implementasi tidak sesuai keputusan, perbaiki implementasi atau ajukan perubahan keputusan secara eksplisit.
