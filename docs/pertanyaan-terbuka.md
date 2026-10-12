# Pertanyaan Terbuka

Dokumen ini berisi pertanyaan yang masih membutuhkan konfirmasi sebelum kebutuhan produk, aturan bisnis, arsitektur hardware, dan implementasi sistem difinalisasi.

**Aturan pengelolaan:**

- Jangan mengisi jawaban berdasarkan asumsi developer.
- Setelah pertanyaan dijawab, pindahkan keputusan ke dokumen terkait dan perbarui `keputusan.md` jika jawabannya menetapkan atau mengubah keputusan.
- Hapus pertanyaan dari daftar terbuka hanya setelah jawabannya dikonfirmasi.
- Pertanyaan yang menghalangi akurasi payroll atau integrasi hardware harus diselesaikan sebelum modul terkait dinyatakan siap produksi.

## 1. Aturan Kerja dan Absensi

### Q-001. Kalender hari kerja

- Apakah hari kerja resmi perusahaan adalah Senin–Sabtu?
- Apakah hari Minggu merupakan hari libur rutin?
- Apakah ada perbedaan kalender kerja antara supir, kenek, dan karyawan lainnya?

**Dampak:** penentuan hari kerja, kehadiran, upah harian, dan lembur.

### Q-002. Jam istirahat

- Jam berapa waktu istirahat dimulai dan berakhir?
- Apakah waktu istirahat selalu sama atau dapat berubah?
- Apakah durasi istirahat dikurangi dari durasi kerja dan perhitungan lembur?
- Bagaimana perlakuan istirahat jika karyawan bekerja di hari libur?

**Dampak:** perhitungan durasi kerja dan lembur.

### Q-003. Toleransi keterlambatan dan pulang lebih awal

- Berapa menit toleransi keterlambatan?
- Apakah keterlambatan dihitung per menit, dibulatkan, atau menggunakan rentang tertentu?
- Apakah pulang lebih awal memiliki toleransi?
- Apakah keterlambatan atau pulang lebih awal langsung mengurangi upah, atau hanya menjadi informasi untuk verifikasi?

**Dampak:** status kehadiran dan perhitungan penghasilan.

### Q-004. Scan tidak lengkap atau tidak normal

- Bagaimana jika karyawan lupa scan masuk atau scan pulang?
- Bagaimana jika karyawan melakukan beberapa scan dalam satu hari?
- Bagaimana jika urutan scan tidak normal?
- Siapa yang berwenang memperbaiki absensi dan apakah diperlukan persetujuan?
- Apakah koreksi harus disertai bukti atau alasan tertentu?

**Dampak:** validitas absensi dan akurasi payroll.

### Q-005. Hari libur dan kerja di luar jadwal

- Apakah bekerja pada hari Minggu atau hari libur nasional dihitung sebagai lembur?
- Apakah tarif lembur berbeda pada hari libur?
- Apakah ada jadwal kerja khusus yang berbeda dari jadwal normal pukul 08.00–17.00?
- Apakah aturan lembur setelah pukul 17.00 berlaku untuk semua kategori karyawan?

**Catatan:** batas awal lembur setelah pukul 17.00 sudah disepakati. Pertanyaan ini hanya mengenai pengecualian dan perlakuan khusus.

**Dampak:** perhitungan lembur dan tarif yang berlaku.

## 2. Penggajian dan Potongan

### Q-006. Batas periode penggajian

- Periode mingguan supir/kenek dimulai dan berakhir pada hari apa?
- Periode bulanan dimulai dan berakhir pada tanggal berapa?
- Bagaimana menangani hari terakhir periode yang jatuh pada hari libur?
- Kapan batas waktu data absensi, lembur, bonus, dan piutang harus masuk agar dihitung dalam periode tersebut?

**Dampak:** penentuan data yang masuk ke setiap payroll.

### Q-007. Aturan upah harian dan gaji pokok

- Untuk karyawan bulanan, bagaimana upah harian dihitung dan dalam kondisi apa upah tersebut dibayarkan?
- Apakah upah harian diberikan berdasarkan kehadiran aktual, jenis tugas, atau ketentuan lain?
- Bagaimana hubungan antara gaji pokok, upah harian, dan jumlah hari hadir?
- Apakah ada batas minimum atau maksimum hari yang diperhitungkan dalam satu periode?

**Catatan:** gaji pokok dan upah harian sudah disepakati sebagai komponen terpisah untuk karyawan bulanan. Formula lengkapnya masih perlu dikonfirmasi.

**Dampak:** formula gaji dan pencegahan pembayaran ganda.

### Q-008. Pembulatan dan presisi

- Apakah nominal rupiah dibulatkan ke rupiah penuh atau mengikuti aturan lain?
- Bagaimana durasi lembur dihitung: per menit, per interval tertentu, atau dibulatkan?
- Jika hasil perhitungan mengandung pecahan rupiah, kapan pembulatan dilakukan?
- Apakah nominal dibulatkan pada setiap komponen atau hanya pada total akhir?

**Contoh:** apabila perhitungan lembur menghasilkan Rp9.333,33, berapa nominal yang harus ditampilkan dan dibayarkan?

**Dampak:** konsistensi hasil payroll dan rekonsiliasi laporan.

### Q-009. Tarif lembur

- Berapa tarif lembur yang berlaku untuk setiap jenis karyawan?
- Apakah tarif berbeda untuk supir, kenek, dan karyawan lainnya?
- Apakah ada tarif khusus untuk hari libur atau jenis pekerjaan tertentu?
- Apakah lembur dihitung berdasarkan durasi aktual atau terdapat aturan minimum durasi?
- Siapa yang berwenang menetapkan dan mengubah tarif tersebut?

**Catatan:** batas mulai lembur setelah pukul 17.00 sudah disepakati. Tarif dan pengecualiannya belum seluruhnya dikonfirmasi.

**Dampak:** perhitungan nominal lembur.

### Q-010. BPJS, pajak, dan potongan lainnya

- Apakah BPJS Kesehatan dan BPJS Ketenagakerjaan termasuk dalam ruang lingkup versi pertama?
- Jika ya, komponen dan tarif apa yang digunakan, serta siapa yang menanggung masing-masing komponen?
- Apakah PPh 21 perlu dihitung otomatis?
- Apakah terdapat potongan tetap atau potongan lain yang perlu dicatat?
- Apakah potongan tersebut mengurangi penghasilan bruto atau dihitung melalui mekanisme lain?

**Dampak:** gaji bersih dan kelengkapan perhitungan payroll.

### Q-011. Piutang atau kasbon

- Apakah pelunasan piutang harus sekaligus atau dapat dicicil?
- Siapa yang menentukan nominal potongan setiap periode?
- Apakah terdapat batas maksimum potongan terhadap penghasilan karyawan?
- Apakah transaksi piutang memerlukan persetujuan Owner atau HR/Finance?
- Bagaimana menangani pembatalan transaksi atau koreksi potongan yang sudah masuk ke payroll final?

**Catatan:** pencatatan piutang menggunakan ledger transaksi sudah disepakati. Kebijakan pembayaran dan pemotongannya masih perlu dikonfirmasi.

**Dampak:** saldo piutang dan nominal gaji bersih.

### Q-012. Bonus

- Apakah bonus berlaku untuk seluruh kategori karyawan atau hanya karyawan tertentu?
- Apakah bonus dapat diberikan beberapa kali dalam satu periode?
- Apakah bonus harus ditetapkan sebelum payroll dihitung atau dapat ditambahkan saat review?
- Apakah perubahan bonus memerlukan alasan atau persetujuan tambahan?

**Catatan:** bonus merupakan komponen terpisah dan nominalnya ditentukan Owner. Formula bonus otomatis tidak termasuk rancangan awal.

**Dampak:** pengelolaan komponen bonus dan proses finalisasi payroll.

### Q-013. Izin, sakit, dan cuti

- Apa saja jenis izin, sakit, dan cuti yang perlu tersedia?
- Apakah masing-masing jenis memiliki kuota atau batas tertentu?
- Siapa yang berhak menyetujui pengajuan?
- Apakah diperlukan dokumen pendukung?
- Bagaimana masing-masing jenis ketidakhadiran memengaruhi upah harian dan gaji pokok?
- Apakah terdapat kebijakan khusus untuk izin mendadak atau ketidakhadiran tanpa pemberitahuan?

**Dampak:** status kehadiran dan komponen penggajian.

## 3. Finalisasi dan Koreksi Payroll

### Q-014. Otorisasi pembukaan kembali periode final

- Siapa yang boleh membuka kembali payroll yang sudah final: Owner saja atau HR/Finance dengan izin tertentu?
- Apakah pembukaan kembali memerlukan alasan wajib?
- Apakah diperlukan persetujuan Owner sebelum periode dibuka?
- Setelah koreksi dilakukan, siapa yang berwenang memfinalisasi ulang?

**Catatan:** pembukaan kembali payroll sudah disepakati sebagai fitur yang diperbolehkan. Tarif historis dan snapshot perhitungan harus tetap terjaga.

**Dampak:** otorisasi, audit, dan pengendalian perubahan payroll.

### Q-015. Prosedur koreksi setelah payroll final

- Jenis data apa yang boleh dikoreksi setelah finalisasi?
- Apakah perubahan harus dihitung ulang seluruhnya atau hanya pada komponen yang terdampak?
- Apakah hasil lama dan hasil baru harus ditampilkan berdampingan?
- Jika gaji sudah dibayarkan, bagaimana selisih hasil koreksi dicatat dan diselesaikan?
- Apakah koreksi menghasilkan penyesuaian pada periode berikutnya atau pembayaran/pengembalian terpisah?

**Dampak:** konsistensi histori penggajian dan penanganan selisih pembayaran.

## 4. Pengguna dan Hak Akses

### Q-016. Hak akses karyawan

- Apakah karyawan boleh melihat riwayat absensi miliknya sendiri?
- Apakah karyawan boleh melihat slip gaji dan rincian komponen penghasilan?
- Apakah karyawan dapat melihat saldo piutang miliknya?
- Apakah karyawan dapat mengajukan izin, cuti, atau koreksi absensi melalui aplikasi?

**Dampak:** ruang lingkup fitur karyawan dan perlindungan data.

### Q-017. Role khusus dan akses per cabang

- Apakah tiga template peran awal—Owner, HR/Finance, dan Karyawan—sudah cukup?
- Apakah Owner perlu dapat membuat role khusus dan mengatur izin setiap fitur?
- Apakah pengguna HR/Finance hanya boleh mengakses cabang tertentu?
- Apakah ada kebutuhan role tambahan, misalnya supervisor atau approver lembur?

**Catatan:** tiga role dasar telah disepakati. Kebutuhan role khusus dan pembatasan akses per cabang belum menjadi keputusan final.

**Dampak:** desain permission dan struktur otorisasi backend.

## 5. Hardware dan Integrasi Perangkat

### Q-018. Spesifikasi perangkat fingerprint

- Mikrokontroler dan sensor sidik jari apa yang digunakan?
- Siapa yang bertanggung jawab atas firmware dan perakitan perangkat?
- Apakah tersedia spesifikasi protokol komunikasi dan format data scan?
- Bagaimana perangkat mengidentifikasi karyawan yang berhasil melakukan pemindaian?

**Dampak:** desain API, protokol komunikasi, dan integrasi backend.

### Q-019. Penyimpanan dan pencocokan sidik jari

- Apakah template sidik jari disimpan dan dicocokkan di sensor/perangkat atau dikirim ke server?
- Apakah pendaftaran sidik jari dilakukan langsung pada mesin atau dipicu melalui aplikasi web?
- Apakah karyawan harus didaftarkan di setiap mesin cabang?
- Bagaimana proses penonaktifan karyawan dan penghapusan identitas biometrik dari perangkat?
- Bagaimana jika perangkat diganti atau mengalami kerusakan?

**Dampak:** arsitektur enrollment, privasi biometrik, dan pengelolaan karyawan.

### Q-020. Offline mode, waktu, dan pengiriman ulang

- Apakah perangkat dapat menyimpan scan ketika internet terputus?
- Berapa kapasitas penyimpanan offline dan berapa lama data dapat ditahan?
- Apakah perangkat memiliki RTC dan bagaimana jamnya disinkronkan?
- Bagaimana perangkat mengirim ulang data yang belum diterima server?
- Apakah setiap scan memiliki ID unik yang tetap sama saat dikirim ulang?
- Bagaimana sistem menangani waktu perangkat yang tidak akurat?

**Dampak:** integritas data, deteksi duplikasi, dan sinkronisasi absensi.

### Q-021. Koneksi antar-cabang dan server

- Apakah setiap cabang memiliki koneksi internet yang stabil?
- Apakah perangkat akan mengirim data melalui Wi-Fi ke server publik atau server lokal?
- Di mana backend dan database akan di-host?
- Apakah perusahaan sudah memiliki domain, server, atau anggaran hosting?
- Siapa yang bertanggung jawab atas pemeliharaan server dan pemantauan perangkat?

**Dampak:** deployment, keamanan komunikasi, dan operasional sistem.

### Q-022. Identitas cabang dan zona waktu

- Apakah setiap karyawan hanya boleh absen pada cabang penempatannya?
- Jika karyawan boleh absen di cabang lain, apakah perlu persetujuan atau alasan?
- Apakah semua cabang menggunakan zona waktu WIB?
- Bagaimana sistem menangani perangkat yang jamnya berbeda dari waktu server?

**Dampak:** validasi lokasi absensi, rekap cabang, dan akurasi waktu.

## 6. Keamanan dan Perlindungan Data

### Q-023. Kebijakan data biometrik

- Bagaimana perusahaan akan memberi informasi dan memperoleh dasar pemrosesan data biometrik karyawan sesuai ketentuan perlindungan data yang berlaku?
- Data apa yang disimpan: template biometrik, ID pada sensor, atau hanya hasil pencocokan?
- Siapa yang boleh mengakses informasi pemetaan biometrik?
- Berapa lama data disimpan?
- Bagaimana data biometrik dihapus ketika karyawan berhenti atau tidak lagi memerlukan akses?
- Apakah diperlukan prosedur khusus ketika perangkat hilang, rusak, atau diganti?

**Dampak:** keamanan, privasi, retensi, dan tata kelola data biometrik.

## 7. Branding dan Antarmuka

### Q-024. Aset brand

- Apakah klien dapat memberikan logo dalam format vektor, misalnya SVG, termasuk versi untuk latar gelap?
- Apakah tersedia panduan brand resmi selain website Takka Steel?
- Apakah ada warna, font, atau aturan visual yang wajib dipertahankan?

### Q-025. Nama aplikasi dan mode tampilan

- Nama aplikasi apa yang ditampilkan pada header dan judul tab browser?
- Apakah mode gelap harus tersedia pada versi pertama?

**Referensi:** `design-system.md`.

## 7a. Pengaturan Aturan (ditemukan saat Fase 2, 2026-10-12)

### Q-026. Koreksi aturan berlaku surut

- Versi aturan saat ini tidak boleh berlaku surut dan terkunci setelah tanggal berlakunya tiba (K-050).
- Jika Owner salah memasukkan aturan yang sudah berlaku (mis. jam masuk salah ketik), bagaimana prosedur koreksinya?
- Siapa yang boleh melakukan koreksi berlaku surut, apakah butuh alasan atau persetujuan, dan bagaimana dampaknya terhadap rekap dan payroll yang belum/sudah final?

**Dampak:** keterlacakan aturan dan payroll yang dibuka kembali (K-033, K-034).

### Q-027. Hari libur khusus cabang

- Apakah ada hari libur yang hanya berlaku di cabang tertentu (mis. libur daerah)?
- Saat ini satu kalender libur berlaku untuk semua cabang (K-051).

**Dampak:** penentuan hari kerja per cabang.

### Q-028. Lembur yang dimulai setelah jam pulang normal

- Batas mulai lembur dan jam pulang normal diatur sebagai dua parameter terpisah. Jika keduanya berbeda (mis. jam pulang 16.00, lembur mulai 17.00), bagaimana status waktu antara 16.00–17.00?
- Saat ini waktu tersebut tidak dihitung sebagai jam normal maupun kandidat lembur.

**Dampak:** durasi kerja dan lembur bila jadwal kategori berbeda dari 08.00–17.00.

## 7b. Mesin Uji X401 (ditemukan 2026-10-12)

### Q-029. Kemampuan nyata mesin Solution X401

- Apakah firmware X401 yang dipinjam memiliki menu ADMS/Cloud Server?
- Apa format persis request yang dikirim (path, parameter, format baris scan, format tanggal)?
- Seberapa sering mesin mengirim heartbeat, dan apakah mesin mengirim ulang scan bila server membalas gagal?
- Apakah mesin mengirim secret/kredensial selain nomor seri, dan apakah mendukung HTTPS?

Cara menjawab: [`panduan-mesin-x401.md`](./panduan-mesin-x401.md) Tahap 1 dan 3, hasil dicatat di §7 panduan.

**Dampak:** adapter ZK-Push, simulator, dan uji Fase 3 (K-054, K-055).

### Q-030. Protokol alat rakitan

- Apakah alat rakitan akan meniru protokol ZK-Push (server tidak perlu diubah sama sekali), atau memakai kontrak sendiri yang mendukung ID unik per scan, kredensial rahasia per mesin, dan TLS?

**Dampak:** jumlah adapter yang dipelihara dan tingkat keamanan di produksi. Perlu dijawab sebelum firmware alat rakitan ditulis.

### Q-031. Pemetaan karyawan ke PIN mesin

- Apakah satu karyawan memakai PIN yang sama di semua mesin cabang, atau PIN per mesin?
- Siapa yang mendaftarkan sidik jari dan PIN di mesin, dan bagaimana sinkronisasinya dengan data karyawan di web?
- Bagaimana menangani scan dari PIN yang belum dipetakan ke karyawan?

**Dampak:** schema karyawan/mesin di Fase 3 dan proses enrollment (terkait Q-019).

### Q-032. Riwayat scan lama saat mesin pertama kali tersambung

- Uji 2026-10-12 menunjukkan X401 langsung mengirim **seluruh riwayat** (16.951 log sejak 2020) begitu ADMS diarahkan ke server.
- Apakah server menyimpan semua riwayat itu sebagai scan asli, atau hanya scan sejak tanggal mesin didaftarkan di web (sisanya tetap di-ACK agar mesin berhenti mengirim, tetapi ditandai/diabaikan)?
- Bagaimana dengan scan dari PIN yang tidak dipetakan ke karyawan (Q-031)?

**Dampak:** isi tabel scan, rekap, dan privasi data orang di luar proyek. Untuk mesin uji milik dosen, riwayat lama **bukan data proyek** dan sebaiknya tidak disimpan.

## 8. Prioritas Penyelesaian

Urutan berikut merupakan rekomendasi prioritas untuk menutup pertanyaan, bukan keputusan bisnis baru.

| Prioritas | ID | Alasan |
|---|---|---|
| P0 — Sebelum formula payroll dikunci | Q-002, Q-005, Q-006, Q-007, Q-008, Q-009, Q-010, Q-011, Q-013, Q-014, Q-015 | Menentukan akurasi nominal, periode, dan prosedur koreksi payroll. |
| P0 — Sebelum Fase 3 (uji X401) | Q-029 (sebagian terjawab 2026-10-12), Q-031, Q-032 | Menentukan format adapter ZK-Push dan pemetaan PIN karyawan. |
| P0 — Sebelum integrasi hardware | Q-018, Q-019, Q-020, Q-021, Q-022, Q-030 | Menentukan protokol, keamanan, sinkronisasi, dan keandalan data scan. |
| P1 — Sebelum fitur absensi dan izin difinalisasi | Q-001, Q-003, Q-004, Q-016, Q-017, Q-026, Q-027, Q-028 | Menentukan perilaku absensi, proses koreksi, dan hak akses. |
| P1 — Sebelum rilis produksi | Q-023 | Menentukan tata kelola data biometrik dan kontrol aksesnya. |
| P2 — Sebelum UI final | Q-024, Q-025 | Menyelesaikan identitas visual dan nama aplikasi. |

## 9. Prosedur Menutup Pertanyaan

Ketika klien telah menjawab suatu pertanyaan:

1. Catat jawaban dan tanggal konfirmasi.
2. Tentukan apakah jawaban tersebut merupakan keputusan final atau masih berupa usulan.
3. Perbarui `keputusan.md` apabila jawaban menetapkan atau mengubah keputusan.
4. Perbarui `PRD.md` dan dokumen terkait sesuai dampaknya.
5. Perbarui `aturan-bisnis.md` untuk formula dan aturan operasional.
6. Perbarui `alur.md` jika alur proses berubah.
7. Perbarui `arsitektur.md` jika jawaban memengaruhi desain teknis atau hardware.
8. Hapus pertanyaan dari daftar terbuka hanya setelah jawaban tercatat pada dokumen yang tepat.

Jangan menutup pertanyaan hanya karena implementasi sudah dibuat. Pertanyaan baru dianggap selesai ketika kebutuhan telah dikonfirmasi dan dampaknya terhadap dokumen serta pengujian sudah ditangani.
